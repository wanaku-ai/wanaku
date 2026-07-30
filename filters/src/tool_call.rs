use std::collections::HashMap;

use async_trait::async_trait;
use bytes::Bytes;
use praxis_filter::{
    BodyAccess, BodyMode, FilterAction, FilterError, HttpFilter, HttpFilterContext,
};
use tracing::{trace, warn};
use wanaku_praxis_apis::grpc::GrpcPool;
use wanaku_praxis_apis::registry::{InMemoryRegistry, ServiceRegistry, ToolRegistry};

pub struct ToolCallFilter {
    max_body_bytes: usize,
}

impl ToolCallFilter {
    pub fn from_config(config: &serde_yaml::Value) -> Result<Box<dyn HttpFilter>, FilterError> {
        let max_body_bytes = config
            .get("max_body_bytes")
            .and_then(serde_yaml::Value::as_u64)
            .unwrap_or(1_048_576) as usize;

        Ok(Box::new(Self { max_body_bytes }))
    }
}

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

fn json_rpc_error(id: &serde_json::Value, code: i32, message: &str) -> FilterAction {
    let response = serde_json::json!({
        "jsonrpc": "2.0",
        "id": id,
        "error": {
            "code": code,
            "message": message,
        }
    });
    let body = Bytes::from(response.to_string());
    FilterAction::Reject(crate::response::json_response(body))
}

#[async_trait]
impl HttpFilter for ToolCallFilter {
    fn name(&self) -> &'static str {
        "wanaku_tool_call"
    }

    fn request_body_access(&self) -> BodyAccess {
        BodyAccess::ReadOnly
    }

    fn request_body_mode(&self) -> BodyMode {
        BodyMode::StreamBuffer {
            max_bytes: Some(self.max_body_bytes),
        }
    }

    async fn on_request(&self, _ctx: &mut HttpFilterContext<'_>) -> Result<FilterAction, FilterError> {
        Ok(FilterAction::Continue)
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

        let method = match ctx.get_metadata("mcp.method") {
            Some(m) => m,
            None => return Ok(FilterAction::Continue),
        };

        if method != "tools/call" {
            return Ok(FilterAction::Continue);
        }

        let tool_name = match ctx.get_metadata("mcp.name") {
            Some(n) => n.to_owned(),
            None => {
                let parsed = parse_body(body);
                return Ok(json_rpc_error(&parsed.id, -32602, "missing tool name in tools/call"));
            }
        };

        trace!(tool = %tool_name, "handling MCP tools/call request");

        let parsed = parse_body(body);

        let registry = match ctx.extensions.get::<InMemoryRegistry>() {
            Some(r) => r,
            None => {
                tracing::error!("InMemoryRegistry not found in request extensions");
                return Ok(json_rpc_error(&parsed.id, -32603, "internal error: registry unavailable"));
            }
        };

        let tool = match registry.get_tool(&tool_name) {
            Some(t) => t,
            None => {
                warn!(tool = %tool_name, "tool not found in registry");
                return Ok(json_rpc_error(
                    &parsed.id,
                    -32602,
                    &format!("tool not found: {tool_name}"),
                ));
            }
        };

        let service = match registry.resolve_service(&tool.type_, "tool-invoker") {
            Ok(s) => s,
            Err(e) => {
                warn!(tool_type = %tool.type_, error = %e, "no service available for tool type");
                return Ok(json_rpc_error(
                    &parsed.id,
                    -32603,
                    &format!("no service available for tool type: {}", tool.type_),
                ));
            }
        };

        let grpc_pool = match ctx.extensions.get::<GrpcPool>() {
            Some(p) => p.clone(),
            None => {
                tracing::error!("GrpcPool not found in request extensions");
                return Ok(json_rpc_error(&parsed.id, -32603, "internal error: gRPC pool unavailable"));
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
                Ok(json_rpc_error(
                    &parsed.id,
                    -32603,
                    &format!("tool invocation failed: {e}"),
                ))
            }
        }
    }
}
