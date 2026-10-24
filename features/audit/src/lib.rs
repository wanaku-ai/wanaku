#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

mod config;
mod credential_recorder;
pub mod persistence;
mod routes;

pub use credential_recorder::CredentialAuditRecorder;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};
use std::sync::Arc;
use wanaku_infra::credentials::CredentialAuditSink;
use wanaku_types::audit::{AuditFailureObserver, AuditPersistence, InMemoryAuditStore};
use wanaku_types::audit_redaction::{AuditRedactionRules, AuditRedactor};
use wanaku_types::feature::{Feature, HttpContext};

/// Inputs that the audit feature needs to build its single store.
///
/// The server collects every input before it constructs the feature. The
/// feature then builds the store once. No method replaces the store later, so
/// every handle from the feature writes into the store that the audit API reads.
#[derive(Default)]
pub struct AuditDeps<'a> {
    /// Root of the `wanaku.yaml` document. The feature reads the `audit` key.
    pub config_root: Option<&'a serde_yaml::Value>,
    /// Durable backend. The store loads it once and starts one worker.
    pub persistence: Option<Arc<dyn AuditPersistence>>,
    /// Receives storage failure notifications, for example the metrics store.
    pub failure_observer: Option<Arc<dyn AuditFailureObserver>>,
}

pub struct AuditFeature {
    store: InMemoryAuditStore,
    config: config::AuditConfig,
}

impl AuditFeature {
    /// Resolve the effective configuration and build the audit store once.
    #[must_use]
    pub fn new(deps: AuditDeps<'_>) -> Self {
        let mut config = yaml_config(deps.config_root);
        config.apply_environment();
        config.normalize();
        Self::with_config(config, deps)
    }

    fn with_config(config: config::AuditConfig, deps: AuditDeps<'_>) -> Self {
        let redactor = AuditRedactor::new(
            AuditRedactionRules {
                include_defaults: config.include_default_redaction_rules,
                sensitive_fields: config.sensitive_fields.clone(),
                credential_markers: config.credential_markers.clone(),
                token_prefixes: config.token_prefixes.clone(),
            },
            config.sensitive_json_pointers.clone(),
            config.payload_max_bytes,
        );
        let store = InMemoryAuditStore::configured(
            config.max_records,
            deps.persistence,
            redactor,
            config.capture_payloads,
            deps.failure_observer,
        )
        .with_external_capacity(config.external_max_records);
        Self { store, config }
    }

    /// A sink that records credential brokerage outcomes into the audit store.
    ///
    /// The request pipeline gets this sink from the request extensions. The
    /// forward discovery path runs outside the pipeline, so the server obtains
    /// the sink here and threads it explicitly into the discovery callers.
    #[must_use]
    pub fn credential_audit_sink(&self) -> Arc<dyn CredentialAuditSink> {
        Arc::new(CredentialAuditRecorder::new(self.store.clone()))
    }

    /// A handle to the store that the audit API reads.
    #[must_use]
    pub fn store(&self) -> InMemoryAuditStore {
        self.store.clone()
    }
}

/// Read the `audit` YAML section. Invalid YAML gives the default configuration.
fn yaml_config(root: Option<&serde_yaml::Value>) -> config::AuditConfig {
    match root.and_then(|value| value.get("audit")) {
        Some(value) => serde_yaml::from_value(value.clone()).unwrap_or_else(|error| {
            tracing::warn!(error = %error, "invalid audit configuration");
            config::AuditConfig::default()
        }),
        None => config::AuditConfig::default(),
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

/// Injects the credential audit sink so the credential filter can record
/// brokerage outcomes on the request-pipeline path.
struct CredentialAuditSinkExtension {
    sink: Arc<dyn CredentialAuditSink>,
}
impl PipelineExtension for CredentialAuditSinkExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.sink.clone());
    }
}

#[async_trait::async_trait]
impl Feature for AuditFeature {
    fn name(&self) -> &'static str {
        "audit"
    }
    fn register_filters(&self, _registry: &mut FilterRegistry) {}
    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        vec![
            Box::new(AuditStoreExtension {
                store: self.store.clone(),
            }),
            Box::new(CredentialAuditSinkExtension {
                sink: self.credential_audit_sink(),
            }),
        ]
    }
    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        let route = routes::resolve(ctx.method, ctx.path);
        if route == routes::AuditRoute::NotFound {
            return None;
        }
        if route == routes::AuditRoute::Ingest {
            return Some(routes::ingest(
                &self.store,
                self.config.ingestion_enabled,
                ctx.body,
            ));
        }
        Some(routes::handle(&self.store, route, ctx.query))
    }
    fn load_yaml_config(&self, _root: &serde_yaml::Value) {}
    fn load_env_config(&self) {}
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Mutex;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use wanaku_infra::credentials::{CredentialAuditRecord, ExpiryCategory, ResolutionOutcome};
    use wanaku_types::TOOLS_CALL;
    use wanaku_types::audit::{AuditCategory, AuditDecision, AuditEvent, AuditStore};
    use wanaku_types::credentials::CredentialPurpose;

    #[derive(Default)]
    struct CountingPersistence {
        loads: AtomicUsize,
        saved: Mutex<Vec<AuditEvent>>,
    }

    impl AuditPersistence for CountingPersistence {
        fn load(&self) -> Result<Vec<AuditEvent>, String> {
            self.loads.fetch_add(1, Ordering::SeqCst);
            Ok(Vec::new())
        }

        fn save(&self, events: &[AuditEvent]) -> Result<(), String> {
            *self.saved.lock().map_err(|error| error.to_string())? = events.to_vec();
            Ok(())
        }
    }

    /// Build the feature from YAML only, so `WANAKU_AUDIT_*` variables in the
    /// test environment cannot change the result.
    fn feature(yaml: &str, persistence: Option<Arc<dyn AuditPersistence>>) -> AuditFeature {
        let root = serde_yaml::from_str::<serde_yaml::Value>(yaml).expect("yaml");
        let mut config = yaml_config(Some(&root));
        config.normalize();
        AuditFeature::with_config(
            config,
            AuditDeps {
                persistence,
                ..AuditDeps::default()
            },
        )
    }

    fn credential_record() -> CredentialAuditRecord {
        CredentialAuditRecord {
            binding_id: "b1".to_owned(),
            binding_revision: 1,
            resolver_types: vec!["fake".to_owned()],
            outcome: ResolutionOutcome::Resolved,
            mechanism: "bearer".to_owned(),
            forward_id: "fwd-a".to_owned(),
            upstream_origin: "https://api.example.com:443".to_owned(),
            purpose: CredentialPurpose::Discovery,
            expiry_category: ExpiryCategory::NotCached,
            failure_reason: None,
        }
    }

    fn evaluator_event() -> AuditEvent {
        AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Allow,
            TOOLS_CALL,
            "evaluator_allowed",
            "Allowed by evaluator",
        )
    }

    async fn call(
        feature: &AuditFeature,
        method: &str,
        path: &str,
        body: Option<&str>,
    ) -> serde_json::Value {
        let headers = http::HeaderMap::new();
        let response = feature
            .handle_route(&HttpContext::new(method, path, None, body, &headers))
            .await
            .expect("audit route");
        assert_eq!(response.status(), http::StatusCode::OK);
        let body: serde_json::Value = serde_json::from_slice(response.body()).expect("json body");
        body["data"].clone()
    }

    #[test]
    fn construction_loads_persistence_once() {
        let backend = Arc::new(CountingPersistence::default());
        let feature = feature("audit:\n  max_records: 50\n", Some(backend.clone()));
        let _sink = feature.credential_audit_sink();
        let _store = feature.store();
        let _extensions = feature.pipeline_extensions();

        assert_eq!(backend.loads.load(Ordering::SeqCst), 1);
        assert_eq!(feature.store().health().capacity, 50);
    }

    #[test]
    fn public_constructor_loads_persistence_once() {
        let backend = Arc::new(CountingPersistence::default());
        let feature = AuditFeature::new(AuditDeps {
            persistence: Some(backend.clone()),
            ..AuditDeps::default()
        });
        let _sink = feature.credential_audit_sink();
        let _extensions = feature.pipeline_extensions();

        assert_eq!(backend.loads.load(Ordering::SeqCst), 1);
    }

    #[tokio::test]
    async fn every_handle_writes_into_the_audit_api_store() {
        let feature = feature("audit:\n  ingestion_enabled: true\n", None);

        feature
            .credential_audit_sink()
            .record_credential_audit(&credential_record());
        feature.store().record(evaluator_event());
        let semantic = serde_json::json!({
            "source": {"instance_id":"runtime-1","forward_name":"support","namespace":"default","catalog_revision":"r1","catalog_digest":"digest1"},
            "record": {"schemaVersion":1,"eventId":"semantic-1","timestamp":"2026-10-12T12:00:00Z","category":"evaluation"}
        })
        .to_string();
        let accepted = call(&feature, "POST", "/api/v1/audit/semantic", Some(&semantic)).await;
        assert_eq!(accepted["status"], "accepted_in_memory");

        let page = call(&feature, "GET", "/api/v1/audit/events", None).await;
        assert_eq!(page["total"], 3);
        let events = page["events"].as_array().cloned().unwrap_or_default();
        let has = |key: &str, value: &str| events.iter().any(|event| event[key] == value);
        assert!(has("operation", "credential/discovery"));
        assert!(has("operation", TOOLS_CALL));
        assert!(
            events
                .iter()
                .any(|event| event["source"]["event_id"] == "semantic-1")
        );
    }

    #[test]
    fn dropping_the_final_owner_flushes_pending_records() {
        let backend = Arc::new(CountingPersistence::default());
        let feature = feature("{}", Some(backend.clone()));
        let sink = feature.credential_audit_sink();
        let store = feature.store();

        sink.record_credential_audit(&credential_record());
        store.record(evaluator_event());
        drop(feature);
        drop(sink);
        drop(store);

        let saved = backend.saved.lock().expect("saved events");
        assert_eq!(saved.len(), 2);
        assert_eq!(
            saved
                .iter()
                .filter(|event| event.operation == "credential/discovery")
                .count(),
            1
        );
        assert_eq!(backend.loads.load(Ordering::SeqCst), 1);
    }

    #[test]
    fn configuration_reserves_native_capacity() {
        let feature = feature(
            "audit:\n  max_records: 3\n  external_max_records: 10\n  ingestion_enabled: true\n",
            None,
        );

        assert!(feature.config.ingestion_enabled);
        assert_eq!(feature.config.max_records, 3);
        assert_eq!(feature.config.external_max_records, 2);
    }

    #[test]
    fn invalid_yaml_falls_back_to_defaults() {
        let feature = feature("audit:\n  max_records: many\n", None);

        assert!(!feature.config.ingestion_enabled);
        assert_eq!(
            feature.store().health().capacity,
            wanaku_types::audit::DEFAULT_AUDIT_CAPACITY
        );
    }
}
