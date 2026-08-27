use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::trace;

crate::body_filter_boilerplate!(McpInitFilter, "wanaku_mcp_init");

impl McpInitFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        _body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let Some(method) = ctx.get_metadata(crate::MCP_METHOD_KEY) else {
            return Ok(FilterAction::Continue);
        };

        let json_rpc_id = crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));

        match method {
            "initialize" => self.handle_initialize(&json_rpc_id),
            "notifications/initialized" => Self::handle_notification(),
            "ping" => Self::handle_ping(&json_rpc_id),
            _ => Ok(FilterAction::Continue),
        }
    }

    #[expect(clippy::unused_self, reason = "consistent signature across filter handler methods")]
    fn handle_initialize(&self, json_rpc_id: &serde_json::Value) -> Result<FilterAction, FilterError> {
        trace!("handling MCP initialize");

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
                "serverInfo": Self::server_info()
            }
        });

        let response_body = Bytes::from(response.to_string());
        Ok(FilterAction::Reject(crate::response::json_response(response_body)))
    }

    /// Builds the `serverInfo` object advertised to MCP clients during initialization.
    ///
    /// The `name` is kept as the fixed literal `wanaku-server` (the client-visible
    /// server name), while the `version` is resolved dynamically at compile time from
    /// the crate version so it never drifts out of sync with releases.
    fn server_info() -> serde_json::Value {
        serde_json::json!({
            "name": "wanaku-server",
            "version": env!("CARGO_PKG_VERSION"),
        })
    }

    fn handle_notification() -> Result<FilterAction, FilterError> {
        trace!("handling MCP notification");
        Ok(FilterAction::Reject(crate::response::empty_accepted()))
    }

    fn handle_ping(json_rpc_id: &serde_json::Value) -> Result<FilterAction, FilterError> {
        trace!("handling MCP ping");

        let response = serde_json::json!({
            "jsonrpc": "2.0",
            "id": json_rpc_id,
            "result": {}
        });

        let response_body = Bytes::from(response.to_string());
        Ok(FilterAction::Reject(crate::response::json_response(response_body)))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn server_info_name_is_wanaku_server() {
        // The client-visible server name must remain `wanaku-server` regardless of the
        // crate name (`wanaku-filters`), so this must stay a fixed literal.
        let info = McpInitFilter::server_info();
        assert_eq!(info["name"], "wanaku-server");
    }

    #[test]
    fn server_info_version_matches_crate_version() {
        // The version is resolved dynamically at compile time and inherits the
        // workspace version, so it stays in sync with releases automatically.
        let info = McpInitFilter::server_info();
        assert_eq!(info["version"], env!("CARGO_PKG_VERSION"));
    }
}
