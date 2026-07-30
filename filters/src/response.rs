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
