use async_trait::async_trait;
use bytes::Bytes;
use praxis_filter::{
    BodyAccess, BodyMode, FilterAction, FilterError, HttpFilter, HttpFilterContext,
};
use wanaku_praxis_apis::registry::DEFAULT_NAMESPACE;

pub const NAMESPACE_METADATA_KEY: &str = "wanaku.namespace";

pub struct NamespaceFilter {
    max_body_bytes: usize,
}

impl NamespaceFilter {
    pub fn from_config(config: &serde_yaml::Value) -> Result<Box<dyn HttpFilter>, FilterError> {
        let max_body_bytes = config
            .get("max_body_bytes")
            .and_then(serde_yaml::Value::as_u64)
            .unwrap_or(1_048_576) as usize;

        Ok(Box::new(Self { max_body_bytes }))
    }
}

fn extract_namespace(path: &str) -> &str {
    let trimmed = path.strip_prefix('/').unwrap_or(path);

    if trimmed == "mcp" || trimmed.is_empty() {
        return DEFAULT_NAMESPACE;
    }

    // /{namespace}/mcp
    if let Some(ns) = trimmed.strip_suffix("/mcp") {
        if !ns.is_empty() && !ns.contains('/') {
            return ns;
        }
    }

    // /mcp/{namespace}
    if let Some(ns) = trimmed.strip_prefix("mcp/") {
        if !ns.is_empty() && !ns.contains('/') {
            return ns;
        }
    }

    DEFAULT_NAMESPACE
}

#[async_trait]
impl HttpFilter for NamespaceFilter {
    fn name(&self) -> &'static str {
        "wanaku_namespace"
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
        _body: &mut Option<Bytes>,
        end_of_stream: bool,
    ) -> Result<FilterAction, FilterError> {
        if !end_of_stream {
            return Ok(FilterAction::Continue);
        }

        let path = ctx.request.uri.path();
        let namespace = extract_namespace(path);

        tracing::debug!(namespace = %namespace, path = %path, "resolved namespace from path");
        ctx.set_metadata(NAMESPACE_METADATA_KEY, namespace);

        Ok(FilterAction::Continue)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn root_mcp_is_default() {
        assert_eq!(extract_namespace("/mcp"), "default");
    }

    #[test]
    fn namespace_from_path() {
        assert_eq!(extract_namespace("/finance/mcp"), "finance");
    }

    #[test]
    fn another_namespace() {
        assert_eq!(extract_namespace("/engineering/mcp"), "engineering");
    }

    #[test]
    fn nested_path_is_default() {
        assert_eq!(extract_namespace("/a/b/mcp"), "default");
    }

    #[test]
    fn no_mcp_suffix_is_default() {
        assert_eq!(extract_namespace("/finance/other"), "default");
    }

    #[test]
    fn empty_path_is_default() {
        assert_eq!(extract_namespace("/"), "default");
    }

    #[test]
    fn mcp_prefix_namespace() {
        assert_eq!(extract_namespace("/mcp/test-ns"), "test-ns");
    }

    #[test]
    fn mcp_prefix_another() {
        assert_eq!(extract_namespace("/mcp/finance"), "finance");
    }

    #[test]
    fn mcp_prefix_nested_is_default() {
        assert_eq!(extract_namespace("/mcp/a/b"), "default");
    }
}
