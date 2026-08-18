use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use wanaku_praxis_apis::registry::{InMemoryRegistry, ToolRegistry};

crate::body_filter_boilerplate!(ToolListFilter, "wanaku_tool_list");

impl ToolListFilter {
    #[expect(clippy::too_many_lines, reason = "MCP protocol handler with JSON-RPC response construction")]
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let Some(method) = ctx.get_metadata(crate::MCP_METHOD_KEY) else {
            return Ok(FilterAction::Continue);
        };

        if method != "tools/list" {
            return Ok(FilterAction::Continue);
        }

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);

        tracing::debug!(namespace = %namespace, "handling MCP tools/list request");

        let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() else {
            tracing::error!("InMemoryRegistry not found in request extensions");
            let json_rpc_id = crate::response::extract_json_rpc_id(body);
            return Ok(crate::response::json_rpc_error(
                &json_rpc_id,
                crate::response::JSONRPC_INTERNAL_ERROR,
                "internal error: registry unavailable",
            ));
        };

        let tools = registry.list_tools_in_namespace(namespace);

        tracing::debug!(namespace = %namespace, tool_count = tools.len(), "tools found in namespace");
        let mcp_tools: Vec<serde_json::Value> = tools
            .iter()
            .map(|t| {
                serde_json::json!({
                    "name": t.name,
                    "description": t.description,
                    "inputSchema": t.input_schema,
                })
            })
            .collect();

        let json_rpc_id = crate::response::extract_json_rpc_id(body);

        let response = serde_json::json!({
            "jsonrpc": "2.0",
            "id": json_rpc_id,
            "result": {
                "tools": mcp_tools,
            }
        });

        let response_body = Bytes::from(response.to_string());
        Ok(FilterAction::Reject(crate::response::json_response(response_body)))
    }
}
