use async_trait::async_trait;
use bytes::Bytes;
use praxis_filter::{
    BodyAccess, BodyMode, FilterAction, FilterError, HttpFilter, HttpFilterContext,
};
use tracing::trace;
use wanaku_praxis_apis::registry::{InMemoryRegistry, ResourceRegistry};

pub struct ResourceListFilter {
    max_body_bytes: usize,
}

impl ResourceListFilter {
    pub fn from_config(config: &serde_yaml::Value) -> Result<Box<dyn HttpFilter>, FilterError> {
        let max_body_bytes = config
            .get("max_body_bytes")
            .and_then(serde_yaml::Value::as_u64)
            .unwrap_or(1_048_576) as usize;

        Ok(Box::new(Self { max_body_bytes }))
    }
}

#[async_trait]
impl HttpFilter for ResourceListFilter {
    fn name(&self) -> &'static str {
        "wanaku_resource_list"
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
                return Ok(FilterAction::Continue);
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

