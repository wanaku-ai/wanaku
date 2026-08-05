use async_trait::async_trait;
use bytes::Bytes;
use praxis_filter::{
    BodyAccess, BodyMode, FilterAction, FilterError, HttpFilter, HttpFilterContext,
};
use tracing::trace;
use wanaku_praxis_apis::registry::{InMemoryRegistry, PromptRegistry};

pub struct PromptListFilter {
    max_body_bytes: usize,
}

impl PromptListFilter {
    pub fn from_config(config: &serde_yaml::Value) -> Result<Box<dyn HttpFilter>, FilterError> {
        let max_body_bytes = config
            .get("max_body_bytes")
            .and_then(serde_yaml::Value::as_u64)
            .unwrap_or(1_048_576) as usize;

        Ok(Box::new(Self { max_body_bytes }))
    }
}

#[async_trait]
impl HttpFilter for PromptListFilter {
    fn name(&self) -> &'static str {
        "wanaku_prompt_list"
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

        if method != "prompts/list" {
            return Ok(FilterAction::Continue);
        }

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);

        trace!(namespace = %namespace, "handling MCP prompts/list request");

        let registry = match ctx.extensions.get::<InMemoryRegistry>() {
            Some(r) => r,
            None => {
                tracing::error!("InMemoryRegistry not found in request extensions");
                return Ok(FilterAction::Continue);
            }
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

        let json_rpc_id = crate::response::extract_json_rpc_id(body);

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

