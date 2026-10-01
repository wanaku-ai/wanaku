use std::collections::HashMap;
use std::path::Path;
use std::sync::{Arc, RwLock};

use http::{Response, StatusCode};
use tracing::warn;
use wanaku_types::http_response::{json_err, json_ok};

use crate::api::{ConfigurePluginRequest, InstallPluginRequest, InstallPluginResponse};
use crate::installer::{InstallError, install_plugin_from_url};
use crate::manifest::PluginManifest;
use crate::persistence::{PluginConfigPersistence, PluginsConfigSnapshot};

pub(crate) fn handle_list_plugins(manifests: &RwLock<Vec<PluginManifest>>) -> Response<Vec<u8>> {
    let list = manifests.read().map(|m| m.clone()).unwrap_or_default();
    let data = serde_json::to_value(list).unwrap_or(serde_json::Value::Array(vec![]));
    json_ok(&data)
}

pub(crate) async fn handle_install_plugin(
    client: &reqwest::Client,
    plugins_path: Option<&Path>,
    manifests: &RwLock<Vec<PluginManifest>>,
    body: Option<&str>,
) -> Response<Vec<u8>> {
    let Some(plugins_dir) = plugins_path else {
        return json_err(
            StatusCode::BAD_REQUEST,
            "plugins directory not configured on server (use --plugins-path)",
        );
    };

    let Some(raw_body) = body else {
        return json_err(StatusCode::BAD_REQUEST, "request body required");
    };

    let req: InstallPluginRequest = match serde_json::from_str(raw_body) {
        Ok(r) => r,
        Err(e) => return json_err(StatusCode::BAD_REQUEST, &format!("invalid request JSON: {e}")),
    };

    match install_plugin_from_url(client, plugins_dir, &req.id, &req.version, &req.url).await {
        Ok(manifest) => {
            if let Ok(mut guard) = manifests.write() {
                guard.retain(|m| m.id != manifest.id);
                guard.push(manifest.clone());
            }
            let val = serde_json::to_value(&InstallPluginResponse { manifest })
                .unwrap_or(serde_json::Value::Null);
            json_ok(&val)
        }
        Err(e) => {
            warn!(plugin = %req.id, error = %e, "plugin installation failed");
            let status = match e {
                InstallError::PluginsPathNotConfigured | InstallError::InsecurePath(_) | InstallError::InvalidManifest | InstallError::IdMismatch(_, _) => {
                    StatusCode::BAD_REQUEST
                }
                InstallError::DownloadFailed(_) => StatusCode::BAD_GATEWAY,
                InstallError::InvalidArchive(_) | InstallError::ManifestError(_) | InstallError::Io(_) => {
                    StatusCode::UNPROCESSABLE_ENTITY
                }
            };
            json_err(status, &e.to_string())
        }
    }
}

pub(crate) fn handle_configure_plugin(
    plugin_id: &str,
    service_map: &RwLock<HashMap<(String, String), String>>,
    persistence: Option<&Arc<dyn PluginConfigPersistence>>,
    body: Option<&str>,
) -> Response<Vec<u8>> {
    let Some(raw_body) = body else {
        return json_err(StatusCode::BAD_REQUEST, "request body required");
    };

    let req: ConfigurePluginRequest = match serde_json::from_str(raw_body) {
        Ok(r) => r,
        Err(e) => return json_err(StatusCode::BAD_REQUEST, &format!("invalid request JSON: {e}")),
    };

    // Update in-memory service map
    if let Ok(mut guard) = service_map.write() {
        // Remove existing mappings for this plugin
        guard.retain(|(p_id, _), _| p_id != plugin_id);
        for (svc_id, target) in &req.services {
            guard.insert((plugin_id.to_owned(), svc_id.clone()), target.target.clone());
        }
    }

    // Persist to default data directory
    if let Some(persist) = persistence {
        let mut snapshot: PluginsConfigSnapshot = match persist.load() {
            Ok(s) => s,
            Err(e) => {
                warn!(plugin = %plugin_id, error = %e, "failed to load existing plugin configuration for update");
                return json_err(StatusCode::INTERNAL_SERVER_ERROR, "failed to read configuration before updating");
            }
        };
        snapshot.insert(plugin_id.to_owned(), req.services);
        if let Err(e) = persist.save(&snapshot) {
            warn!(plugin = %plugin_id, error = %e, "failed to persist plugin configuration");
            return json_err(StatusCode::INTERNAL_SERVER_ERROR, "failed to persist configuration");
        }
    }

    json_ok(&serde_json::json!({"status": "ok"}))
}

#[expect(clippy::expect_used, reason = "valid static file response")]
pub(crate) fn handle_serve_file(
    plugins_path: &Path,
    plugin_id: &str,
    file_path: &str,
) -> Response<Vec<u8>> {
    let plugin_root = plugins_path.join(plugin_id);

    // Locate effective root (either plugin_root itself or nested directory if plugin.json is nested)
    let effective_root = if plugin_root.join("plugin.json").exists() {
        plugin_root.clone()
    } else if let Ok(sub_entries) = std::fs::read_dir(&plugin_root) {
        let mut found = plugin_root.clone();
        for sub_entry in sub_entries.flatten() {
            let sub_path = sub_entry.path();
            if sub_path.is_dir() && sub_path.join("plugin.json").exists() {
                found = sub_path;
                break;
            }
        }
        found
    } else {
        plugin_root.clone()
    };

    let target = if file_path.is_empty() {
        effective_root.join("index.html")
    } else {
        effective_root.join(file_path)
    };

    let Ok(canonical) = target.canonicalize() else {
        return json_err(StatusCode::NOT_FOUND, "file not found");
    };

    let Ok(canonical_root) = plugin_root.canonicalize() else {
        return json_err(StatusCode::NOT_FOUND, "plugin not found");
    };

    if !canonical.starts_with(&canonical_root) {
        return json_err(StatusCode::FORBIDDEN, "forbidden");
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

#[expect(
    clippy::too_many_arguments,
    reason = "proxy handler requires full HTTP context"
)]
#[expect(
    clippy::too_many_lines,
    reason = "HTTP proxy with request/response handling"
)]
pub(crate) async fn handle_proxy_service(
    client: &reqwest::Client,
    target_url: &str,
    path: &str,
    query: Option<&str>,
    method: &str,
    body: Option<&str>,
    headers: &http::HeaderMap,
) -> Response<Vec<u8>> {
    let url = match query {
        Some(q) => format!("{target_url}{path}?{q}"),
        None => format!("{target_url}{path}"),
    };

    let Ok(req_method) = method.parse::<reqwest::Method>() else {
        return json_err(
            StatusCode::BAD_REQUEST,
            &format!("unsupported method: {method}"),
        );
    };

    let mut request = client.request(req_method, &url);

    let accept = headers
        .get(http::header::ACCEPT)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("application/json");
    request = request.header(http::header::ACCEPT, accept);

    if let Some(b) = body {
        request = request
            .header("Content-Type", "application/json")
            .body(b.to_owned());
    }

    let response = match request.send().await {
        Ok(r) => r,
        Err(e) => {
            warn!(url = %url, error = %e, "plugin proxy request failed");
            return json_err(StatusCode::BAD_GATEWAY, "upstream request failed");
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
            return json_err(StatusCode::BAD_GATEWAY, "upstream response read failed");
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
