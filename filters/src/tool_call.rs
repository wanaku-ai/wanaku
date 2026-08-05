use std::collections::HashMap;

use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::{trace, warn};
use wanaku_praxis_apis::grpc::GrpcPool;
use wanaku_praxis_apis::registry::{InMemoryRegistry, ServiceRegistry, ToolEntry, ToolRegistry};

crate::body_filter_boilerplate!(ToolCallFilter, "wanaku_tool_call");

struct ParsedBody {
    id: serde_json::Value,
    arguments: HashMap<String, String>,
}

fn parse_body(body: &Option<Bytes>) -> ParsedBody {
    let Some(body_bytes) = body else {
        return ParsedBody {
            id: serde_json::Value::Null,
            arguments: HashMap::new(),
        };
    };

    let Ok(parsed) = serde_json::from_slice::<serde_json::Value>(body_bytes) else {
        return ParsedBody {
            id: serde_json::Value::Null,
            arguments: HashMap::new(),
        };
    };

    let id = parsed
        .get("id")
        .cloned()
        .unwrap_or(serde_json::Value::Null);

    let arguments = parsed
        .get("params")
        .and_then(|p| p.get("arguments"))
        .and_then(|a| a.as_object())
        .map(|args| {
            args.iter()
                .map(|(k, v)| {
                    let value_str = match v {
                        serde_json::Value::String(s) => s.clone(),
                        other => other.to_string(),
                    };
                    (k.clone(), value_str)
                })
                .collect()
        })
        .unwrap_or_default();

    ParsedBody { id, arguments }
}

impl ToolCallFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let method = match ctx.get_metadata(crate::MCP_METHOD_KEY) {
            Some(m) => m,
            None => return Ok(FilterAction::Continue),
        };

        if method != "tools/call" {
            return Ok(FilterAction::Continue);
        }

        let tool_name = match ctx.get_metadata(crate::MCP_NAME_KEY) {
            Some(n) => n.to_owned(),
            None => {
                let parsed = parse_body(body);
                return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INVALID_PARAMS, "missing tool name in tools/call"));
            }
        };

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);

        let mut parsed = parse_body(body);

        let conversation_id = parsed.arguments
            .remove(wanaku_praxis_apis::correlation::REQUEST_ID_ARG)
            .unwrap_or_else(|| "-".to_owned());

        let request_id = ctx.request.headers.get("x-request-id")
            .and_then(|v| v.to_str().ok())
            .unwrap_or("-");

        for (name, value) in &ctx.request.headers {
            tracing::debug!(header = %name, value = ?value, "tools/call request header");
        }

        tracing::info!(
            tool = %tool_name,
            namespace = %namespace,
            conversation_id = %conversation_id,
            x_request_id = %request_id,
            "tools/call"
        );

        tracing::debug!(
            tool = %tool_name,
            arguments = ?parsed.arguments,
            "parsed tools/call request body (x-request-id stripped)"
        );

        let registry = match ctx.extensions.get::<InMemoryRegistry>() {
            Some(r) => r,
            None => {
                tracing::error!("InMemoryRegistry not found in request extensions");
                return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INTERNAL_ERROR, "internal error: registry unavailable"));
            }
        };

        let tool = match registry.get_tool_in_namespace(namespace, &tool_name) {
            Some(t) => {
                tracing::debug!(
                    tool = %t.name,
                    uri = %t.uri,
                    type_ = %t.type_,
                    "resolved tool from registry"
                );
                t
            }
            None => {
                warn!(tool = %tool_name, "tool not found in registry");
                return Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INVALID_PARAMS,
                    &format!("tool not found: {tool_name}"),
                ));
            }
        };

        if tool.type_ == wanaku_praxis_apis::registry::MCP_FORWARD_TYPE {
            return self.handle_forwarded_call(&tool, &tool_name, &parsed).await;
        }

        let service = match registry.resolve_service(&tool.type_, "tool-invoker") {
            Ok(s) => s,
            Err(e) => {
                warn!(tool_type = %tool.type_, error = %e, "no service available for tool type");
                return Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("no service available for tool type: {}", tool.type_),
                ));
            }
        };

        let grpc_pool = match ctx.extensions.get::<GrpcPool>() {
            Some(p) => p.clone(),
            None => {
                tracing::error!("GrpcPool not found in request extensions");
                return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INTERNAL_ERROR, "internal error: gRPC pool unavailable"));
            }
        };

        trace!(
            tool = %tool_name,
            uri = %tool.uri,
            service = %service.address,
            "invoking tool via gRPC"
        );

        match grpc_pool
            .invoke_tool(&service.address, tool.uri.clone(), parsed.arguments)
            .await
        {
            Ok(content) => {
                let mcp_content: Vec<serde_json::Value> = content
                    .iter()
                    .map(|text| {
                        serde_json::json!({
                            "type": "text",
                            "text": text,
                        })
                    })
                    .collect();

                let response = serde_json::json!({
                    "jsonrpc": "2.0",
                    "id": parsed.id,
                    "result": {
                        "content": mcp_content,
                    }
                });

                let response_body = Bytes::from(response.to_string());
                Ok(FilterAction::Reject(crate::response::json_response(response_body)))
            }
            Err(e) => {
                warn!(tool = %tool_name, error = %e, "gRPC invocation failed");
                Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("tool invocation failed: {e}"),
                ))
            }
        }
    }

    async fn handle_forwarded_call(
        &self,
        tool: &ToolEntry,
        tool_name: &str,
        parsed: &ParsedBody,
    ) -> Result<FilterAction, FilterError> {
        trace!(tool = %tool_name, uri = %tool.uri, "forwarding tools/call to remote MCP server");

        let arguments = serde_json::Value::Object(
            parsed
                .arguments
                .iter()
                .map(|(k, v)| (k.clone(), serde_json::Value::String(v.clone())))
                .collect(),
        );

        match wanaku_praxis_apis::mcp_client::call_tool(&tool.uri, tool_name, arguments).await {
            Ok(content) => {
                let mcp_content: Vec<serde_json::Value> = content
                    .iter()
                    .map(|text| serde_json::json!({"type": "text", "text": text}))
                    .collect();

                let response = serde_json::json!({
                    "jsonrpc": "2.0",
                    "id": parsed.id,
                    "result": {"content": mcp_content}
                });

                let response_body = Bytes::from(response.to_string());
                Ok(FilterAction::Reject(crate::response::json_response(response_body)))
            }
            Err(e) => {
                warn!(tool = %tool_name, error = %e, "MCP forward call failed");
                Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("forwarded tool call failed: {e}"),
                ))
            }
        }
    }
}
