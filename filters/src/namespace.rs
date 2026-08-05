use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use wanaku_praxis_apis::registry::DEFAULT_NAMESPACE;

pub const NAMESPACE_METADATA_KEY: &str = "wanaku.namespace";

crate::body_filter_boilerplate!(NamespaceFilter, "wanaku_namespace");

fn extract_namespace(path: &str) -> &str {
    let trimmed = path
        .strip_prefix('/')
        .unwrap_or(path)
        .trim_end_matches('/');

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

impl NamespaceFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        _body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
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

    #[test]
    fn trailing_slash_namespace_mcp() {
        assert_eq!(extract_namespace("/test-ns2/mcp/"), "test-ns2");
    }

    #[test]
    fn trailing_slash_mcp_namespace() {
        assert_eq!(extract_namespace("/mcp/test-ns2/"), "test-ns2");
    }
}
