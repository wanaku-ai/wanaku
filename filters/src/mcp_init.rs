use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::trace;

crate::body_filter_boilerplate!(McpInitFilter, "wanaku_mcp_init");

impl McpInitFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
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
