use bytes::Bytes;

/// Method-specific fields parsed from a JSON-RPC request's `params` object.
///
/// The request `id` is deliberately not part of this struct: it is read once
/// from `mcp.id` metadata (set by `McpIdFilter`) rather than re-parsed here.
#[derive(Default)]
pub(crate) struct JsonRpcParams {
    pub(crate) name: Option<String>,
    pub(crate) uri: Option<String>,
    pub(crate) arguments: serde_json::Map<String, serde_json::Value>,
}

impl JsonRpcParams {
    pub(crate) fn parse(body: &Option<Bytes>) -> Self {
        let Some(body_bytes) = body else {
            return Self::default();
        };

        let Ok(value) = serde_json::from_slice::<serde_json::Value>(body_bytes) else {
            return Self::default();
        };

        let Some(params) = value.get("params") else {
            return Self::default();
        };

        let name = params.get("name").and_then(serde_json::Value::as_str).map(str::to_owned);
        let uri = params.get("uri").and_then(serde_json::Value::as_str).map(str::to_owned);
        let arguments = params
            .get("arguments")
            .and_then(serde_json::Value::as_object)
            .cloned()
            .unwrap_or_default();

        Self { name, uri, arguments }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_all_fields_present() {
        let body = Some(Bytes::from(
            r#"{"jsonrpc":"2.0","id":1,"method":"prompts/get","params":{"name":"summarize","uri":"file:///x","arguments":{"topic":"rust"}}}"#,
        ));
        let params = JsonRpcParams::parse(&body);
        assert_eq!(params.name.as_deref(), Some("summarize"));
        assert_eq!(params.uri.as_deref(), Some("file:///x"));
        assert_eq!(params.arguments.get("topic"), Some(&serde_json::json!("rust")));
    }

    #[test]
    fn parse_arguments_with_non_string_values() {
        let body = Some(Bytes::from(
            r#"{"params":{"arguments":{"count":42,"flag":true,"nested":{"a":1}}}}"#,
        ));
        let params = JsonRpcParams::parse(&body);
        assert_eq!(params.arguments.get("count"), Some(&serde_json::json!(42)));
        assert_eq!(params.arguments.get("flag"), Some(&serde_json::json!(true)));
        assert_eq!(params.arguments.get("nested"), Some(&serde_json::json!({"a": 1})));
    }

    #[test]
    fn parse_missing_params() {
        let body = Some(Bytes::from(r#"{"id":1}"#));
        let params = JsonRpcParams::parse(&body);
        assert!(params.name.is_none());
        assert!(params.uri.is_none());
        assert!(params.arguments.is_empty());
    }

    #[test]
    fn parse_none_body() {
        let params = JsonRpcParams::parse(&None);
        assert!(params.name.is_none());
        assert!(params.uri.is_none());
        assert!(params.arguments.is_empty());
    }

    #[test]
    fn parse_malformed_json() {
        let body = Some(Bytes::from("not json"));
        let params = JsonRpcParams::parse(&body);
        assert!(params.name.is_none());
        assert!(params.uri.is_none());
        assert!(params.arguments.is_empty());
    }

    #[test]
    fn parse_empty_bytes() {
        let body = Some(Bytes::new());
        let params = JsonRpcParams::parse(&body);
        assert!(params.name.is_none());
        assert!(params.uri.is_none());
        assert!(params.arguments.is_empty());
    }

    #[test]
    fn parse_arguments_is_not_object() {
        let body = Some(Bytes::from(r#"{"params":{"arguments":"not-an-object"}}"#));
        let params = JsonRpcParams::parse(&body);
        assert!(params.arguments.is_empty());
    }

    #[test]
    fn parse_uri_is_not_string() {
        let body = Some(Bytes::from(r#"{"params":{"uri":123}}"#));
        let params = JsonRpcParams::parse(&body);
        assert!(params.uri.is_none());
    }

    #[test]
    fn parse_name_is_not_string() {
        let body = Some(Bytes::from(r#"{"params":{"name":99}}"#));
        let params = JsonRpcParams::parse(&body);
        assert!(params.name.is_none());
    }
}
