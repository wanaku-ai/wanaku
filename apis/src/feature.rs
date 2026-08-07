use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension};

#[async_trait::async_trait]
pub trait Feature: Send + Sync {
    fn name(&self) -> &'static str;

    fn register_filters(&self, registry: &mut FilterRegistry);

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>>;

    async fn handle_route(
        &self,
        method: &str,
        path: &str,
        query: Option<&str>,
        body: Option<&str>,
    ) -> Option<Response<Vec<u8>>>;

    fn load_yaml_config(&self, root: &serde_yaml::Value);

    fn load_env_config(&self);
}
