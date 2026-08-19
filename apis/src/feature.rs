use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension};

/// Bundles the HTTP request components dispatched to [`Feature::handle_route`].
///
/// Built once per management API request and shared across all features
/// during route dispatch. Fields map directly to the incoming Pingora
/// `ServerSession` request.
#[derive(Debug)]
pub struct HttpContext<'a> {
    /// HTTP method (`"GET"`, `"POST"`, …).
    pub method: &'a str,
    /// Request path, e.g. `"/api/v1/chat/completions"`.
    pub path: &'a str,
    /// Raw query string, if present.
    pub query: Option<&'a str>,
    /// Request body (read once for POST/PUT/PATCH, `None` otherwise).
    pub body: Option<&'a str>,
    /// HTTP headers forwarded from the client.
    pub headers: &'a http::HeaderMap,
}

impl<'a> HttpContext<'a> {
    /// Creates a new HTTP context from the request components.
    #[must_use]
    pub fn new(
        method: &'a str,
        path: &'a str,
        query: Option<&'a str>,
        body: Option<&'a str>,
        headers: &'a http::HeaderMap,
    ) -> Self {
        Self { method, path, query, body, headers }
    }
}

#[async_trait::async_trait]
pub trait Feature: Send + Sync {
    fn name(&self) -> &'static str;

    fn register_filters(&self, registry: &mut FilterRegistry);

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>>;

    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>>;

    fn load_yaml_config(&self, root: &serde_yaml::Value);

    fn load_env_config(&self);
}
