use async_trait::async_trait;
use bytes::Bytes;
use praxis_filter::{
    BodyAccess, BodyMode, FilterAction, FilterError, HttpFilter, HttpFilterContext,
};
use tracing::trace;

pub struct McpInitFilter {
    max_body_bytes: usize,
}

impl McpInitFilter {
    pub fn from_config(config: &serde_yaml::Value) -> Result<Box<dyn HttpFilter>, FilterError> {
        let max_body_bytes = config
            .get("max_body_bytes")
            .and_then(serde_yaml::Value::as_u64)
            .unwrap_or(1_048_576) as usize;

        Ok(Box::new(Self { max_body_bytes }))
    }
}

#[async_trait]
impl HttpFilter for McpInitFilter {
    fn name(&self) -> &'static str {
        "wanaku_mcp_init"
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

        match method {
            "initialize" => self.handle_initialize(body),
            "notifications/initialized" => Self::handle_notification(),
            "ping" => Self::handle_ping(body),
            _ => Ok(FilterAction::Continue),
        }
    }
}

impl McpInitFilter {
    fn handle_initialize(&self, body: &Option<Bytes>) -> Result<FilterAction, FilterError> {
        trace!("handling MCP initialize");

        let json_rpc_id = crate::response::extract_json_rpc_id(body);
        let response = serde_json::json!({
            "jsonrpc": "2.0",
            "id": json_rpc_id,
            "result": {
                "protocolVersion": "2025-03-26",
                "capabilities": {
                    "tools": {
                        "listChanged": false
                    },
                    "resources": {
                        "listChanged": false
                    },
                    "prompts": {
                        "listChanged": false
                    }
                },
                "serverInfo": {
                    "name": "wanaku-praxis",
                    "version": "0.1.0"
                }
            }
        });

        let response_body = Bytes::from(response.to_string());
        Ok(FilterAction::Reject(crate::response::json_response(response_body)))
    }

    fn handle_notification() -> Result<FilterAction, FilterError> {
        trace!("handling MCP notification");
        Ok(FilterAction::Reject(crate::response::empty_accepted()))
    }

    fn handle_ping(body: &Option<Bytes>) -> Result<FilterAction, FilterError> {
        trace!("handling MCP ping");

        let json_rpc_id = crate::response::extract_json_rpc_id(body);
        let response = serde_json::json!({
            "jsonrpc": "2.0",
            "id": json_rpc_id,
            "result": {}
        });

        let response_body = Bytes::from(response.to_string());
        Ok(FilterAction::Reject(crate::response::json_response(response_body)))
    }
}

