use http::Response;
use pingora_core::protocols::http::ServerSession;
use tracing::warn;

pub(super) const MAX_BODY_BYTES: usize = 1_048_576;

#[expect(clippy::expect_used, reason = "valid static response")]
pub(super) fn redirect_response(location: &str) -> Response<Vec<u8>> {
    Response::builder()
        .status(301)
        .header("Location", location)
        .body(Vec::new())
        .expect("valid redirect")
}

#[expect(clippy::expect_used, reason = "valid static response")]
pub(super) fn raw_json_response(body: Vec<u8>) -> Response<Vec<u8>> {
    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .header("Access-Control-Allow-Origin", wanaku_praxis_apis::config::ENV.cors_origin.as_str())
        .body(body)
        .expect("valid json response")
}

#[expect(clippy::expect_used, reason = "valid static response")]
pub(super) fn json_ok(data: &serde_json::Value) -> Response<Vec<u8>> {
    let wrapper = serde_json::json!({
        "data": data,
        "error": null,
    });
    let body = serde_json::to_vec(&wrapper).unwrap_or_default();
    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .header("Access-Control-Allow-Origin", wanaku_praxis_apis::config::ENV.cors_origin.as_str())
        .body(body)
        .expect("valid json response")
}

pub(super) fn json_err(status: u16, message: &str) -> Response<Vec<u8>> {
    crate::http_response::json_err(status, message)
}

pub(super) async fn read_body(session: &mut ServerSession) -> Result<String, Response<Vec<u8>>> {
    let mut buf = Vec::new();
    loop {
        match session.read_request_body().await {
            Ok(Some(chunk)) => {
                if buf.len() + chunk.len() > MAX_BODY_BYTES {
                    warn!(limit = MAX_BODY_BYTES, "management request body exceeded size limit");
                    return Err(json_err(413, "request body too large"));
                }
                buf.extend_from_slice(&chunk);
            }
            Ok(None) => break,
            Err(e) => {
                warn!(error = %e, "management request body read failed");
                return Err(json_err(502, "request body read failed"));
            }
        }
    }
    String::from_utf8(buf).map_err(|_| json_err(400, "request body is not valid UTF-8"))
}
