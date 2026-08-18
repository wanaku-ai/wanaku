use http::{Response, StatusCode};
use rust_embed::Embed;

use super::response::json_err;

#[derive(Embed)]
#[folder = "../ui/admin/dist/"]
#[prefix = ""]
struct AdminUi;

#[expect(clippy::expect_used, clippy::too_many_lines, reason = "valid static response")]
pub(super) fn serve_ui(ui_override: &Option<std::path::PathBuf>, request_path: &str) -> Response<Vec<u8>> {
    let relative = request_path
        .strip_prefix("/admin")
        .unwrap_or("")
        .trim_start_matches('/');

    let asset_path = if relative.is_empty() {
        "index.html"
    } else {
        relative
    };

    if let Some(ui_root) = ui_override {
        return serve_from_filesystem(ui_root, relative);
    }

    if let Some(file) = AdminUi::get(asset_path) {
        let content_type = mime_for_path(asset_path);
        return Response::builder()
            .status(StatusCode::OK)
            .header("Content-Type", content_type)
            .header("Content-Length", file.data.len())
            .body(file.data.into_owned())
            .expect("valid static response");
    }

    if !relative.contains('.')
        && let Some(index) = AdminUi::get("index.html") {
            return Response::builder()
                .status(StatusCode::OK)
                .header("Content-Type", "text/html; charset=utf-8")
                .header("Content-Length", index.data.len())
                .body(index.data.into_owned())
                .expect("valid static response");
        }

    json_err(StatusCode::NOT_FOUND, "file not found")
}

#[expect(clippy::expect_used, clippy::too_many_lines, reason = "valid static response")]
fn serve_from_filesystem(ui_root: &std::path::Path, relative: &str) -> Response<Vec<u8>> {
    let file_path = if relative.is_empty() {
        ui_root.join("index.html")
    } else {
        ui_root.join(relative)
    };

    let Ok(canonical) = file_path.canonicalize() else {
        if !relative.contains('.')
            && let Ok(index) = std::fs::read(ui_root.join("index.html")) {
                return Response::builder()
                    .status(StatusCode::OK)
                    .header("Content-Type", "text/html; charset=utf-8")
                    .header("Content-Length", index.len())
                    .body(index)
                    .expect("valid static response");
            }
        return json_err(StatusCode::NOT_FOUND, "file not found");
    };

    let Ok(canonical_root) = ui_root.canonicalize() else {
        return json_err(StatusCode::INTERNAL_SERVER_ERROR, "UI root path not found");
    };

    if !canonical.starts_with(&canonical_root) {
        return json_err(StatusCode::FORBIDDEN, StatusCode::FORBIDDEN.canonical_reason().unwrap_or_default());
    }

    let Ok(body) = std::fs::read(&canonical) else {
        return json_err(StatusCode::NOT_FOUND, "file not found");
    };

    let content_type = mime_for_path(canonical.to_str().unwrap_or(""));

    Response::builder()
        .status(StatusCode::OK)
        .header("Content-Type", content_type)
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid static response")
}

fn mime_for_path(path: &str) -> &'static str {
    match path.rsplit('.').next() {
        Some("html") => "text/html; charset=utf-8",
        Some("js" | "mjs") => "application/javascript",
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
