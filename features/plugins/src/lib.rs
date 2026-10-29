#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

pub mod api;
pub mod catalog;
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

/// One entry of the `plugins` section of `wanaku.yaml`.
#[derive(Debug, Clone, serde::Deserialize)]
pub struct PluginConfig {
    pub id: String,
    #[serde(default)]
    pub services: HashMap<String, PluginServiceConfig>,
}

/// A plugin service mapping in `wanaku.yaml`.
#[derive(Debug, Clone, serde::Deserialize)]
pub struct PluginServiceConfig {
    pub target: String,
}

pub struct PluginsFeature {
    plugins_path: Option<PathBuf>,
    manifests: RwLock<Vec<PluginManifest>>,
    service_map: RwLock<HashMap<(String, String), String>>,
    persistence: Option<Arc<dyn PluginConfigPersistence>>,
    client: reqwest::Client,
    catalog_source: catalog::CatalogSource,
}

impl PluginsFeature {
    fn register_configured_services(&self, plugins: Vec<PluginConfig>) {
        let Ok(mut guard) = self.service_map.write() else {
            return;
        };
        for plugin in plugins {
            for (service_id, service) in plugin.services {
                tracing::info!(
                    plugin = %plugin.id,
                    service = %service_id,
                    target = %service.target,
                    "registered plugin service from config"
                );
                guard.insert((plugin.id.clone(), service_id), service.target);
            }
        }
    }

    #[must_use]
    pub fn new(plugins_path: Option<PathBuf>) -> Self {
        let manifests = match &plugins_path {
            Some(dir) => discover_plugins(dir),
            None => Vec::new(),
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
            plugins_path,
            manifests: RwLock::new(manifests),
            service_map,
            persistence,
            client: reqwest::Client::new(),
            catalog_source: catalog::CatalogSource::from_config(),
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
        tracing::info!(path = %plugins_dir.display(), "plugins directory does not exist yet");
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
            PluginRoute::Catalog => {
                catalog::handle_catalog(&self.client, &self.catalog_source).await
            }
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

    fn load_yaml_config(&self, root: &serde_yaml::Value) {
        if let Some(plugins_val) = root.get("plugins") {
            match serde_yaml::from_value::<Vec<PluginConfig>>(plugins_val.clone()) {
                Ok(plugins) => self.register_configured_services(plugins),
                Err(error) => tracing::warn!(error = %error, "invalid plugins configuration"),
            }
        }

        // Apply persisted service configuration overrides on top of YAML defaults
        if let Some(persist) = &self.persistence {
            if let Ok(snapshot) = persist.load() {
                if let Ok(mut guard) = self.service_map.write() {
                    for (plugin_id, services) in snapshot {
                        for (svc_id, target) in services {
                            guard.insert((plugin_id.clone(), svc_id), target.target);
                        }
                    }
                }
            }
        }
    }

    fn load_env_config(&self) {}
}

#[cfg(test)]
mod tests {
    use super::PluginsFeature;
    use wanaku_types::feature::Feature;

    #[test]
    fn load_yaml_config_registers_plugin_services() {
        let feature = PluginsFeature::new(None);
        let root: serde_yaml::Value = serde_yaml::from_str(
            r#"
plugins:
  - id: customer-management
    services:
      customer-api:
        target: "http://customer-service:8080"
"#,
        )
        .unwrap();

        feature.load_yaml_config(&root);

        let services = feature.service_map.read().unwrap();
        assert_eq!(
            services
                .get(&("customer-management".to_owned(), "customer-api".to_owned()))
                .map(String::as_str),
            Some("http://customer-service:8080")
        );
    }
}
