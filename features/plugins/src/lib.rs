#![deny(unsafe_code)]

mod handlers;
pub mod manifest;
mod routes;

use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::RwLock;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension};
use wanaku_praxis_apis::feature::Feature;
use wanaku_praxis_apis::http_response::json_err;

use crate::manifest::PluginManifest;
use crate::routes::{PluginRoute, resolve_plugin_route};

pub struct PluginsFeature {
    plugins_path: RwLock<Option<PathBuf>>,
    manifests: RwLock<Vec<PluginManifest>>,
    service_map: RwLock<HashMap<(String, String), String>>,
    client: reqwest::Client,
}

impl Default for PluginsFeature {
    fn default() -> Self {
        Self::new()
    }
}

impl PluginsFeature {
    #[must_use]
    pub fn new() -> Self {
        Self {
            plugins_path: RwLock::new(None),
            manifests: RwLock::new(Vec::new()),
            service_map: RwLock::new(HashMap::new()),
            client: reqwest::Client::new(),
        }
    }
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

    async fn handle_route(
        &self,
        method: &str,
        path: &str,
        query: Option<&str>,
        body: Option<&str>,
    ) -> Option<Response<Vec<u8>>> {
        let route = resolve_plugin_route(method, path);
        if route == PluginRoute::NotFound {
            return None;
        }
        Some(match route {
            PluginRoute::ListPlugins => {
                let manifests = self
                    .manifests
                    .read()
                    .ok()
                    .map(|g| g.clone())
                    .unwrap_or_default();
                routes::handle_list(&manifests)
            }
            PluginRoute::ServeFile(plugin_id, file_path) => {
                let plugins_path = self
                    .plugins_path
                    .read()
                    .ok()
                    .and_then(|g| g.clone());
                match plugins_path {
                    Some(p) => routes::handle_file(&p, &plugin_id, &file_path),
                    None => json_err(404, "plugins directory not configured"),
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
                            query,
                            method,
                            body,
                        )
                        .await
                    }
                    None => json_err(
                        404,
                        &format!("service {service_id} not found for plugin {plugin_id}"),
                    ),
                }
            }
            PluginRoute::NotFound => return None,
        })
    }

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

    fn load_env_config(&self) {
        let path_str = match std::env::var("WANAKU_PLUGINS_PATH") {
            Ok(p) if !p.is_empty() => p,
            _ => return,
        };

        let plugins_dir = PathBuf::from(&path_str);
        if !plugins_dir.is_dir() {
            tracing::warn!(path = %path_str, "plugins directory does not exist");
            return;
        }

        if let Ok(mut guard) = self.plugins_path.write() {
            *guard = Some(plugins_dir.clone());
        }

        let entries = match std::fs::read_dir(&plugins_dir) {
            Ok(e) => e,
            Err(e) => {
                tracing::warn!(path = %path_str, error = %e, "failed to read plugins directory");
                return;
            }
        };

        let mut discovered = Vec::new();
        for entry in entries.flatten() {
            let entry_path = entry.path();
            if !entry_path.is_dir() {
                continue;
            }

            let manifest_path = entry_path.join("plugin.json");
            let content = match std::fs::read_to_string(&manifest_path) {
                Ok(c) => c,
                Err(_) => continue,
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

        if let Ok(mut guard) = self.manifests.write() {
            *guard = discovered;
        }
    }
}
