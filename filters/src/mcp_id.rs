use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};

crate::body_filter_boilerplate!(McpIdFilter, "wanaku_mcp_id");

impl McpIdFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let id = crate::response::extract_json_rpc_id(body);
        let id_json = id.to_string();
        ctx.set_metadata(crate::MCP_ID_KEY, &id_json);
        Ok(FilterAction::Continue)
    }
}
