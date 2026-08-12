use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::{trace, warn};

crate::body_filter_boilerplate!(ResourceReadFilter, "wanaku_resource_read");

struct ParsedBody {
    id: serde_json::Value,
    uri: Option<String>,
}

fn parse_body(body: &Option<Bytes>) -> ParsedBody {
    let Some(body_bytes) = body else {
        return ParsedBody {
            id: serde_json::Value::Null,
            uri: None,
        };
    };

    let Ok(parsed) = serde_json::from_slice::<serde_json::Value>(body_bytes) else {
        return ParsedBody {
            id: serde_json::Value::Null,
            uri: None,
        };
    };

    let id = parsed
        .get("id")
        .cloned()
        .unwrap_or(serde_json::Value::Null);

    let uri = parsed
        .get("params")
        .and_then(|p| p.get("uri"))
        .and_then(|u| u.as_str())
        .map(str::to_owned);

    ParsedBody { id, uri }
}

impl ResourceReadFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let method = match ctx.get_metadata(crate::MCP_METHOD_KEY) {
            Some(m) => m,
            None => return Ok(FilterAction::Continue),
        };

        if method != "resources/read" {
            return Ok(FilterAction::Continue);
        }

        let parsed = parse_body(body);

        let resource_uri = match &parsed.uri {
            Some(u) => u.clone(),
            None => {
                return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INVALID_PARAMS, "missing uri in resources/read"));
            }
        };

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);

        trace!(uri = %resource_uri, namespace = %namespace, "handling MCP resources/read request");

        warn!(uri = %resource_uri, "resource read is not supported — no resource provider backend configured");
        Ok(crate::response::json_rpc_error(
            &parsed.id,
            crate::response::JSONRPC_INTERNAL_ERROR,
            "resource read is not supported in this configuration",
        ))
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
        let parsed = parse_body(&body);
        assert_eq!(parsed.id, serde_json::Value::from(1));
        assert_eq!(parsed.uri.as_deref(), Some("file:///data/report.csv"));
    }

    #[test]
    fn parse_body_missing_uri() {
        let body = Some(Bytes::from(
            r#"{"id":2,"params":{}}"#,
        ));
        let parsed = parse_body(&body);
        assert_eq!(parsed.id, serde_json::Value::from(2));
        assert!(parsed.uri.is_none());
    }

    #[test]
    fn parse_body_missing_params() {
        let body = Some(Bytes::from(r#"{"id":3}"#));
        let parsed = parse_body(&body);
        assert_eq!(parsed.id, serde_json::Value::from(3));
        assert!(parsed.uri.is_none());
    }

    #[test]
    fn parse_body_none() {
        let parsed = parse_body(&None);
        assert!(parsed.id.is_null());
        assert!(parsed.uri.is_none());
    }

    #[test]
    fn parse_body_malformed_json() {
        let body = Some(Bytes::from("{broken"));
        let parsed = parse_body(&body);
        assert!(parsed.id.is_null());
        assert!(parsed.uri.is_none());
    }

    #[test]
    fn parse_body_empty_bytes() {
        let body = Some(Bytes::new());
        let parsed = parse_body(&body);
        assert!(parsed.id.is_null());
        assert!(parsed.uri.is_none());
    }

    #[test]
    fn parse_body_no_id_field() {
        let body = Some(Bytes::from(
            r#"{"params":{"uri":"s3://bucket/key"}}"#,
        ));
        let parsed = parse_body(&body);
        assert!(parsed.id.is_null());
        assert_eq!(parsed.uri.as_deref(), Some("s3://bucket/key"));
    }

    #[test]
    fn parse_body_uri_is_not_string() {
        let body = Some(Bytes::from(
            r#"{"id":4,"params":{"uri":123}}"#,
        ));
        let parsed = parse_body(&body);
        assert_eq!(parsed.id, serde_json::Value::from(4));
        assert!(parsed.uri.is_none());
    }
}
