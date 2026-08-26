#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

mod handlers;
pub mod manifest;
mod routes;

use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::RwLock;

use http::{Response, StatusCode};
use praxis_filter::{FilterRegistry, PipelineExtension};
use wanaku_types::feature::{Feature, HttpContext};
use wanaku_types::http_response::json_err;

use crate::manifest::PluginManifest;
use crate::routes::{PluginRoute, resolve_plugin_route};

pub struct PluginsFeature {
    plugins_path: Option<PathBuf>,
    manifests: Vec<PluginManifest>,
    service_map: RwLock<HashMap<(String, String), String>>,
    client: reqwest::Client,
}

impl PluginsFeature {
    #[must_use]
    pub fn new(plugins_path: Option<&str>) -> Self {
        let (path, manifests) = match plugins_path {
            Some(p) => {
                let dir = PathBuf::from(p);
                let discovered = discover_plugins(&dir);
                (Some(dir), discovered)
            }
            None => (None, Vec::new()),
        };

        Self {
            plugins_path: path,
            manifests,
            service_map: RwLock::new(HashMap::new()),
            client: reqwest::Client::new(),
        }
    }
}

#[expect(clippy::too_many_lines, clippy::cognitive_complexity, clippy::large_stack_frames, reason = "plugin discovery with validation")]
fn discover_plugins(plugins_dir: &PathBuf) -> Vec<PluginManifest> {
    if !plugins_dir.is_dir() {
        tracing::warn!(path = %plugins_dir.display(), "plugins directory does not exist");
        return Vec::new();
    }

    let entries = match std::fs::read_dir(plugins_dir) {
        Ok(e) => e,
        Err(e) => {
            tracing::warn!(path = %plugins_dir.display(), error = %e, "failed to read plugins directory");
            return Vec::new();
        }
    };

    let mut discovered = Vec::new();
    for entry in entries.flatten() {
        let entry_path = entry.path();
        if !entry_path.is_dir() {
            continue;
        }

        let manifest_path = entry_path.join("plugin.json");
        let Ok(content) = std::fs::read_to_string(&manifest_path) else {
            continue;
        };

        let manifest: PluginManifest = match serde_json::from_str(&content) {
            Ok(m) => m,
            Err(e) => {
                tracing::warn!(
                    path = %manifest_path.display(),
                    error = %e,
                    "failed to parse plugin manifest"
                );
                continue;
            }
        };

        if manifest.id.is_empty() || manifest.entrypoint.is_empty() {
            tracing::warn!(
                path = %manifest_path.display(),
                "plugin manifest missing required fields (id, entrypoint)"
            );
            continue;
        }

        tracing::info!(
            plugin = %manifest.id,
            name = %manifest.name,
            version = %manifest.version,
            "discovered plugin"
        );
        discovered.push(manifest);
    }

    discovered
}

#[async_trait::async_trait]
impl Feature for PluginsFeature {
    fn name(&self) -> &'static str {
        "plugins"
    }

    fn register_filters(&self, _registry: &mut FilterRegistry) {}

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        vec![]
    }

    #[expect(clippy::too_many_lines, reason = "route dispatch with plugin proxy logic")]
    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        let route = resolve_plugin_route(ctx.method, ctx.path);
        if route == PluginRoute::NotFound {
            return None;
        }
        Some(match route {
            PluginRoute::ListPlugins => {
                routes::handle_list(&self.manifests)
            }
            PluginRoute::ServeFile(plugin_id, file_path) => {
                match &self.plugins_path {
                    Some(p) => routes::handle_file(p, &plugin_id, &file_path),
                    None => json_err(StatusCode::NOT_FOUND, "plugins directory not configured"),
                }
            }
            PluginRoute::ProxyService(plugin_id, service_id, proxy_path) => {
                let target = self
                    .service_map
                    .read()
                    .ok()
                    .and_then(|g| g.get(&(plugin_id.clone(), service_id.clone())).cloned());
                match target {
                    Some(target_url) => {
                        routes::handle_proxy(
                            &self.client,
                            &target_url,
                            &proxy_path,
                            ctx.query,
                            ctx.method,
                            ctx.body,
                            ctx.headers,
                        )
                        .await
                    }
                    None => json_err(
                        StatusCode::NOT_FOUND,
                        &format!("service {service_id} not found for plugin {plugin_id}"),
                    ),
                }
            }
            PluginRoute::NotFound => return None,
        })
    }

    #[expect(clippy::too_many_lines, reason = "YAML config parsing with nested plugin/service structure")]
    fn load_yaml_config(&self, root: &serde_yaml::Value) {
        let Some(plugins_val) = root.get("plugins") else {
            return;
        };
        let Some(plugins_seq) = plugins_val.as_sequence() else {
            return;
        };

        for plugin_val in plugins_seq {
            let Some(id) = plugin_val.get("id").and_then(|v| v.as_str()) else {
                continue;
            };
            let Some(services_val) = plugin_val.get("services") else {
                continue;
            };
            let Some(services_map) = services_val.as_mapping() else {
                continue;
            };

            for (svc_key, svc_val) in services_map {
                let Some(svc_id) = svc_key.as_str() else {
                    continue;
                };
                let Some(target) = svc_val.get("target").and_then(|v| v.as_str()) else {
                    continue;
                };

                if let Ok(mut guard) = self.service_map.write() {
                    guard.insert((id.to_owned(), svc_id.to_owned()), target.to_owned());
                }

                tracing::info!(
                    plugin = %id,
                    service = %svc_id,
                    target = %target,
                    "registered plugin service from config"
                );
            }
        }
    }

    fn load_env_config(&self) {}
}
