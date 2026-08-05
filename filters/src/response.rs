use bytes::Bytes;
use praxis_filter::Rejection;

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

pub fn extract_json_rpc_id(body: &Option<Bytes>) -> serde_json::Value {
    body.as_ref()
        .and_then(|b| serde_json::from_slice::<serde_json::Value>(b).ok())
        .and_then(|v| v.get("id").cloned())
        .unwrap_or(serde_json::Value::Null)
}
