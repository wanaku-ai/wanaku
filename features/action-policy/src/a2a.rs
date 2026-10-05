//! The initial, non-streaming A2A JSON-RPC boundary.
use bytes::{Bytes, BytesMut};
use praxis_filter::{
    BodyAccess, BodyMode, FilterAction, FilterError, HttpFilter, HttpFilterContext,
};
use serde::Deserialize;
use serde_json::Value;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Config {
    public_url: String,
    #[serde(default)]
    managed: bool,
    #[serde(default = "default_agent")]
    agent: String,
    #[serde(default = "default_namespace")]
    namespace: String,
    #[serde(default = "default_limit")]
    max_body_bytes: usize,
}

fn default_agent() -> String {
    "a2a".to_owned()
}
fn default_namespace() -> String {
    "default".to_owned()
}
const fn default_limit() -> usize {
    1_048_576
}

pub struct A2aFilter {
    config: Config,
}

struct DiscoveryBody(BytesMut);

impl A2aFilter {
    fn handle_request(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: Option<&Bytes>,
    ) -> FilterAction {
        if ctx.get_metadata("wanaku.a2a.validated") == Some("true") {
            return FilterAction::Continue;
        }
        ctx.extra_request_headers
            .push(("Accept-Encoding".into(), "identity".to_owned()));
        if self.config.managed
            && let Err(action) = self.select_agent(ctx)
        {
            return action;
        }
        if ctx.request.method == http::Method::GET
            && (ctx.request.uri.path() == "/.well-known/agent-card.json"
                || ctx.get_metadata("wanaku.a2a.discovery") == Some("true"))
        {
            ctx.set_metadata("wanaku.a2a.discovery", "true");
            ctx.set_metadata("wanaku.a2a.validated", "true");
            return FilterAction::Continue;
        }
        if ctx.request.method != http::Method::POST
            || (ctx.request.uri.path() != "/" && !self.config.managed)
        {
            return rpc_error(&Value::Null, -32600, "Use POST / for A2A JSON-RPC.");
        }
        let action = self.handle_rpc_request(ctx, body);
        if matches!(action, FilterAction::Continue) {
            ctx.set_metadata("wanaku.a2a.validated", "true");
        }
        action
    }

    fn select_agent(&self, ctx: &mut HttpFilterContext<'_>) -> Result<(), FilterAction> {
        let Some((namespace, name, card)) = managed_route(ctx.request.uri.path()) else {
            return Err(route_error(404, "A2A agent route not found"));
        };
        if (card && ctx.request.method != http::Method::GET)
            || (!card && ctx.request.method != http::Method::POST)
        {
            return Err(route_error(405, "unsupported A2A HTTP method"));
        }
        let Some(endpoint) = ctx
            .extensions
            .get::<wanaku_infra::registry::InMemoryRegistry>()
            .and_then(|registry| registry.agent_endpoint(namespace, name, card))
        else {
            return Err(route_error(404, "A2A agent not found"));
        };
        let public_url = format!(
            "{}/{namespace}/a2a/{name}",
            self.config.public_url.trim_end_matches('/')
        );
        ctx.set_metadata(wanaku_types::NAMESPACE_METADATA_KEY, namespace);
        ctx.set_metadata("wanaku.a2a.agent", name);
        ctx.set_metadata("wanaku.a2a.public_url", public_url);
        if card {
            ctx.set_metadata("wanaku.a2a.discovery", "true");
        }
        ctx.upstream = Some(endpoint.upstream);
        ctx.rewritten_path = Some(endpoint.path);
        Ok(())
    }

    fn handle_rpc_request(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: Option<&Bytes>,
    ) -> FilterAction {
        let Some(request) = body.and_then(|body| serde_json::from_slice::<Value>(body).ok()) else {
            return rpc_error(&Value::Null, -32700, "Invalid JSON.");
        };
        let id = request
            .get("id")
            .filter(|id| id.is_string() || id.is_number())
            .cloned()
            .unwrap_or(Value::Null);
        if ctx
            .request
            .headers
            .get("a2a-version")
            .is_some_and(|version| !matches!(version.to_str(), Ok("0.3" | "0.3.0")))
        {
            return rpc_error(&id, -32600, "Only A2A JSON-RPC 0.3 is supported.");
        }
        let method = ctx.get_metadata("a2a.method").unwrap_or_default();
        if let Err((code, message)) = validate_request(&request, method) {
            return rpc_error(&id, code, message);
        }
        if ctx.get_metadata("a2a.streaming") == Some("true") {
            return rpc_error(&id, -32602, "Streaming is not supported.");
        }
        ctx.set_metadata(wanaku_filters::MCP_ID_KEY, id.to_string());
        if !self.config.managed {
            ctx.set_metadata(wanaku_types::NAMESPACE_METADATA_KEY, &self.config.namespace);
            ctx.set_metadata("wanaku.a2a.agent", &self.config.agent);
        }
        FilterAction::Continue
    }

    pub fn from_config(config: &serde_yaml::Value) -> Result<Box<dyn HttpFilter>, FilterError> {
        let mut config: Config = praxis_filter::parse_filter_config("wanaku_a2a", config)?;
        if config.managed {
            config.public_url = wanaku_types::config::ENV.a2a_public_url.clone();
        }
        let url: http::Uri = config
            .public_url
            .parse()
            .map_err(|_| FilterError::from("wanaku_a2a: invalid public_url"))?;
        if !matches!(url.scheme_str(), Some("http" | "https"))
            || url.authority().is_none()
            || url.path() != "/"
            || url.query().is_some()
            || config.agent.is_empty()
            || config.namespace.is_empty()
            || config.max_body_bytes == 0
        {
            return Err("wanaku_a2a: public_url must be an HTTP(S) origin; agent, namespace and body limit must be nonempty".into());
        }
        Ok(Box::new(Self { config }))
    }
}

fn managed_route(path: &str) -> Option<(&str, &str, bool)> {
    let suffix = path.strip_prefix('/')?;
    let (namespace, suffix) = suffix.split_once('/')?;
    let suffix = suffix.strip_prefix("a2a/")?;
    let (name, card) = match suffix.strip_suffix("/.well-known/agent-card.json") {
        Some(name) => (name, true),
        None => (suffix, false),
    };
    if wanaku_types::registry::validate_namespace_name(namespace).is_err()
        || wanaku_types::registry::validate_namespace_name(name).is_err()
    {
        return None;
    }
    Some((namespace, name, card))
}

fn route_error(status: u16, message: &str) -> FilterAction {
    let mut response = wanaku_filters::response::json_response(Bytes::from(
        serde_json::json!({"error": message}).to_string(),
    ));
    response.status = status;
    FilterAction::Reject(response)
}

pub(crate) fn is_supported_method(method: &str) -> bool {
    matches!(method, "SendMessage" | "GetTask" | "CancelTask")
}

fn rpc_error(id: &Value, code: i32, message: &str) -> FilterAction {
    wanaku_filters::response::json_rpc_error(id, code, message)
}

fn validate_request(request: &Value, method: &str) -> Result<(), (i32, &'static str)> {
    if request.get("jsonrpc").and_then(Value::as_str) != Some("2.0")
        || !request
            .get("id")
            .is_some_and(|id| id.is_string() || id.is_number())
    {
        return Err((-32600, "A2A requires a JSON-RPC 2.0 request with an ID."));
    }
    if !is_supported_method(method) {
        return Err((
            -32601,
            "A2A method is not supported; streaming and push are disabled.",
        ));
    }
    let params = request
        .get("params")
        .and_then(Value::as_object)
        .ok_or((-32602, "A2A params must be an object."))?;
    validate_configuration(params)?;
    match method {
        "GetTask" | "CancelTask"
            if !params
                .get("id")
                .and_then(Value::as_str)
                .is_some_and(|id| !id.is_empty()) =>
        {
            Err((-32602, "A2A task ID is required."))
        }
        "SendMessage" => validate_message(params.get("message")),
        _ => Ok(()),
    }
}

fn validate_configuration(
    params: &serde_json::Map<String, Value>,
) -> Result<(), (i32, &'static str)> {
    let streaming = |options: &serde_json::Map<String, Value>| {
        ["stream", "streaming"].iter().any(|key| {
            options
                .get(*key)
                .is_some_and(|value| value != &Value::Bool(false))
        })
    };
    if streaming(params)
        || params.get("configuration").is_some_and(|configuration| {
            configuration.as_object().is_none_or(|options| {
                streaming(options) || options.contains_key("pushNotificationConfig")
            })
        })
    {
        return Err((
            -32602,
            "Streaming and push notifications are not supported.",
        ));
    }
    Ok(())
}

fn validate_message(message: Option<&Value>) -> Result<(), (i32, &'static str)> {
    let Some(message) = message.and_then(Value::as_object) else {
        return Err((-32602, "A2A message must be an object."));
    };
    if !message
        .get("messageId")
        .and_then(Value::as_str)
        .is_some_and(|id| !id.is_empty())
        || !matches!(
            message.get("role").and_then(Value::as_str),
            Some("user" | "agent")
        )
        || !message
            .get("parts")
            .and_then(Value::as_array)
            .is_some_and(|parts| !parts.is_empty() && parts.iter().all(Value::is_object))
    {
        return Err((-32602, "A2A message requires messageId, role and parts."));
    }
    Ok(())
}

fn rewrite_card(card: &mut Value, public_url: &str) -> Result<(), FilterError> {
    let card = card
        .as_object_mut()
        .ok_or_else(|| FilterError::from("upstream agent card must be an object"))?;
    if card
        .get("protocolVersion")
        .is_some_and(|version| version != "0.3.0")
    {
        return Err("upstream agent card must use A2A 0.3.0".into());
    }
    card.insert("url".to_owned(), Value::String(public_url.to_owned()));
    card.insert(
        "preferredTransport".to_owned(),
        Value::String("JSONRPC".to_owned()),
    );
    card.remove("additionalInterfaces");
    card.remove("signatures");
    card.insert(
        "supportsAuthenticatedExtendedCard".to_owned(),
        Value::Bool(false),
    );
    card.remove("supportedInterfaces");
    let capabilities = card
        .entry("capabilities")
        .or_insert_with(|| serde_json::json!({}));
    let capabilities = capabilities
        .as_object_mut()
        .ok_or_else(|| FilterError::from("invalid agent card capabilities"))?;
    capabilities.insert("streaming".to_owned(), Value::Bool(false));
    capabilities.insert("pushNotifications".to_owned(), Value::Bool(false));
    Ok(())
}

fn validate_response_headers(headers: &http::HeaderMap) -> Result<(), FilterError> {
    if headers
        .get(http::header::CONTENT_ENCODING)
        .is_some_and(|encoding| encoding != "identity")
    {
        return Err("compressed A2A upstream responses are not supported".into());
    }
    if headers
        .get(http::header::CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        .is_some_and(|value| {
            value
                .split(';')
                .next()
                .is_some_and(|media| media.trim().eq_ignore_ascii_case("text/event-stream"))
        })
    {
        return Err("streaming A2A upstream responses are not supported".into());
    }
    Ok(())
}

#[async_trait::async_trait]
impl HttpFilter for A2aFilter {
    fn name(&self) -> &'static str {
        "wanaku_a2a"
    }
    fn request_body_access(&self) -> BodyAccess {
        BodyAccess::ReadOnly
    }
    fn request_body_mode(&self) -> BodyMode {
        BodyMode::StreamBuffer {
            max_bytes: Some(self.config.max_body_bytes),
        }
    }
    fn response_body_access(&self) -> BodyAccess {
        BodyAccess::ReadWrite
    }
    fn response_body_mode(&self) -> BodyMode {
        BodyMode::Stream
    }

    async fn on_request(
        &self,
        ctx: &mut HttpFilterContext<'_>,
    ) -> Result<FilterAction, FilterError> {
        let body = ctx.buffered_request_body.clone();
        Ok(self.handle_request(ctx, body.as_ref()))
    }

    async fn on_request_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
        end_of_stream: bool,
    ) -> Result<FilterAction, FilterError> {
        if !end_of_stream {
            return Ok(FilterAction::Continue);
        }
        Ok(self.handle_request(ctx, body.as_ref()))
    }

    async fn on_response(
        &self,
        ctx: &mut HttpFilterContext<'_>,
    ) -> Result<FilterAction, FilterError> {
        let discovery = ctx.get_metadata("wanaku.a2a.discovery") == Some("true");
        let rewrite = discovery
            && ctx
                .response_header
                .as_ref()
                .is_some_and(|response| response.status.is_success());
        if let Some(response) = ctx.response_header.as_mut() {
            validate_response_headers(&response.headers)?;
            if response.status.is_redirection() {
                return Err("A2A upstream redirects are not supported".into());
            }
            if rewrite {
                response.headers.remove(http::header::CONTENT_LENGTH);
                response.headers.remove(http::header::ETAG);
            }
        }
        if rewrite {
            ctx.insert_filter_state(DiscoveryBody(BytesMut::new()));
        }
        Ok(FilterAction::Continue)
    }

    fn on_response_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
        end_of_stream: bool,
    ) -> Result<FilterAction, FilterError> {
        if ctx.get_metadata("wanaku.a2a.discovery") != Some("true") {
            return Ok(FilterAction::Continue);
        }
        if ctx.get_filter_state::<DiscoveryBody>().is_none() {
            return Ok(FilterAction::Continue);
        }
        let state = ctx
            .get_filter_state_mut::<DiscoveryBody>()
            .ok_or_else(|| FilterError::from("missing agent card response state"))?;
        let chunk = body.take().unwrap_or_default();
        if chunk.len() > self.config.max_body_bytes.saturating_sub(state.0.len()) {
            return Err("upstream agent card exceeds the body limit".into());
        }
        state.0.extend_from_slice(&chunk);
        if !end_of_stream {
            return Ok(FilterAction::Continue);
        }
        let mut card: Value = serde_json::from_slice(&state.0)
            .map_err(|error| FilterError::from(error.to_string()))?;
        rewrite_card(
            &mut card,
            ctx.get_metadata("wanaku.a2a.public_url")
                .unwrap_or(&self.config.public_url),
        )?;
        *body = Some(Bytes::from(card.to_string()));
        Ok(FilterAction::Continue)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn managed_route_requires_namespace_first_and_exact_agent_path() {
        assert_eq!(
            managed_route("/blue/a2a/worker"),
            Some(("blue", "worker", false))
        );
        assert_eq!(
            managed_route("/blue/a2a/worker/.well-known/agent-card.json"),
            Some(("blue", "worker", true))
        );
        for path in [
            "/a2a/blue/worker",
            "/a2a/blue/worker/.well-known/agent-card.json",
            "/blue/a2a/worker/extra",
            "/blue/a2a/",
            "/blue/mcp/worker",
        ] {
            assert_eq!(managed_route(path), None, "{path}");
        }
    }

    fn test_filter(managed: bool) -> A2aFilter {
        A2aFilter {
            config: Config {
                public_url: "https://proxy.example/".to_owned(),
                managed,
                agent: default_agent(),
                namespace: default_namespace(),
                max_body_bytes: default_limit(),
            },
        }
    }

    #[tokio::test]
    async fn rpc_redirects_are_rejected_before_a_client_can_follow_them() {
        let registry = praxis_filter::FilterRegistry::with_builtins();
        let pipeline = praxis_filter::FilterPipeline::build(&mut [], &registry).expect("pipeline");
        let request = praxis_filter::Request {
            method: http::Method::POST,
            uri: http::Uri::from_static("/"),
            headers: http::HeaderMap::new(),
        };
        let mut protocol = praxis_protocol::http::pingora::context::PingoraRequestCtx::default();
        let mut response = praxis_filter::Response {
            status: http::StatusCode::TEMPORARY_REDIRECT,
            headers: http::HeaderMap::new(),
        };
        response.headers.insert(
            http::header::LOCATION,
            http::HeaderValue::from_static("https://backend.example/rpc"),
        );
        let mut ctx = protocol.build_filter_context(&pipeline, &request, Some(&mut response));
        assert!(test_filter(false).on_response(&mut ctx).await.is_err());
    }

    #[tokio::test]
    #[expect(
        clippy::too_many_lines,
        reason = "managed selection and live update fixtures use real protocol contexts"
    )]
    async fn managed_routes_select_the_current_agent_with_namespace_isolation() {
        let registry = wanaku_infra::registry::InMemoryRegistry::new();
        let default: wanaku_types::agents::AgentEntry =
            serde_json::from_str(r#"{"name":"worker","address":"http://default.example/rpc"}"#)
                .expect("default agent");
        assert!(registry.save_agent(default, false).expect("create default"));
        let mut blue: wanaku_types::agents::AgentEntry = serde_json::from_str(r#"{"name":"worker","namespace":"blue","address":"http://blue.example/rpc","cardAddress":"https://cards.example/blue.json"}"#).expect("blue agent");
        assert!(
            registry
                .save_agent(blue.clone(), false)
                .expect("create blue")
        );
        let filters = praxis_filter::FilterRegistry::with_builtins();
        let pipeline = praxis_filter::FilterPipeline::build(&mut [], &filters).expect("pipeline");
        let request = praxis_filter::Request {
            method: http::Method::POST,
            uri: http::Uri::from_static("/blue/a2a/worker"),
            headers: http::HeaderMap::new(),
        };
        let mut protocol = praxis_protocol::http::pingora::context::PingoraRequestCtx::default();
        let mut ctx = protocol.build_filter_context(&pipeline, &request, None);
        ctx.extensions.insert(registry.clone());
        let filter = test_filter(true);
        assert!(filter.select_agent(&mut ctx).is_ok());
        assert_eq!(
            &*ctx.upstream.as_ref().expect("upstream").address,
            "blue.example:80"
        );
        assert_eq!(ctx.rewritten_path.as_deref(), Some("/rpc"));
        assert_eq!(
            ctx.get_metadata(wanaku_types::NAMESPACE_METADATA_KEY),
            Some("blue")
        );
        assert_eq!(
            ctx.get_metadata("wanaku.a2a.public_url"),
            Some("https://proxy.example/blue/a2a/worker")
        );
        blue.address = "http://updated.example/new-rpc".to_owned();
        assert!(registry.save_agent(blue, true).expect("update"));
        assert!(filter.select_agent(&mut ctx).is_ok());
        assert_eq!(
            &*ctx.upstream.as_ref().expect("updated upstream").address,
            "updated.example:80"
        );
        assert_eq!(ctx.rewritten_path.as_deref(), Some("/new-rpc"));
        assert!(registry.remove_agent("blue", "worker"));
        assert!(filter.select_agent(&mut ctx).is_err());
        assert!(registry.get_agent("default", "worker").is_some());
    }

    #[tokio::test]
    #[expect(
        clippy::too_many_lines,
        reason = "protocol callback regression includes a split response fixture"
    )]
    async fn discovery_rewrites_split_body_without_response_headers_in_body_context() {
        let registry = praxis_filter::FilterRegistry::with_builtins();
        let pipeline =
            praxis_filter::FilterPipeline::build(&mut [], &registry).expect("test pipeline");
        let request = praxis_filter::Request {
            method: http::Method::GET,
            uri: http::Uri::from_static("/.well-known/agent-card.json"),
            headers: http::HeaderMap::new(),
        };
        let mut protocol_ctx =
            praxis_protocol::http::pingora::context::PingoraRequestCtx::default();
        let mut response = praxis_filter::Response {
            status: http::StatusCode::OK,
            headers: http::HeaderMap::new(),
        };
        response.headers.insert(
            http::header::CONTENT_LENGTH,
            http::HeaderValue::from_static("999"),
        );
        let mut ctx = protocol_ctx.build_filter_context(&pipeline, &request, Some(&mut response));
        ctx.current_filter_id = Some(0);
        let filter = A2aFilter {
            config: Config {
                managed: false,
                public_url: "https://proxy.example/".to_owned(),
                agent: default_agent(),
                namespace: default_namespace(),
                max_body_bytes: default_limit(),
            },
        };
        assert!(matches!(
            filter
                .on_request(&mut ctx)
                .await
                .expect("GET without body hook"),
            FilterAction::Continue
        ));
        assert!(matches!(
            filter
                .on_response(&mut ctx)
                .await
                .expect("successful upstream response"),
            FilterAction::Continue
        ));
        assert!(
            ctx.response_header
                .as_ref()
                .expect("header phase")
                .headers
                .get(http::header::CONTENT_LENGTH)
                .is_none()
        );
        // Praxis body callbacks have no response header; state must carry eligibility.
        ctx.response_header = None;
        let mut first = Some(Bytes::from_static(
            b"{\"protocolVersion\":\"0.3.0\",\"url\":",
        ));
        assert!(matches!(
            filter
                .on_response_body(&mut ctx, &mut first, false)
                .expect("partial card"),
            FilterAction::Continue
        ));
        assert!(first.is_none(), "do not send the unmodified partial card");
        let mut second = Some(Bytes::from_static(
            b"\"http://backend/\",\"capabilities\":{\"streaming\":true}}",
        ));
        assert!(matches!(
            filter
                .on_response_body(&mut ctx, &mut second, true)
                .expect("complete card"),
            FilterAction::Continue
        ));
        let card: Value =
            serde_json::from_slice(second.as_ref().expect("rewritten card")).expect("JSON card");
        assert_eq!(card["url"], "https://proxy.example/");
        assert_eq!(card["capabilities"]["streaming"], false);
    }

    fn request(method: &str, params: &Value) -> Value {
        serde_json::json!({"jsonrpc": "2.0", "id": 7, "method": method, "params": params})
    }

    #[test]
    fn basic_operations_require_valid_parameters() {
        let message = serde_json::json!({"message": {"messageId": "m1", "role": "user", "parts": [{"kind": "text", "text": "hello"}]}});
        assert!(validate_request(&request("message/send", &message), "SendMessage").is_ok());
        for method in ["GetTask", "CancelTask"] {
            assert!(
                validate_request(&request(method, &serde_json::json!({"id": "t1"})), method)
                    .is_ok()
            );
            for params in [
                serde_json::json!({}),
                serde_json::json!({"id": ""}),
                serde_json::json!({"id": 7}),
            ] {
                assert_eq!(
                    validate_request(&request(method, &params), method)
                        .expect_err("invalid task id")
                        .0,
                    -32602
                );
            }
        }
        assert!(
            validate_request(
                &request("SendMessage", &serde_json::json!({"message": {}})),
                "SendMessage"
            )
            .is_err()
        );
    }

    #[test]
    fn unsupported_operations_are_rejected() {
        for method in [
            "SendStreamingMessage",
            "SubscribeToTask",
            "ListTasks",
            "GetExtendedAgentCard",
            "CreateTaskPushNotificationConfig",
            "unknown",
        ] {
            assert_eq!(
                validate_request(&request(method, &serde_json::json!({})), method)
                    .expect_err("unsupported method")
                    .0,
                -32601
            );
        }
    }

    #[test]
    fn hidden_streaming_and_push_configuration_are_rejected() {
        for configuration in [
            serde_json::json!({"stream": true}),
            serde_json::json!({"streaming": true}),
            serde_json::json!({"pushNotificationConfig": {}}),
        ] {
            let params = serde_json::json!({"configuration": configuration, "message": {"messageId": "m1", "role": "user", "parts": [{"kind": "text", "text": "hello"}]}});
            assert_eq!(
                validate_request(&request("SendMessage", &params), "SendMessage")
                    .expect_err("unsupported configuration")
                    .0,
                -32602
            );
        }
        assert_eq!(
            validate_request(&Value::Null, "SendMessage")
                .expect_err("invalid envelope")
                .0,
            -32600
        );
    }

    #[test]
    fn discovery_exposes_only_the_governed_proxy_binding() {
        let mut card = serde_json::json!({"protocolVersion": "0.3.0", "url": "http://backend/", "additionalInterfaces": [{"url": "http://bypass/"}], "signatures": [{"signature": "invalid-after-rewrite"}], "capabilities": {"streaming": true, "pushNotifications": true}, "supportsAuthenticatedExtendedCard": true});
        rewrite_card(&mut card, "https://proxy.example/").expect("valid card");
        assert_eq!(card["url"], "https://proxy.example/");
        assert_eq!(card["capabilities"]["streaming"], false);
        assert_eq!(card["capabilities"]["pushNotifications"], false);
        assert_eq!(card["supportsAuthenticatedExtendedCard"], false);
        assert!(card.get("additionalInterfaces").is_none());
        assert!(card.get("signatures").is_none());
        card["protocolVersion"] = serde_json::json!("1.0.0");
        assert!(rewrite_card(&mut card, "https://proxy.example/").is_err());
    }

    #[test]
    fn configuration_requires_a_proxy_origin() {
        for public_url in [
            "ftp://proxy.example/",
            "/relative",
            "http://proxy.example/rpc",
            "http://proxy.example/?x=1",
        ] {
            let config = serde_yaml::to_value(serde_json::json!({"public_url": public_url}))
                .expect("yaml config");
            assert!(A2aFilter::from_config(&config).is_err());
        }
        let config =
            serde_yaml::to_value(serde_json::json!({"public_url": "https://proxy.example/"}))
                .expect("yaml config");
        assert!(A2aFilter::from_config(&config).is_ok());
    }
}
