use bytes::Bytes;
use praxis_filter::Rejection;

pub const JSONRPC_INVALID_PARAMS: i32 = -32602;
pub const JSONRPC_INTERNAL_ERROR: i32 = -32603;

pub fn json_response(body: Bytes) -> Rejection {
    Rejection::status(200)
        .with_header("content-type", "application/json")
        .with_header("access-control-allow-origin", "*")
        .with_body(body)
}

pub fn empty_accepted() -> Rejection {
    Rejection::status(202)
        .with_header("access-control-allow-origin", "*")
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
