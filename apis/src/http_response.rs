use http::Response;

use crate::config::ENV;

#[expect(clippy::expect_used, reason = "valid static response")]
pub fn json_ok(data: &serde_json::Value) -> Response<Vec<u8>> {
    let wrapper = serde_json::json!({"data": data, "error": null});
    let body = serde_json::to_vec(&wrapper).unwrap_or_default();
    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .header("Access-Control-Allow-Origin", ENV.cors_origin.as_str())
        .body(body)
        .expect("valid json response")
}

#[expect(clippy::expect_used, reason = "valid static response")]
pub fn json_err(status: u16, message: &str) -> Response<Vec<u8>> {
    let wrapper = serde_json::json!({"data": null, "error": message});
    let body = serde_json::to_vec(&wrapper).unwrap_or_default();
    Response::builder()
        .status(status)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .header("Access-Control-Allow-Origin", ENV.cors_origin.as_str())
        .body(body)
        .expect("valid json error response")
}
