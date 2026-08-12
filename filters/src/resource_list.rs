use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::trace;
use wanaku_praxis_apis::registry::{InMemoryRegistry, ResourceRegistry};

crate::body_filter_boilerplate!(ResourceListFilter, "wanaku_resource_list");

impl ResourceListFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let method = match ctx.get_metadata(crate::MCP_METHOD_KEY) {
            Some(m) => m,
            None => return Ok(FilterAction::Continue),
        };

        if method != "resources/list" {
            return Ok(FilterAction::Continue);
        }

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);

        trace!(namespace = %namespace, "handling MCP resources/list request");

        let registry = match ctx.extensions.get::<InMemoryRegistry>() {
            Some(r) => r,
            None => {
                tracing::error!("InMemoryRegistry not found in request extensions");
                let json_rpc_id = crate::response::extract_json_rpc_id(body);
                return Ok(crate::response::json_rpc_error(
                    &json_rpc_id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    "internal error: registry unavailable",
                ));
            }
        };

        let resources = registry.list_resources_in_namespace(namespace);
        let mcp_resources: Vec<serde_json::Value> = resources
            .iter()
            .map(|r| {
                serde_json::json!({
                    "uri": r.location,
                    "name": r.name,
                    "description": r.description,
                    "mimeType": r.mime_type,
                })
            })
            .collect();

        let json_rpc_id = crate::response::extract_json_rpc_id(body);

        let response = serde_json::json!({
            "jsonrpc": "2.0",
            "id": json_rpc_id,
            "result": {
                "resources": mcp_resources,
            }
        });

        let response_body = Bytes::from(response.to_string());
        Ok(FilterAction::Reject(crate::response::json_response(response_body)))
    }
}
