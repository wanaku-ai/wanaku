use http::Response;

#[expect(clippy::expect_used, reason = "valid static response")]
pub fn json_err(status: u16, message: &str) -> Response<Vec<u8>> {
    let wrapper = serde_json::json!({
        "data": null,
        "error": message,
    });
    let body = serde_json::to_vec(&wrapper).unwrap_or_default();
    Response::builder()
        .status(status)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid json error response")
}
