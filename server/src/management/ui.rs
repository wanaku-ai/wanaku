use http::Response;
use rust_embed::Embed;

use super::response::json_err;

#[derive(Embed)]
#[folder = "../ui/admin/dist/"]
#[prefix = ""]
struct AdminUi;

#[expect(clippy::expect_used, reason = "valid static response")]
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
            .status(200)
            .header("Content-Type", content_type)
            .header("Content-Length", file.data.len())
            .body(file.data.into_owned())
            .expect("valid static response");
    }

    if !relative.contains('.') {
        if let Some(index) = AdminUi::get("index.html") {
            return Response::builder()
                .status(200)
                .header("Content-Type", "text/html; charset=utf-8")
                .header("Content-Length", index.data.len())
                .body(index.data.into_owned())
                .expect("valid static response");
        }
    }

    json_err(404, "file not found")
}

#[expect(clippy::expect_used, reason = "valid static response")]
fn serve_from_filesystem(ui_root: &std::path::Path, relative: &str) -> Response<Vec<u8>> {
    let file_path = if relative.is_empty() {
        ui_root.join("index.html")
    } else {
        ui_root.join(relative)
    };

    let canonical = match file_path.canonicalize() {
        Ok(p) => p,
        Err(_) => {
            if !relative.contains('.') {
                if let Ok(index) = std::fs::read(ui_root.join("index.html")) {
                    return Response::builder()
                        .status(200)
                        .header("Content-Type", "text/html; charset=utf-8")
                        .header("Content-Length", index.len())
                        .body(index)
                        .expect("valid static response");
                }
            }
            return json_err(404, "file not found");
        }
    };

    let canonical_root = match ui_root.canonicalize() {
        Ok(r) => r,
        Err(_) => return json_err(500, "UI root path not found"),
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
