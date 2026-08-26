use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};

pub use wanaku_types::metadata::NAMESPACE_METADATA_KEY;

crate::body_filter_boilerplate!(NamespaceFilter, "wanaku_namespace");

fn extract_namespace(path: &str) -> Option<&str> {
    let trimmed = path
        .strip_prefix('/')
        .unwrap_or(path)
        .trim_end_matches('/');

    // /{namespace}/mcp — the only valid format
    if let Some(ns) = trimmed.strip_suffix("/mcp")
        && !ns.is_empty() && !ns.contains('/') {
            return Some(ns);
        }

    None
}

impl NamespaceFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        _body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let path = ctx.request.uri.path();

        match extract_namespace(path) {
            Some(namespace) => {
                tracing::debug!(namespace = %namespace, path = %path, "resolved namespace from path");
                ctx.set_metadata(NAMESPACE_METADATA_KEY, namespace);
                Ok(FilterAction::Continue)
            }
            None => {
                if ctx.get_metadata(crate::MCP_METHOD_KEY).is_some() {
                    tracing::warn!(path = %path, "rejected MCP request on invalid path");
                    let id = crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));
                    Ok(crate::response::json_rpc_error(
                        &id,
                        crate::response::JSONRPC_INVALID_REQUEST,
                        "invalid MCP endpoint path: use /{namespace}/mcp",
                    ))
                } else {
                    Ok(FilterAction::Continue)
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bare_mcp_is_none() {
        assert_eq!(extract_namespace("/mcp"), None);
    }

    #[test]
    fn default_namespace_explicit() {
        assert_eq!(extract_namespace("/default/mcp"), Some("default"));
    }

    #[test]
    fn namespace_from_path() {
        assert_eq!(extract_namespace("/finance/mcp"), Some("finance"));
    }

    #[test]
    fn another_namespace() {
        assert_eq!(extract_namespace("/engineering/mcp"), Some("engineering"));
    }

    #[test]
    fn nested_path_is_none() {
        assert_eq!(extract_namespace("/a/b/mcp"), None);
    }

    #[test]
    fn no_mcp_suffix_is_none() {
        assert_eq!(extract_namespace("/finance/other"), None);
    }

    #[test]
    fn empty_path_is_none() {
        assert_eq!(extract_namespace("/"), None);
    }

    #[test]
    fn mcp_prefix_namespace_is_none() {
        assert_eq!(extract_namespace("/mcp/test-ns"), None);
    }

    #[test]
    fn mcp_prefix_default_is_none() {
        assert_eq!(extract_namespace("/mcp/default"), None);
    }

    #[test]
    fn mcp_prefix_nested_is_none() {
        assert_eq!(extract_namespace("/mcp/a/b"), None);
    }

    #[test]
    fn trailing_slash_namespace_mcp() {
        assert_eq!(extract_namespace("/test-ns2/mcp/"), Some("test-ns2"));
    }

    #[test]
    fn bare_namespace_is_none() {
        assert_eq!(extract_namespace("/finance"), None);
    }

    #[test]
    fn bare_namespace_with_trailing_slash_is_none() {
        assert_eq!(extract_namespace("/finance/"), None);
    }
}
