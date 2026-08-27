use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::{trace, warn};
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::registry::ResourceRegistry;

crate::body_filter_boilerplate!(ResourceReadFilter, "wanaku_resource_read");

#[expect(clippy::too_many_lines, reason = "URI template matching with segment iteration")]
fn matches_uri_template(template: &str, uri: &str) -> bool {
    let mut parts = Vec::new();
    let mut rest = template;
    while let Some(start) = rest.find('{') {
        parts.push(&rest[..start]);
        match rest[start..].find('}') {
            Some(end) => rest = &rest[start + end + 1..],
            None => return false,
        }
    }
    parts.push(rest);

    if parts.len() == 1 {
        return template == uri;
    }

    let mut pos = 0;
    for (i, part) in parts.iter().enumerate() {
        if part.is_empty() {
            continue;
        }
        match uri[pos..].find(part) {
            Some(found) => {
                if i == 0 && found != 0 {
                    return false;
                }
                pos += found + part.len();
            }
            None => return false,
        }
    }

    if template.ends_with('}') {
        return pos <= uri.len();
    }

    pos == uri.len()
}

/// Resolve the registry resource used for one exact requested URI.
///
/// The returned entry can use either an exact location or a URI template.
#[must_use]
pub fn find_resource_in_namespace(
    registry: &InMemoryRegistry,
    namespace: &str,
    uri: &str,
) -> Option<wanaku_types::registry::ResourceEntry> {
    registry
        .list_resources_in_namespace(namespace)
        .into_iter()
        .find(|resource| {
            (!resource.is_template() && resource.location == uri)
                || (resource.is_template() && matches_uri_template(&resource.location, uri))
        })
}

struct ParsedBody {
    id: serde_json::Value,
    uri: Option<String>,
}

fn parse_body(body: &Option<Bytes>, json_rpc_id: serde_json::Value) -> ParsedBody {
    let uri = crate::json_rpc::JsonRpcParams::parse(body).uri;
    ParsedBody { id: json_rpc_id, uri }
}

impl ResourceReadFilter {
    #[expect(clippy::too_many_lines, reason = "MCP protocol handler with JSON-RPC response construction")]
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let Some(method) = ctx.get_metadata(crate::MCP_METHOD_KEY) else {
            return Ok(FilterAction::Continue);
        };

        if method != "resources/read" {
            return Ok(FilterAction::Continue);
        }

        let json_rpc_id = crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));
        let parsed = parse_body(body, json_rpc_id);

        let Some(resource_uri) = parsed.uri.as_deref() else {
            return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INVALID_PARAMS, "missing uri in resources/read"));
        };

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_types::registry::DEFAULT_NAMESPACE);

        trace!(uri = %resource_uri, namespace = %namespace, "handling MCP resources/read request");

        let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() else {
            tracing::error!("InMemoryRegistry not found in request extensions");
            return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INTERNAL_ERROR, "internal error: registry unavailable"));
        };

        let Some(resource) = find_resource_in_namespace(registry, namespace, resource_uri) else {
            warn!(uri = %resource_uri, namespace = %namespace, "resource not found in registry");
            return Ok(crate::response::json_rpc_error(
                &parsed.id,
                crate::response::JSONRPC_INVALID_PARAMS,
                &format!("resource not found: {resource_uri}"),
            ));
        };

        if resource.is_mcp_forward() {
            return self.handle_forwarded_read(&resource, resource_uri, &parsed).await;
        }

        warn!(uri = %resource_uri, resource_type = %resource.type_, "unsupported resource type — only MCP-forwarded resources are supported");
        Ok(crate::response::json_rpc_error(
            &parsed.id,
            crate::response::JSONRPC_INTERNAL_ERROR,
            &format!("unsupported resource type '{}': only MCP-forwarded resources are supported", resource.type_),
        ))
    }

    async fn handle_forwarded_read(
        &self,
        resource: &wanaku_types::registry::ResourceEntry,
        resource_uri: &str,
        parsed: &ParsedBody,
    ) -> Result<FilterAction, FilterError> {
        let Some(forward_address) = resource.forward_address() else {
            warn!(uri = %resource_uri, "forwarded resource missing forward address label");
            return Ok(crate::response::json_rpc_error(
                &parsed.id,
                crate::response::JSONRPC_INTERNAL_ERROR,
                "forwarded resource has no upstream address configured",
            ));
        };

        trace!(uri = %resource_uri, forward = %forward_address, "forwarding resources/read to remote MCP server");

        match wanaku_infra::mcp_client::read_resource(forward_address, resource_uri).await {
            Ok(contents) => {
                let response = serde_json::json!({
                    "jsonrpc": "2.0",
                    "id": parsed.id,
                    "result": {"contents": contents}
                });

                let response_body = Bytes::from(response.to_string());
                Ok(FilterAction::Reject(crate::response::json_response(response_body)))
            }
            Err(e) => {
                warn!(uri = %resource_uri, error = %e, "MCP forward resource read failed");
                Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("forwarded resource read failed: {e}"),
                ))
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_body_valid_with_uri() {
        let body = Some(Bytes::from(
            r#"{"jsonrpc":"2.0","id":1,"method":"resources/read","params":{"uri":"file:///data/report.csv"}}"#,
        ));
        let parsed = parse_body(&body, serde_json::Value::from(1));
        assert_eq!(parsed.id, serde_json::Value::from(1));
        assert_eq!(parsed.uri.as_deref(), Some("file:///data/report.csv"));
    }

    #[test]
    fn parse_body_none() {
        let parsed = parse_body(&None, serde_json::Value::Null);
        assert!(parsed.id.is_null());
        assert!(parsed.uri.is_none());
    }

    #[test]
    fn parse_body_id_comes_from_parameter_not_body() {
        let body = Some(Bytes::from(
            r#"{"jsonrpc":"2.0","id":999,"params":{"uri":"file:///x"}}"#,
        ));
        let parsed = parse_body(&body, serde_json::Value::from(1));
        assert_eq!(parsed.id, serde_json::Value::from(1));
    }

    #[test]
    fn template_matches_single_param() {
        assert!(matches_uri_template("logs://{server_id}/syslog", "logs://web-01/syslog"));
    }

    #[test]
    fn template_matches_multiple_params() {
        assert!(matches_uri_template("logs://{server}/{log_type}", "logs://web-01/syslog"));
    }

    #[test]
    fn template_no_match_wrong_prefix() {
        assert!(!matches_uri_template("logs://{server_id}/syslog", "files://web-01/syslog"));
    }

    #[test]
    fn template_no_match_wrong_suffix() {
        assert!(!matches_uri_template("logs://{server_id}/syslog", "logs://web-01/access"));
    }

    #[test]
    fn template_matches_param_at_end() {
        assert!(matches_uri_template("file://{path}", "file:///data/report.csv"));
    }

    #[test]
    fn template_exact_match_no_params() {
        assert!(matches_uri_template("file:///fixed.txt", "file:///fixed.txt"));
        assert!(!matches_uri_template("file:///fixed.txt", "file:///other.txt"));
    }

    #[test]
    fn template_malformed_unclosed_brace() {
        assert!(!matches_uri_template("logs://{server/syslog", "logs://web-01/syslog"));
    }
}
