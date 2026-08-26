use bytes::Bytes;
use http::StatusCode;
use praxis_filter::Rejection;
use wanaku_types::config::ENV;

pub const JSONRPC_INVALID_REQUEST: i32 = -32600;
pub const JSONRPC_INVALID_PARAMS: i32 = -32602;
pub const JSONRPC_INTERNAL_ERROR: i32 = -32603;

pub fn json_response(body: Bytes) -> Rejection {
    Rejection::status(StatusCode::OK.as_u16())
        .with_header("content-type", "application/json")
        .with_header("access-control-allow-origin", ENV.cors_origin.as_str())
        .with_body(body)
}

pub fn empty_accepted() -> Rejection {
    Rejection::status(StatusCode::ACCEPTED.as_u16())
        .with_header("access-control-allow-origin", ENV.cors_origin.as_str())
}

pub fn json_rpc_error(id: &serde_json::Value, code: i32, message: &str) -> praxis_filter::FilterAction {
    let response = serde_json::json!({
        "jsonrpc": "2.0",
        "id": id,
        "error": {
            "code": code,
            "message": message,
        }
    });
    let body = Bytes::from(response.to_string());
    praxis_filter::FilterAction::Reject(json_response(body))
}

pub fn extract_json_rpc_id(body: &Option<Bytes>) -> serde_json::Value {
    body.as_ref()
        .and_then(|b| serde_json::from_slice::<serde_json::Value>(b).ok())
        .and_then(|v| v.get("id").cloned())
        .unwrap_or(serde_json::Value::Null)
}

/// Reads the JSON-RPC request ID from its serialized metadata representation.
///
/// The `mcp.id` metadata is set by the `McpIdFilter` early in the filter chain
/// so that downstream filters can retrieve the id without re-parsing the body.
pub fn json_rpc_id_from_metadata(raw: Option<&str>) -> serde_json::Value {
    raw.and_then(|s| serde_json::from_str(s).ok())
        .unwrap_or(serde_json::Value::Null)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn extract_id_from_valid_body() {
        let body = Some(Bytes::from(r#"{"jsonrpc":"2.0","id":42,"method":"tools/list"}"#));
        assert_eq!(extract_json_rpc_id(&body), serde_json::Value::from(42));
    }

    #[test]
    fn extract_id_string_value() {
        let body = Some(Bytes::from(r#"{"id":"req-1"}"#));
        assert_eq!(extract_json_rpc_id(&body), serde_json::Value::from("req-1"));
    }

    #[test]
    fn extract_id_null_when_no_id_field() {
        let body = Some(Bytes::from(r#"{"jsonrpc":"2.0","method":"tools/list"}"#));
        assert_eq!(extract_json_rpc_id(&body), serde_json::Value::Null);
    }

    #[test]
    fn extract_id_null_when_body_is_none() {
        assert_eq!(extract_json_rpc_id(&None), serde_json::Value::Null);
    }

    #[test]
    fn extract_id_null_when_body_is_malformed() {
        let body = Some(Bytes::from("not json at all"));
        assert_eq!(extract_json_rpc_id(&body), serde_json::Value::Null);
    }

    #[test]
    fn extract_id_null_when_body_is_empty() {
        let body = Some(Bytes::new());
        assert_eq!(extract_json_rpc_id(&body), serde_json::Value::Null);
    }

    #[test]
    fn metadata_round_trip_numeric_id() {
        let id = serde_json::Value::from(42);
        let serialized = id.to_string();
        assert_eq!(json_rpc_id_from_metadata(Some(&serialized)), serde_json::Value::from(42));
    }

    #[test]
    fn metadata_round_trip_string_id() {
        let id = serde_json::Value::from("req-1");
        let serialized = id.to_string();
        assert_eq!(json_rpc_id_from_metadata(Some(&serialized)), serde_json::Value::from("req-1"));
    }

    #[test]
    fn metadata_round_trip_null_id() {
        let id = serde_json::Value::Null;
        let serialized = id.to_string();
        assert_eq!(json_rpc_id_from_metadata(Some(&serialized)), serde_json::Value::Null);
    }

    #[test]
    fn metadata_none_returns_null() {
        assert_eq!(json_rpc_id_from_metadata(None), serde_json::Value::Null);
    }

    #[test]
    fn metadata_malformed_returns_null() {
        assert_eq!(json_rpc_id_from_metadata(Some("not valid json {")), serde_json::Value::Null);
    }

    #[test]
    fn json_rpc_error_returns_reject() {
        let id = serde_json::Value::from(7);
        let action = json_rpc_error(&id, JSONRPC_INVALID_PARAMS, "bad params");
        assert!(matches!(action, praxis_filter::FilterAction::Reject(_)));
    }

    #[test]
    fn json_rpc_error_body_has_correct_structure() {
        let id = serde_json::Value::from(7);
        let action = json_rpc_error(&id, JSONRPC_INTERNAL_ERROR, "something broke");

        let body_bytes = match action {
            praxis_filter::FilterAction::Reject(r) => {
                assert_eq!(r.status, 200);
                r.body
            }
            _ => None,
        };

        assert!(body_bytes.is_some());
        if let Some(bytes) = body_bytes {
            let parsed: Result<serde_json::Value, _> = serde_json::from_slice(&bytes);
            assert!(parsed.is_ok());
            if let Ok(v) = parsed {
                assert_eq!(v["jsonrpc"], "2.0");
                assert_eq!(v["id"], 7);
                assert_eq!(v["error"]["code"], JSONRPC_INTERNAL_ERROR);
                assert_eq!(v["error"]["message"], "something broke");
            }
        }
    }

    #[test]
    fn json_rpc_error_with_null_id() {
        let id = serde_json::Value::Null;
        let action = json_rpc_error(&id, JSONRPC_INVALID_PARAMS, "missing id");

        let body_bytes = match action {
            praxis_filter::FilterAction::Reject(r) => r.body,
            _ => None,
        };

        assert!(body_bytes.is_some());
        if let Some(bytes) = body_bytes {
            let parsed: Result<serde_json::Value, _> = serde_json::from_slice(&bytes);
            assert!(parsed.is_ok());
            if let Ok(v) = parsed {
                assert!(v["id"].is_null());
                assert_eq!(v["error"]["code"], JSONRPC_INVALID_PARAMS);
            }
        }
    }

    #[test]
    fn json_response_has_cors_and_content_type() {
        let r = json_response(Bytes::from("{}"));
        assert_eq!(r.status, 200);
        assert!(r.headers.iter().any(|(k, v)| k == "content-type" && v == "application/json"));
        assert!(r.headers.iter().any(|(k, v)| k == "access-control-allow-origin" && v == ENV.cors_origin.as_str()));
    }

    #[test]
    fn empty_accepted_status() {
        let r = empty_accepted();
        assert_eq!(r.status, 202);
        assert!(r.body.is_none());
    }
}
