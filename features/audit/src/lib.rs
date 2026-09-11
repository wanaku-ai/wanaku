#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

mod config;
pub mod persistence;
mod routes;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};
use std::sync::{Arc, RwLock};
use wanaku_types::audit::{AuditPersistence, DEFAULT_AUDIT_CAPACITY, InMemoryAuditStore};
use wanaku_types::audit_redaction::AuditRedactor;
use wanaku_types::feature::{Feature, HttpContext};

pub struct AuditFeature {
    store: RwLock<InMemoryAuditStore>,
    config: RwLock<config::AuditConfig>,
    persistence: Option<Arc<dyn AuditPersistence>>,
    failure_observer: Option<Arc<dyn wanaku_types::audit::AuditFailureObserver>>,
}

impl AuditFeature {
    #[must_use]
    pub fn new() -> Self {
        Self {
            store: RwLock::new(InMemoryAuditStore::new(DEFAULT_AUDIT_CAPACITY)),
            config: RwLock::new(config::AuditConfig::default()),
            persistence: None,
            failure_observer: None,
        }
    }

    #[must_use]
    pub fn with_persistence(mut self, backend: Arc<dyn AuditPersistence>) -> Self {
        self.persistence = Some(backend);
        self.rebuild_store();
        self
    }

    #[must_use]
    pub fn store(&self) -> InMemoryAuditStore {
        self.store
            .read()
            .map(|store| store.clone())
            .unwrap_or_else(|error| {
                tracing::error!(error = %error, "audit store lock poisoned");
                InMemoryAuditStore::new(DEFAULT_AUDIT_CAPACITY)
            })
    }

    #[must_use]
    pub fn with_metrics(mut self, metrics: wanaku_infra::metrics::MetricsStore) -> Self {
        self.failure_observer = Some(Arc::new(metrics));
        self.rebuild_store();
        self
    }

    #[must_use]
    pub fn configured_from(mut self, root: Option<&serde_yaml::Value>) -> Self {
        let yaml_config =
            root.and_then(|value| value.get("audit")).and_then(
                |value| match serde_yaml::from_value::<config::AuditConfig>(value.clone()) {
                    Ok(config) => Some(config),
                    Err(error) => {
                        tracing::warn!(error = %error, "invalid audit configuration");
                        None
                    }
                },
            );
        if let Some(config) = yaml_config {
            self.config = RwLock::new(config);
        }
        match self.config.write() {
            Ok(mut config) => {
                config.apply_environment();
                config.normalize();
            }
            Err(error) => tracing::error!(error = %error, "audit configuration lock poisoned"),
        }
        self.rebuild_store();
        self
    }

    fn rebuild_store(&self) {
        let config = self
            .config
            .read()
            .map(|value| value.clone())
            .unwrap_or_default();
        let redactor = AuditRedactor::new(
            config.sensitive_fields,
            config.sensitive_json_pointers,
            config.payload_max_bytes,
        );
        let replacement = InMemoryAuditStore::configured(
            config.max_records,
            self.persistence.clone(),
            redactor,
            config.capture_payloads,
            self.failure_observer.clone(),
        );
        match self.store.write() {
            Ok(mut store) => *store = replacement,
            Err(error) => tracing::error!(error = %error, "audit store lock poisoned"),
        }
    }
}

impl Default for AuditFeature {
    fn default() -> Self {
        Self::new()
    }
}

struct AuditStoreExtension {
    store: InMemoryAuditStore,
}
impl PipelineExtension for AuditStoreExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.store.clone());
    }
}

#[async_trait::async_trait]
impl Feature for AuditFeature {
    fn name(&self) -> &'static str {
        "audit"
    }
    fn register_filters(&self, _registry: &mut FilterRegistry) {}
    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        self.store
            .read()
            .map(|store| {
                vec![Box::new(AuditStoreExtension {
                    store: store.clone(),
                }) as Box<dyn PipelineExtension>]
            })
            .unwrap_or_default()
    }
    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        let route = routes::resolve(ctx.method, ctx.path);
        if route == routes::AuditRoute::NotFound {
            return None;
        }
        Some(match self.store.read() {
            Ok(store) => routes::handle(&store, route, ctx.query),
            Err(error) => {
                tracing::error!(error = %error, "audit store lock poisoned");
                wanaku_types::http_response::json_err(
                    http::StatusCode::INTERNAL_SERVER_ERROR,
                    "audit store unavailable",
                )
            }
        })
    }
    fn load_yaml_config(&self, _root: &serde_yaml::Value) {}
    fn load_env_config(&self) {}
}
