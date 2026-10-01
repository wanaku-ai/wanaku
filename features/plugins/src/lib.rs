#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

pub mod api;
mod handlers;
pub mod installer;
pub mod manifest;
pub mod persistence;
mod routes;

use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::{Arc, RwLock};

use http::{Response, StatusCode};
use praxis_filter::{FilterRegistry, PipelineExtension};
use wanaku_types::feature::{Feature, HttpContext};
use wanaku_types::http_response::json_err;

use crate::manifest::PluginManifest;
use crate::persistence::{FilePluginConfigPersistence, PluginConfigPersistence};
use crate::routes::{PluginRoute, resolve_plugin_route};

pub struct PluginsFeature {
    plugins_path: Option<PathBuf>,
    manifests: RwLock<Vec<PluginManifest>>,
    service_map: RwLock<HashMap<(String, String), String>>,
    persistence: Option<Arc<dyn PluginConfigPersistence>>,
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

        let persistence = FilePluginConfigPersistence::from_config();
        let service_map = RwLock::new(HashMap::new());

        // Load persisted service configs
        if let Some(persist) = &persistence {
            if let Ok(snapshot) = persist.load() {
                if let Ok(mut guard) = service_map.write() {
                    for (plugin_id, services) in snapshot {
                        for (svc_id, target) in services {
                            guard.insert((plugin_id.clone(), svc_id), target.target);
                        }
                    }
                }
            }
        }

        Self {
            plugins_path: path,
            manifests: RwLock::new(manifests),
            service_map,
            persistence,
            client: reqwest::Client::new(),
        }
    }

    #[must_use]
    pub fn with_persistence(mut self, persistence: Arc<dyn PluginConfigPersistence>) -> Self {
        if let Ok(snapshot) = persistence.load() {
            if let Ok(mut guard) = self.service_map.write() {
                for (plugin_id, services) in snapshot {
                    for (svc_id, target) in services {
                        guard.insert((plugin_id.clone(), svc_id), target.target);
                    }
                }
            }
        }
        self.persistence = Some(persistence);
        self
    }
}

#[expect(
    clippy::too_many_lines,
    clippy::cognitive_complexity,
    clippy::large_stack_frames,
    reason = "plugin discovery with validation"
)]
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

        // Support either entry_path/plugin.json or entry_path/<subdir>/plugin.json
        let (manifest_path, content) = if let Ok(content) = std::fs::read_to_string(entry_path.join("plugin.json")) {
            (entry_path.join("plugin.json"), content)
        } else if let Ok(sub_entries) = std::fs::read_dir(&entry_path) {
            let mut found = None;
            for sub_entry in sub_entries.flatten() {
                let sub_path = sub_entry.path();
                if sub_path.is_dir() {
                    let sub_manifest = sub_path.join("plugin.json");
                    if let Ok(c) = std::fs::read_to_string(&sub_manifest) {
                        found = Some((sub_manifest, c));
                        break;
                    }
                }
            }
            match found {
                Some(f) => f,
                None => continue,
            }
        } else {
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

    #[expect(
        clippy::too_many_lines,
        reason = "route dispatch with plugin proxy logic"
    )]
    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        let route = resolve_plugin_route(ctx.method, ctx.path);
        if route == PluginRoute::NotFound {
            return None;
        }
        Some(match route {
            PluginRoute::ListPlugins => handlers::handle_list_plugins(&self.manifests),
            PluginRoute::InstallPlugin => {
                handlers::handle_install_plugin(
                    &self.client,
                    self.plugins_path.as_deref(),
                    &self.manifests,
                    ctx.body,
                )
                .await
            }
            PluginRoute::ConfigurePlugin(plugin_id) => handlers::handle_configure_plugin(
                &plugin_id,
                &self.service_map,
                self.persistence.as_ref(),
                ctx.body,
            ),
            PluginRoute::ServeFile(plugin_id, file_path) => match &self.plugins_path {
                Some(p) => handlers::handle_serve_file(p, &plugin_id, &file_path),
                None => json_err(StatusCode::NOT_FOUND, "plugins directory not configured"),
            },
            PluginRoute::ProxyService(plugin_id, service_id, proxy_path) => {
                let target = self
                    .service_map
                    .read()
                    .ok()
                    .and_then(|g| g.get(&(plugin_id.clone(), service_id.clone())).cloned());
                match target {
                    Some(target_url) => {
                        handlers::handle_proxy_service(
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

    #[expect(
        clippy::too_many_lines,
        reason = "YAML config parsing with nested plugin/service structure"
    )]
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
