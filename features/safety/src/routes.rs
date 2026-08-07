use http::Response;
use tracing::info;

use crate::classifier::{SafetyConfig, SafetyState};

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum SafetyRoute {
    Get,
    Update,
    Delete,
    NotFound,
}

pub(crate) fn resolve_safety_route(method: &str, path: &str) -> SafetyRoute {
    let suffix = match path.strip_prefix("/api/v1/safety") {
        Some(s) => s,
        None => return SafetyRoute::NotFound,
    };

    if !suffix.is_empty() && suffix != "/" {
        return SafetyRoute::NotFound;
    }

    match method {
        "GET" => SafetyRoute::Get,
        "PUT" => SafetyRoute::Update,
        "DELETE" => SafetyRoute::Delete,
        _ => SafetyRoute::NotFound,
    }
}

pub(crate) fn handle_safety_get(state: &SafetyState) -> Response<Vec<u8>> {
    json_ok(&serde_json::json!(state.current_config()))
}

pub(crate) fn handle_safety_update(state: &SafetyState, body: &str) -> Response<Vec<u8>> {
    let config: SafetyConfig = match serde_json::from_str(body) {
        Ok(c) => c,
        Err(e) => return json_err(400, &format!("invalid safety config: {e}")),
    };

    info!(model = %config.llm_model, url = %config.llm_url, "safety classifier updated via management API");
    state.configure(config.clone());

    json_ok(&serde_json::json!(config))
}

pub(crate) fn handle_safety_delete(state: &SafetyState) -> Response<Vec<u8>> {
    state.disable();
    info!("safety classifier disabled via management API");
    json_ok(&serde_json::Value::Null)
}

#[expect(clippy::expect_used, reason = "valid static json response")]
fn json_ok(data: &serde_json::Value) -> Response<Vec<u8>> {
    let wrapper = serde_json::json!({"data": data, "error": null});
    let body = serde_json::to_vec(&wrapper).unwrap_or_default();
    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid json response")
}

#[expect(clippy::expect_used, reason = "valid static json response")]
fn json_err(status: u16, message: &str) -> Response<Vec<u8>> {
    let wrapper = serde_json::json!({"data": null, "error": message});
    let body = serde_json::to_vec(&wrapper).unwrap_or_default();
    Response::builder()
        .status(status)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid json error response")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn get_safety() {
        assert_eq!(
            resolve_safety_route("GET", "/api/v1/safety"),
            SafetyRoute::Get
        );
    }

    #[test]
    fn get_safety_with_trailing_slash() {
        assert_eq!(
            resolve_safety_route("GET", "/api/v1/safety/"),
            SafetyRoute::Get
        );
    }

    #[test]
    fn update_safety() {
        assert_eq!(
            resolve_safety_route("PUT", "/api/v1/safety"),
            SafetyRoute::Update
        );
    }

    #[test]
    fn delete_safety() {
        assert_eq!(
            resolve_safety_route("DELETE", "/api/v1/safety"),
            SafetyRoute::Delete
        );
    }

    #[test]
    fn post_not_supported() {
        assert_eq!(
            resolve_safety_route("POST", "/api/v1/safety"),
            SafetyRoute::NotFound
        );
    }

    #[test]
    fn wrong_prefix() {
        assert_eq!(
            resolve_safety_route("GET", "/api/v1/other"),
            SafetyRoute::NotFound
        );
    }

    #[test]
    fn sub_path_not_found() {
        assert_eq!(
            resolve_safety_route("GET", "/api/v1/safety/config"),
            SafetyRoute::NotFound
        );
    }
}
