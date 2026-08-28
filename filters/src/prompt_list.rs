use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::trace;
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::registry::PromptRegistry;

crate::body_filter_boilerplate!(PromptListFilter, "wanaku_prompt_list");

impl PromptListFilter {
    #[expect(clippy::too_many_lines, reason = "MCP protocol handler with JSON-RPC response construction")]
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        _body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let Some(method) = ctx.get_metadata(crate::MCP_METHOD_KEY) else {
            return Ok(FilterAction::Continue);
        };

        if method != crate::PROMPTS_LIST {
            return Ok(FilterAction::Continue);
        }

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_types::registry::DEFAULT_NAMESPACE);

        trace!(namespace = %namespace, "handling MCP prompts/list request");

        let json_rpc_id = crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));

        let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() else {
            tracing::error!("InMemoryRegistry not found in request extensions");
            return Ok(crate::response::json_rpc_error(
                &json_rpc_id,
                crate::response::JSONRPC_INTERNAL_ERROR,
                "internal error: registry unavailable",
            ));
        };

        let prompts = registry.list_prompts_in_namespace(namespace);
        let mcp_prompts: Vec<serde_json::Value> = prompts
            .iter()
            .map(|p| {
                let args: Vec<serde_json::Value> = p
                    .arguments
                    .iter()
                    .map(|a| {
                        serde_json::json!({
                            "name": a.name,
                            "description": a.description,
                            "required": a.required,
                        })
                    })
                    .collect();

                serde_json::json!({
                    "name": p.name,
                    "description": p.description,
                    "arguments": args,
                })
            })
            .collect();

        let response = serde_json::json!({
            "jsonrpc": "2.0",
            "id": json_rpc_id,
            "result": {
                "prompts": mcp_prompts,
            }
        });

        let response_body = Bytes::from(response.to_string());
        Ok(FilterAction::Reject(crate::response::json_response(response_body)))
    }
}
