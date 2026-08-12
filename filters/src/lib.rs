
pub const MCP_METHOD_KEY: &str = "mcp.method";
pub const MCP_NAME_KEY: &str = "mcp.name";

#[macro_export]
macro_rules! body_filter_boilerplate {
    ($struct_name:ident, $filter_name:literal) => {
        pub struct $struct_name {
            max_body_bytes: usize,
        }

        impl $struct_name {
            pub fn from_config(
                config: &serde_yaml::Value,
            ) -> Result<Box<dyn praxis_filter::HttpFilter>, praxis_filter::FilterError> {
                let max_body_bytes = config
                    .get("max_body_bytes")
                    .and_then(serde_yaml::Value::as_u64)
                    .unwrap_or(1_048_576) as usize;

                Ok(Box::new(Self { max_body_bytes }))
            }
        }

        #[async_trait::async_trait]
        impl praxis_filter::HttpFilter for $struct_name {
            fn name(&self) -> &'static str {
                $filter_name
            }

            fn request_body_access(&self) -> praxis_filter::BodyAccess {
                praxis_filter::BodyAccess::ReadOnly
            }

            fn request_body_mode(&self) -> praxis_filter::BodyMode {
                praxis_filter::BodyMode::StreamBuffer {
                    max_bytes: Some(self.max_body_bytes),
                }
            }

            async fn on_request(
                &self,
                _ctx: &mut praxis_filter::HttpFilterContext<'_>,
            ) -> Result<praxis_filter::FilterAction, praxis_filter::FilterError> {
                Ok(praxis_filter::FilterAction::Continue)
            }

            async fn on_request_body(
                &self,
                ctx: &mut praxis_filter::HttpFilterContext<'_>,
                body: &mut Option<bytes::Bytes>,
                end_of_stream: bool,
            ) -> Result<praxis_filter::FilterAction, praxis_filter::FilterError> {
                if !end_of_stream {
                    return Ok(praxis_filter::FilterAction::Continue);
                }
                self.handle_body(ctx, body).await
            }
        }
    };
}

pub mod mcp_init;
pub mod namespace;
pub mod prompt_get;
pub mod prompt_list;
pub mod resource_list;
pub mod resource_read;
pub mod response;
pub mod tool_call;
pub mod tool_list;

pub use mcp_init::McpInitFilter;
pub use namespace::NamespaceFilter;
pub use prompt_get::PromptGetFilter;
pub use prompt_list::PromptListFilter;
pub use resource_list::ResourceListFilter;
pub use resource_read::ResourceReadFilter;
pub use tool_call::ToolCallFilter;
pub use tool_list::ToolListFilter;
