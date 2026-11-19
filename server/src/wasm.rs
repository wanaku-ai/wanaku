//! Scoped MCP access for WASM guests through the configured listener pipeline.

use std::{
    collections::HashMap,
    net::IpAddr,
    sync::{Arc, Weak},
};

use bytes::Bytes;
use praxis_filter::{
    FilterAction, FilterPipeline, HttpFilterContext, PipelineExtension, RequestExtensions,
};
use praxis_protocol::http::pingora::context::PingoraRequestCtx;
use serde_json::{Value, json};
use wanaku_infra::{mcp_client::McpInvoker, registry::InMemoryRegistry};
use wanaku_types::{
    registry::ToolRegistry,
    wasm::{HostError, HostErrorKind, InvocationContext, McpHostClient, McpHostFactory},
};

/// Holds only a weak pipeline reference, so the pipeline can own this extension.
pub(crate) struct HostExtension {
    pub pipeline: Weak<FilterPipeline>,
    pub registry: InMemoryRegistry,
}

impl PipelineExtension for HostExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        let factory: Arc<dyn McpHostFactory> = Arc::new(HostFactory {
            pipeline: self.pipeline.clone(),
            registry: self.registry.clone(),
        });
        extensions.insert(factory);
    }
}

struct HostFactory {
    pipeline: Weak<FilterPipeline>,
    registry: InMemoryRegistry,
}

impl McpHostFactory for HostFactory {
    fn create(&self, ctx: &HttpFilterContext<'_>) -> Result<Arc<dyn McpHostClient>, HostError> {
        Ok(Arc::new(ScopedClient {
            pipeline: self.pipeline.clone(),
            registry: self.registry.clone(),
            concrete: ctx
                .get_metadata(wanaku_types::NAMESPACE_METADATA_KEY)
                .ok_or_else(|| {
                    HostError::new(HostErrorKind::Denied, "request namespace unavailable")
                })?
                .to_owned(),
            virtual_namespace: ctx
                .get_metadata(wanaku_types::VIRTUAL_NAMESPACE_METADATA_KEY)
                .map(str::to_owned),
            invocation: ctx
                .extensions
                .get::<InvocationContext>()
                .cloned()
                .unwrap_or_default(),
            headers: trusted_headers(ctx)?,
            metadata: inherited_metadata(ctx),
            client_addr: ctx.client_addr,
            downstream_tls: ctx.downstream_tls,
            peer_identity: ctx.peer_identity.clone(),
            invoker: ctx.extensions.get::<Arc<dyn McpInvoker>>().cloned(),
            identity: ctx
                .extensions
                .get::<praxis_filter::AuthenticatedIdentity>()
                .cloned(),
        }))
    }
}

fn inherited_metadata(ctx: &HttpFilterContext<'_>) -> HashMap<String, String> {
    ctx.filter_metadata
        .iter()
        .filter(|(key, _)| !routing_key(key))
        .map(|(key, value)| (key.clone(), value.clone()))
        .collect()
}

fn trusted_headers(ctx: &HttpFilterContext<'_>) -> Result<http::HeaderMap, HostError> {
    let mut headers = ctx.request.headers.clone();
    for (name, value) in &ctx.extra_request_headers {
        let name = http::HeaderName::try_from(name.as_ref()).map_err(|_| {
            HostError::new(HostErrorKind::Internal, "invalid trusted request header")
        })?;
        let value = http::HeaderValue::from_str(value).map_err(|_| {
            HostError::new(HostErrorKind::Internal, "invalid trusted request header")
        })?;
        headers.append(name, value);
    }
    for name in &ctx.request_headers_to_remove {
        headers.remove(name);
    }
    for (name, value) in &ctx.request_headers_to_set {
        headers.insert(name.clone(), value.clone());
    }
    Ok(headers)
}

fn routing_key(key: &str) -> bool {
    key.starts_with("mcp.")
        || key.starts_with("a2a.")
        || key.starts_with("wanaku.a2a.")
        || matches!(
            key,
            wanaku_types::NAMESPACE_METADATA_KEY | wanaku_types::VIRTUAL_NAMESPACE_METADATA_KEY
        )
}

struct ScopedClient {
    pipeline: Weak<FilterPipeline>,
    registry: InMemoryRegistry,
    concrete: String,
    virtual_namespace: Option<String>,
    headers: http::HeaderMap,
    metadata: HashMap<String, String>,
    invocation: InvocationContext,
    client_addr: Option<IpAddr>,
    downstream_tls: bool,
    peer_identity: Option<Arc<praxis_tls::TlsPeerIdentity>>,
    invoker: Option<Arc<dyn McpInvoker>>,
    identity: Option<praxis_filter::AuthenticatedIdentity>,
}

#[async_trait::async_trait]
impl McpHostClient for ScopedClient {
    async fn request(
        &self,
        method: &str,
        namespace: &str,
        params: Value,
    ) -> Result<Value, HostError> {
        self.authorize(namespace)?;
        self.invocation.consume()?;
        if !params.is_object() {
            return Err(HostError::new(
                HostErrorKind::InvalidArguments,
                "MCP params must be an object",
            ));
        }
        match method {
            "tools/get" => Box::pin(self.get_tool(namespace, &params)).await,
            "tools/list"
            | "tools/call"
            | "resources/list"
            | "resources/templates/list"
            | "resources/read"
            | "prompts/list"
            | "prompts/get" => Box::pin(self.request_scoped(method, namespace, params)).await,
            _ => Err(HostError::new(
                HostErrorKind::Denied,
                "MCP method is not exposed to WASM guests",
            )),
        }
    }
}

impl ScopedClient {
    fn authorize(&self, namespace: &str) -> Result<(), HostError> {
        let allowed = match &self.virtual_namespace {
            Some(group) => self.registry.resolve_virtual_member(group, namespace),
            None => namespace == self.concrete,
        };
        if !allowed {
            return Err(HostError::new(
                HostErrorKind::Denied,
                "target namespace is outside the request scope",
            ));
        }
        Ok(())
    }

    async fn request_scoped(
        &self,
        method: &str,
        namespace: &str,
        params: Value,
    ) -> Result<Value, HostError> {
        let result = Box::pin(tokio::time::timeout(
            self.invocation.remaining()?,
            Box::pin(self.dispatch(method, namespace, params)),
        ))
        .await
        .map_err(|_| {
            HostError::new(HostErrorKind::Timeout, "nested MCP call deadline exceeded")
        })??;
        Ok(scope_catalog(
            method,
            namespace,
            result,
            self.virtual_namespace.is_some(),
        ))
    }

    async fn get_tool(&self, namespace: &str, params: &Value) -> Result<Value, HostError> {
        let name = params
            .get("name")
            .and_then(Value::as_str)
            .ok_or_else(|| HostError::new(HostErrorKind::InvalidArguments, "missing tool name"))?;
        let result = Box::pin(self.request_scoped("tools/list", namespace, json!({}))).await?;
        let definition = find_tool_definition(&result, name)?;
        let tool = self
            .registry
            .get_tool_in_namespace(namespace, name)
            .ok_or_else(|| HostError::new(HostErrorKind::NotFound, "tool no longer available"))?;
        Ok(
            json!({"identity":{"namespace":namespace,"name":name},"forward_id":tool.forward_id,"definition":definition}),
        )
    }

    async fn dispatch(
        &self,
        method: &str,
        namespace: &str,
        params: Value,
    ) -> Result<Value, HostError> {
        let pipeline = self.pipeline.upgrade().ok_or_else(|| {
            HostError::new(HostErrorKind::Unavailable, "MCP pipeline unavailable")
        })?;
        let (request, body) = self.build_request(method, namespace, params)?;
        let mut protocol = Box::new(self.protocol_context(&pipeline));
        let mut ctx = protocol.build_filter_context(&pipeline, &request, None);
        ctx.filter_metadata.extend(self.metadata.clone());
        validate_body_size(&pipeline, ctx.request_body_mode, body.len())?;
        Box::pin(execute_pipeline(&pipeline, &mut ctx, Some(body))).await
    }

    fn build_request(
        &self,
        method: &str,
        namespace: &str,
        mut params: Value,
    ) -> Result<(praxis_filter::Request, Bytes), HostError> {
        let path = match &self.virtual_namespace {
            Some(group) => {
                qualify_params(method, namespace, &mut params)?;
                format!("/virtual-ns/{group}/mcp")
            }
            None => format!("/{namespace}/mcp"),
        };
        let body = Bytes::from(
            json!({"jsonrpc":"2.0","id":1,"method":method,"params":params}).to_string(),
        );
        let request = praxis_filter::Request {
            method: http::Method::POST,
            uri: path.parse().map_err(|_| {
                HostError::new(HostErrorKind::InvalidArguments, "invalid target namespace")
            })?,
            headers: payload_headers(&self.headers, body.len()),
        };
        Ok((request, body))
    }

    fn protocol_context(&self, pipeline: &FilterPipeline) -> PingoraRequestCtx {
        let mut protocol = PingoraRequestCtx::default();
        protocol.client_addr = self.client_addr;
        protocol.downstream_tls = self.downstream_tls;
        protocol.peer_identity.clone_from(&self.peer_identity);
        pipeline.prepare_extensions(&mut protocol.extensions);
        protocol.extensions.insert(self.invocation.clone());
        protocol.request_body_mode = pipeline.body_capabilities().request_body_mode;
        if let Some(identity) = &self.identity {
            protocol.extensions.insert(identity.clone());
        }
        if let Some(invoker) = &self.invoker {
            protocol.extensions.insert(invoker.clone());
        }
        protocol
    }
}

fn find_tool_definition<'a>(result: &'a Value, name: &str) -> Result<&'a Value, HostError> {
    result
        .get("tools")
        .and_then(Value::as_array)
        .and_then(|tools| {
            tools
                .iter()
                .find(|tool| tool.get("name").and_then(Value::as_str) == Some(name))
        })
        .ok_or_else(|| {
            HostError::new(
                HostErrorKind::NotFound,
                "tool not found in authorized catalog",
            )
        })
}

fn payload_headers(original: &http::HeaderMap, length: usize) -> http::HeaderMap {
    let mut headers = original.clone();
    headers.remove(http::header::CONTENT_ENCODING);
    headers.remove(http::header::TRANSFER_ENCODING);
    headers.insert(
        http::header::CONTENT_TYPE,
        http::HeaderValue::from_static("application/json"),
    );
    headers.insert(
        http::header::CONTENT_LENGTH,
        http::HeaderValue::from(length),
    );
    headers
}

fn validate_body_size(
    pipeline: &FilterPipeline,
    mode: praxis_filter::BodyMode,
    length: usize,
) -> Result<(), HostError> {
    let mode_limit = match mode {
        praxis_filter::BodyMode::StreamBuffer { max_bytes } => max_bytes,
        praxis_filter::BodyMode::SizeLimit { max_bytes } => Some(max_bytes),
        praxis_filter::BodyMode::Stream => None,
        _ => {
            return Err(HostError::new(
                HostErrorKind::Unavailable,
                "unsupported MCP pipeline body mode",
            ));
        }
    };
    if [pipeline.request_body_ceiling(), mode_limit]
        .into_iter()
        .flatten()
        .any(|limit| length > limit)
    {
        return Err(HostError::new(
            HostErrorKind::Limit,
            "nested MCP request exceeds the listener body limit",
        ));
    }
    Ok(())
}

async fn execute_pipeline(
    pipeline: &FilterPipeline,
    ctx: &mut HttpFilterContext<'_>,
    mut body: Option<Bytes>,
) -> Result<Value, HostError> {
    let action = Box::pin(pipeline.execute_http_request_head(ctx))
        .await
        .map_err(pipeline_error)?;
    if !matches!(action, FilterAction::Continue) {
        return response(action);
    }
    // StreamBuffer runs full-body governance before request filters and fallbacks.
    let action = Box::pin(pipeline.execute_http_request_body(ctx, &mut body, true))
        .await
        .map_err(pipeline_error)?;
    if !matches!(action, FilterAction::Continue) {
        return response(action);
    }
    let action = Box::pin(pipeline.execute_http_request(ctx))
        .await
        .map_err(pipeline_error)?;
    response(action)
}

fn pipeline_error(_: praxis_filter::FilterError) -> HostError {
    HostError::new(HostErrorKind::Internal, "MCP pipeline execution failed")
}

fn response(action: FilterAction) -> Result<Value, HostError> {
    let FilterAction::Reject(rejection) = action else {
        return Err(HostError::new(
            HostErrorKind::Unavailable,
            "MCP operation did not produce a local result",
        ));
    };
    let value: Value = rejection
        .body
        .as_deref()
        .and_then(|body| serde_json::from_slice(body).ok())
        .ok_or_else(|| {
            HostError::new(
                HostErrorKind::Denied,
                "MCP request rejected without a JSON-RPC result",
            )
        })?;
    if let Some(error) = value.get("error") {
        return Err(protocol_error(error));
    }
    value
        .get("result")
        .cloned()
        .ok_or_else(|| HostError::new(HostErrorKind::Protocol, "missing MCP result"))
}

fn protocol_error(error: &Value) -> HostError {
    let code = error
        .get("code")
        .and_then(Value::as_i64)
        .and_then(|code| i32::try_from(code).ok());
    let kind = error
        .get("data")
        .and_then(|data| data.get("kind"))
        .cloned()
        .and_then(|kind| serde_json::from_value(kind).ok())
        .unwrap_or(match code {
            Some(-32003..=-32001) => HostErrorKind::Denied,
            Some(-32602) => HostErrorKind::InvalidArguments,
            Some(-32601) => HostErrorKind::NotFound,
            _ => HostErrorKind::Protocol,
        });
    HostError {
        kind,
        code,
        message: error
            .get("message")
            .and_then(Value::as_str)
            .unwrap_or("MCP protocol error")
            .to_owned(),
    }
}

fn qualify_params(method: &str, namespace: &str, params: &mut Value) -> Result<(), HostError> {
    let key = match method {
        "tools/call" | "prompts/get" => "name",
        "resources/read" => "uri",
        _ => return Ok(()),
    };
    let value = params
        .get(key)
        .and_then(Value::as_str)
        .ok_or_else(|| HostError::new(HostErrorKind::InvalidArguments, "missing MCP target"))?;
    params[key] = Value::String(if key == "uri" {
        let prefix = format!("wanaku://{namespace}/");
        if value.starts_with(&prefix) {
            value.to_owned()
        } else {
            format!("{prefix}{value}")
        }
    } else {
        format!("{namespace}/{value}")
    });
    Ok(())
}

fn scope_catalog(method: &str, namespace: &str, mut result: Value, qualified: bool) -> Value {
    if !qualified {
        return result;
    }
    let key = match method {
        "tools/list" => "tools",
        "prompts/list" => "prompts",
        "resources/list" => "resources",
        "resources/templates/list" => "resourceTemplates",
        _ => return result,
    };
    if let Some(entries) = result.get_mut(key).and_then(Value::as_array_mut) {
        let prefix = format!("{namespace}/");
        entries.retain(|entry| {
            entry
                .get("name")
                .and_then(Value::as_str)
                .is_some_and(|name| name.starts_with(&prefix))
        });
        for entry in entries {
            if let Some(name) = entry
                .get("name")
                .and_then(Value::as_str)
                .and_then(|name| name.strip_prefix(&prefix))
            {
                entry["name"] = Value::String(name.to_owned());
            }
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn preserves_tool_errors_and_classifies_host_errors() {
        let result = json!({"content":[],"isError":true,"structuredContent":{"failed":true},"_meta":{"a":1}});
        let action = FilterAction::Reject(wanaku_filters::response::json_response(Bytes::from(
            json!({"result":result}).to_string(),
        )));
        assert_eq!(response(action).unwrap(), result);
        let action = wanaku_filters::response::json_rpc_host_error(
            &json!(1),
            -32603,
            HostErrorKind::Unavailable,
            "target unavailable",
        );
        let error = response(action).unwrap_err();
        assert_eq!(error.kind, HostErrorKind::Unavailable);
        assert_eq!(error.code, Some(-32603));
    }

    #[test]
    fn scopes_virtual_catalogs_without_losing_definition_fields() {
        let result = scope_catalog(
            "tools/list",
            "a",
            json!({"tools":[
                {"name":"a/shared","outputSchema":{"type":"object"},"annotations":{"readOnlyHint":true},"_meta":{"x":1}},
                {"name":"b/shared"},
            ]}),
            true,
        );
        assert_eq!(result["tools"].as_array().unwrap().len(), 1);
        assert_eq!(result["tools"][0]["name"], "shared");
        assert_eq!(result["tools"][0]["outputSchema"], json!({"type":"object"}));
        assert_eq!(result["tools"][0]["_meta"]["x"], 1);
    }
}
