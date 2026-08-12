use std::path::Path;

use http::Response;
use tracing::warn;
use wanaku_praxis_apis::http_response::{json_err, json_ok};

use crate::manifest::PluginManifest;

pub(crate) fn handle_list_plugins(manifests: &[PluginManifest]) -> Response<Vec<u8>> {
    let data = serde_json::to_value(manifests).unwrap_or(serde_json::Value::Array(vec![]));
    json_ok(&data)
}

#[expect(clippy::expect_used, reason = "valid static file response")]
pub(crate) fn handle_serve_file(
    plugins_path: &Path,
    plugin_id: &str,
    file_path: &str,
) -> Response<Vec<u8>> {
    let plugin_root = plugins_path.join(plugin_id);

    let target = if file_path.is_empty() {
        plugin_root.join("index.html")
    } else {
        plugin_root.join(file_path)
    };

    let canonical = match target.canonicalize() {
        Ok(p) => p,
        Err(_) => return json_err(404, "file not found"),
    };

    let canonical_root = match plugin_root.canonicalize() {
        Ok(r) => r,
        Err(_) => return json_err(404, "plugin not found"),
    };

    if !canonical.starts_with(&canonical_root) {
        return json_err(403, "forbidden");
    }

    let body = match std::fs::read(&canonical) {
        Ok(b) => b,
        Err(_) => return json_err(404, "file not found"),
    };

    let content_type = mime_for_path(canonical.to_str().unwrap_or(""));

    Response::builder()
        .status(200)
        .header("Content-Type", content_type)
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid static response")
}

pub(crate) async fn handle_proxy_service(
    client: &reqwest::Client,
    target_url: &str,
    path: &str,
    query: Option<&str>,
    method: &str,
    body: Option<&str>,
) -> Response<Vec<u8>> {
    let url = match query {
        Some(q) => format!("{target_url}{path}?{q}"),
        None => format!("{target_url}{path}"),
    };

    let req_method = match method.parse::<reqwest::Method>() {
        Ok(m) => m,
        Err(_) => return json_err(400, &format!("unsupported method: {method}")),
    };

    let mut request = client.request(req_method, &url);

    if let Some(b) = body {
        request = request
            .header("Content-Type", "application/json")
            .body(b.to_owned());
    }

    let response = match request.send().await {
        Ok(r) => r,
        Err(e) => {
            warn!(url = %url, error = %e, "plugin proxy request failed");
            return json_err(502, "upstream request failed");
        }
    };

    let status = response.status().as_u16();
    let content_type = response
        .headers()
        .get("content-type")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("application/octet-stream")
        .to_owned();
    let response_body = match response.bytes().await {
        Ok(b) => b.to_vec(),
        Err(e) => {
            warn!(error = %e, "failed to read plugin proxy response body");
            return json_err(502, "upstream response read failed");
        }
    };

    build_proxy_response(status, &content_type, response_body)
}

#[expect(clippy::expect_used, reason = "valid static response")]
fn build_proxy_response(status: u16, content_type: &str, body: Vec<u8>) -> Response<Vec<u8>> {
    Response::builder()
        .status(status)
        .header("Content-Type", content_type)
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid proxy response")
}

fn mime_for_path(path: &str) -> &'static str {
    match path.rsplit('.').next() {
        Some("html") => "text/html; charset=utf-8",
        Some("js") | Some("mjs") => "application/javascript",
        Some("css") => "text/css",
        Some("json") => "application/json",
        Some("svg") => "image/svg+xml",
        Some("png") => "image/png",
        Some("ico") => "image/x-icon",
        Some("woff2") => "font/woff2",
        Some("woff") => "font/woff",
        _ => "application/octet-stream",
    }
}
