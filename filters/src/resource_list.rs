use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::trace;
use wanaku_apis::registry::{InMemoryRegistry, ResourceRegistry};

crate::body_filter_boilerplate!(ResourceListFilter, "wanaku_resource_list");

impl ResourceListFilter {
    #[expect(clippy::too_many_lines, reason = "MCP protocol handler with JSON-RPC response construction")]
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        _body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let Some(method) = ctx.get_metadata(crate::MCP_METHOD_KEY) else {
            return Ok(FilterAction::Continue);
        };

        let is_list = method == "resources/list";
        let is_template_list = method == "resources/templates/list";
        if !is_list && !is_template_list {
            return Ok(FilterAction::Continue);
        }

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_apis::registry::DEFAULT_NAMESPACE);

        trace!(namespace = %namespace, "handling MCP resources/list request");

        let json_rpc_id = crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));

        let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() else {
            tracing::error!("InMemoryRegistry not found in request extensions");
            return Ok(crate::response::json_rpc_error(
                &json_rpc_id,
                crate::response::JSONRPC_INTERNAL_ERROR,
                "internal error: registry unavailable",
            ));
        };

        let all_resources = registry.list_resources_in_namespace(namespace);

        let response = if is_template_list {
            let templates: Vec<serde_json::Value> = all_resources
                .iter()
                .filter(|r| r.is_template())
                .map(|r| {
                    serde_json::json!({
                        "uriTemplate": r.location,
                        "name": r.name,
                        "description": r.description,
                        "mimeType": r.mime_type,
                    })
                })
                .collect();

            serde_json::json!({
                "jsonrpc": "2.0",
                "id": json_rpc_id,
                "result": { "resourceTemplates": templates }
            })
        } else {
            let resources: Vec<serde_json::Value> = all_resources
                .iter()
                .filter(|r| !r.is_template())
                .map(|r| {
                    serde_json::json!({
                        "uri": r.location,
                        "name": r.name,
                        "description": r.description,
                        "mimeType": r.mime_type,
                    })
                })
                .collect();

            serde_json::json!({
                "jsonrpc": "2.0",
                "id": json_rpc_id,
                "result": { "resources": resources }
            })
        };

        let response_body = Bytes::from(response.to_string());
        Ok(FilterAction::Reject(crate::response::json_response(response_body)))
    }
}
