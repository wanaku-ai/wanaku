//! The initial, non-streaming A2A JSON-RPC boundary.
use bytes::{Bytes, BytesMut};
use praxis_filter::{
    BodyAccess, BodyMode, FilterAction, FilterError, HttpFilter, HttpFilterContext,
};
use serde::Deserialize;
use serde_json::Value;

mod discovery;
mod request;
use discovery::rewrite_card;

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
        let address = ctx
            .extensions
            .get::<wanaku_infra::registry::InMemoryRegistry>()
            .and_then(|registry| registry.get_agent(namespace, name))
            .map(|entry| entry.address);
        if let Some(address) = address {
            ctx.set_metadata("wanaku.a2a.upstream_url", address);
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
        let version = match request::Version::from_headers(&ctx.request.headers) {
            Ok(version) => version,
            Err((code, message)) => return rpc_error(&id, code, message),
        };
        ctx.set_metadata("wanaku.a2a.version", version.as_str());
        let method = ctx.get_metadata("a2a.method").unwrap_or_default();
        if let Err((code, message)) = request::validate_request(&request, method, version) {
            return rpc_error(&id, code, message);
        }
        if ctx.get_metadata("a2a.streaming") == Some("true") {
            return rpc_error(
                &id,
                if version == request::Version::V1 {
                    -32004
                } else {
                    -32602
                },
                "Streaming is not supported.",
            );
        }
        ctx.set_metadata(wanaku_filters::MCP_ID_KEY, id.to_string());
        if !self.config.managed {
            ctx.set_metadata(wanaku_types::NAMESPACE_METADATA_KEY, &self.config.namespace);
            ctx.set_metadata("wanaku.a2a.agent", &self.config.agent);
        }
        FilterAction::Continue
    }

    fn capture_static_upstream(&self, ctx: &mut HttpFilterContext<'_>) {
        if self.config.managed
            || ctx.get_metadata("wanaku.a2a.upstream_url").is_some()
            || ctx
                .rewritten_path
                .as_deref()
                .is_some_and(|path| path != ctx.request.uri.path())
        {
            return;
        }
        if let Some(upstream) = ctx.upstream.as_ref() {
            let authority = upstream
                .authority
                .as_ref()
                .and_then(|authority| authority.to_str().ok())
                .unwrap_or(&upstream.address);
            let scheme = if upstream.tls.is_some() {
                "https"
            } else {
                "http"
            };
            // With no discovery rewrite, the static JSON-RPC endpoint is POST /.
            ctx.set_metadata(
                "wanaku.a2a.upstream_url",
                format!("{scheme}://{authority}/"),
            );
        }
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

#[cfg(test)]
fn validate_legacy_request(request: &Value, method: &str) -> Result<(), (i32, &'static str)> {
    request::validate_request(request, method, request::Version::Legacy)
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
            self.capture_static_upstream(ctx);
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
            ctx.get_metadata("wanaku.a2a.upstream_url"),
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

    #[tokio::test]
    async fn v1_request_keeps_original_body_and_sets_effective_version() {
        let registry = praxis_filter::FilterRegistry::with_builtins();
        let pipeline = praxis_filter::FilterPipeline::build(&mut [], &registry).expect("pipeline");
        let mut headers = http::HeaderMap::new();
        headers.insert("a2a-version", http::HeaderValue::from_static("1.0"));
        let request = praxis_filter::Request {
            method: http::Method::POST,
            uri: http::Uri::from_static("/"),
            headers,
        };
        let mut protocol = praxis_protocol::http::pingora::context::PingoraRequestCtx::default();
        let mut ctx = protocol.build_filter_context(&pipeline, &request, None);
        ctx.set_metadata("a2a.method", "SendMessage");
        let body = Bytes::from_static(br#"{"jsonrpc":"2.0","id":1,"method":"SendMessage","params":{"message":{"messageId":"message","role":"ROLE_USER","parts":[{"text":"hello"}]}}}"#);
        assert!(matches!(
            test_filter(false).handle_rpc_request(&mut ctx, Some(&body)),
            FilterAction::Continue
        ));
        assert_eq!(ctx.get_metadata("wanaku.a2a.version"), Some("1.0"));
        assert_eq!(
            ctx.request.headers.get("a2a-version").expect("header"),
            "1.0"
        );
        assert_eq!(ctx.get_metadata("wanaku.a2a.agent"), Some("a2a"));
    }

    #[tokio::test]
    async fn static_v1_discovery_uses_selected_upstream_root_for_interface_matching() {
        let agents = wanaku_infra::registry::InMemoryRegistry::new();
        let entry: wanaku_types::agents::AgentEntry = serde_json::from_value(
            serde_json::json!({"name":"backend", "address":"https://backend.example/"}),
        )
        .expect("entry");
        agents.save_agent(entry, false).expect("save agent");
        let registry = praxis_filter::FilterRegistry::with_builtins();
        let pipeline = praxis_filter::FilterPipeline::build(&mut [], &registry).expect("pipeline");
        let request = praxis_filter::Request {
            method: http::Method::GET,
            uri: http::Uri::from_static("/.well-known/agent-card.json"),
            headers: http::HeaderMap::new(),
        };
        let filter = test_filter(false);
        for (upstream_path, compatible, authority_override, rewrite_discovery) in [
            ("/", true, true, false),
            ("/", true, false, false),
            ("/rpc", false, true, false),
            ("/", false, true, true),
        ] {
            let mut protocol =
                praxis_protocol::http::pingora::context::PingoraRequestCtx::default();
            let mut response = praxis_filter::Response {
                status: http::StatusCode::OK,
                headers: http::HeaderMap::new(),
            };
            let mut ctx = protocol.build_filter_context(&pipeline, &request, Some(&mut response));
            let mut upstream = agents
                .agent_endpoint("default", "backend", false)
                .expect("endpoint")
                .upstream;
            if !authority_override {
                upstream.authority = None;
            }
            ctx.upstream = Some(upstream);
            if rewrite_discovery {
                ctx.rewritten_path = Some("/rpc/.well-known/agent-card.json".to_owned());
            }
            ctx.current_filter_id = Some(0);
            assert!(matches!(
                filter
                    .on_request(&mut ctx)
                    .await
                    .expect("discovery request"),
                FilterAction::Continue
            ));
            assert!(matches!(
                filter
                    .on_response(&mut ctx)
                    .await
                    .expect("response headers"),
                FilterAction::Continue
            ));
            assert_eq!(
                ctx.get_metadata("wanaku.a2a.upstream_url"),
                if rewrite_discovery {
                    None
                } else {
                    Some(if authority_override {
                        "https://backend.example/"
                    } else {
                        "https://backend.example:443/"
                    })
                }
            );
            ctx.response_header = None;
            let first = format!(
                r#"{{"supportedInterfaces":[{{"url":"https://backend.example{upstream_path}","protocolBinding":"JSONRPC","protocolVersion":"1.0","tenant":"blue"}}],"capabilities":{{"#
            );
            let mut first = Some(Bytes::from(first));
            assert!(matches!(
                filter
                    .on_response_body(&mut ctx, &mut first, false)
                    .expect("first chunk"),
                FilterAction::Continue
            ));
            assert!(first.is_none());
            let mut last = Some(Bytes::from_static(br#""extendedAgentCard":true}}"#));
            let action = filter.on_response_body(&mut ctx, &mut last, true);
            if compatible {
                assert!(matches!(
                    action.expect("compatible card"),
                    FilterAction::Continue
                ));
                let card: Value =
                    serde_json::from_slice(last.as_ref().expect("card")).expect("JSON");
                assert_eq!(
                    card["supportedInterfaces"][0]["url"],
                    "https://proxy.example/"
                );
                assert_eq!(card["supportedInterfaces"][0]["tenant"], "blue");
                assert_eq!(card["capabilities"]["extendedAgentCard"], false);
            } else {
                assert!(
                    action.is_err(),
                    "do not advertise a JSON-RPC path this pipeline cannot serve"
                );
            }
        }
    }

    fn request(method: &str, params: &Value) -> Value {
        serde_json::json!({"jsonrpc": "2.0", "id": 7, "method": method, "params": params})
    }

    #[test]
    fn basic_operations_require_valid_parameters() {
        let message = serde_json::json!({"message": {"messageId": "m1", "role": "user", "parts": [{"kind": "text", "text": "hello"}]}});
        assert!(validate_legacy_request(&request("message/send", &message), "SendMessage").is_ok());
        for method in ["GetTask", "CancelTask"] {
            assert!(
                validate_legacy_request(&request(method, &serde_json::json!({"id": "t1"})), method)
                    .is_ok()
            );
            for params in [
                serde_json::json!({}),
                serde_json::json!({"id": ""}),
                serde_json::json!({"id": 7}),
            ] {
                assert_eq!(
                    validate_legacy_request(&request(method, &params), method)
                        .expect_err("invalid task id")
                        .0,
                    -32602
                );
            }
        }
        assert!(
            validate_legacy_request(
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
                validate_legacy_request(&request(method, &serde_json::json!({})), method)
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
                validate_legacy_request(&request("SendMessage", &params), "SendMessage")
                    .expect_err("unsupported configuration")
                    .0,
                -32602
            );
        }
        assert_eq!(
            validate_legacy_request(&Value::Null, "SendMessage")
                .expect_err("invalid envelope")
                .0,
            -32600
        );
    }

    #[test]
    fn discovery_exposes_only_the_governed_proxy_binding() {
        let mut card = serde_json::json!({"protocolVersion": "0.3.0", "url": "http://backend/", "additionalInterfaces": [{"url": "http://bypass/"}], "signatures": [{"signature": "invalid-after-rewrite"}], "capabilities": {"streaming": true, "pushNotifications": true}, "supportsAuthenticatedExtendedCard": true});
        rewrite_card(&mut card, "https://proxy.example/", None).expect("valid card");
        assert_eq!(card["url"], "https://proxy.example/");
        assert_eq!(card["capabilities"]["streaming"], false);
        assert_eq!(card["capabilities"]["pushNotifications"], false);
        assert_eq!(card["supportsAuthenticatedExtendedCard"], false);
        assert!(card.get("additionalInterfaces").is_none());
        assert!(card.get("signatures").is_none());
        card["protocolVersion"] = serde_json::json!("2.0.0");
        assert!(rewrite_card(&mut card, "https://proxy.example/", None).is_err());
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
