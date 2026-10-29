//! Production runtime graph assembly.
//!
//! [`App::build`] connects the registry, credential broker, features, filter
//! registry, and listener pipelines. The binary and the server tests call the
//! same function. The binary resolves the startup settings and runtime
//! resources first, and registers the listeners and background services
//! after assembly.

use std::path::PathBuf;
use std::sync::Arc;

use praxis_core::config::Config;
use praxis_protocol::ListenerPipelines;
use tracing::info;

use wanaku_infra::audit::InMemoryAuditStore;
use wanaku_infra::credentials::{CredentialAuditSink, CredentialBroker};
use wanaku_infra::metrics::MetricsStore;
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::audit::{AuditHandle, AuditPersistence};
use wanaku_types::credentials::{CredentialBinding, NormalizedOrigin};
use wanaku_types::feature::Feature;
use wanaku_types::governance::GovernanceConfig;
use wanaku_types::persistence::PersistenceBackend;
use wanaku_types::registry::{BindingRegistry, ForwardEntry, ForwardRegistry};

use crate::management::WanakuManagementService;
use crate::pipelines::{PipelineDeps, resolve_pipelines};

/// Startup settings that the startup adapter resolved before assembly.
pub struct AppSettings<'a> {
    /// Praxis pipeline configuration, with environment overrides applied.
    pub pipeline: &'a Config,
    /// Parsed Wanaku bootstrap configuration, if a file was present.
    pub wanaku_config: Option<&'a serde_yaml::Value>,
    /// Validated governance posture configuration.
    pub governance: GovernanceConfig,
    /// Directory that contains the UI plugin subdirectories.
    pub plugins_dir: Option<PathBuf>,
}

/// Optional persistence backends. `None` keeps the state in memory only.
#[derive(Default)]
pub struct AppPersistence {
    /// Registry persistence (tools, resources, prompts, namespaces, forwards).
    pub registry: Option<Arc<dyn PersistenceBackend>>,
    /// Audit event persistence.
    pub audit: Option<Arc<dyn AuditPersistence>>,
    /// Action-policy revision persistence.
    pub action_policy:
        Option<Arc<dyn wanaku_feature_action_policy::revision_persistence::RevisionPersistence>>,
    /// Evaluator revision persistence.
    pub evaluator:
        Option<Arc<dyn wanaku_feature_evaluator::revision_persistence::RevisionPersistence>>,
}

/// Runtime resources that the startup adapter constructed before assembly.
pub struct AppResources {
    /// Persistence backends.
    pub persistence: AppPersistence,
    /// Shared credential broker with its configured resolvers.
    pub broker: Arc<CredentialBroker>,
}

/// The assembled application state.
///
/// The management service and the listener pipelines share the same
/// registry, broker, policy, evaluator, and audit state.
pub struct App {
    /// Shared Wanaku registry.
    pub registry: InMemoryRegistry,
    /// Shared credential broker.
    pub broker: Arc<CredentialBroker>,
    /// Credential audit sink that writes into the shared audit store.
    pub audit_sink: Arc<dyn CredentialAuditSink>,
    /// Shared audit store.
    pub audit_store: InMemoryAuditStore,
    /// Health-check registry for the configured clusters.
    pub health_registry: praxis_core::health::HealthRegistry,
    /// Key-value store registry for filter state.
    pub kv_stores: praxis_core::kv::KvStoreRegistry,
    /// Filter pipelines for each configured listener.
    pub pipelines: ListenerPipelines,
    /// Management API service. It owns the feature set.
    pub management: WanakuManagementService,
}

impl App {
    /// Assemble the production runtime graph.
    ///
    /// The function loads the bootstrap configuration into the registry and
    /// the features. When the bootstrap configuration declares forwards, the
    /// function runs forward discovery on a temporary runtime. Do not call it
    /// from inside an async runtime with forwards configured.
    ///
    /// # Errors
    ///
    /// Returns an error when the bootstrap configuration has an unknown key,
    /// when a feature rejects the startup configuration, or when pipeline
    /// construction fails.
    #[expect(clippy::too_many_lines, reason = "runtime assembly is sequential")]
    pub fn build(settings: &AppSettings<'_>, resources: AppResources) -> Result<Self, String> {
        if let Some(config) = settings.wanaku_config {
            crate::config_keys::reject_unknown_keys(config)?;
        }
        let AppResources {
            persistence,
            broker,
        } = resources;
        let registry = build_registry(persistence.registry);
        // Paired with InterceptFeature: adds x-request-id to tool schemas for conversation tracking
        registry.enable_request_id_injection();

        let metrics_store = MetricsStore::new();
        let audit = build_audit(&metrics_store, settings.wanaku_config, persistence.audit);
        let audit_store = audit.store();
        let audit_sink = audit.credential_audit_sink();
        let features = build_features(
            audit,
            &metrics_store,
            settings,
            &registry,
            FeaturePersistence {
                action_policy: persistence.action_policy,
                evaluator: persistence.evaluator,
            },
        );

        load_config(
            settings.wanaku_config,
            &registry,
            &broker,
            Some(audit_sink.as_ref()),
            &features,
        );
        for feature in &features {
            feature.validate_startup()?;
        }

        let mut filter_registry = crate::build_full_registry();
        for feature in &features {
            feature.register_filters(&mut filter_registry);
        }

        let health_registry =
            praxis_core::health::build_health_registry(&settings.pipeline.clusters);
        let kv_stores = praxis_core::kv::KvStoreRegistry::new();
        info!("building wanaku pipelines");
        let pipelines = resolve_pipelines(
            settings.pipeline,
            &PipelineDeps::new(
                &filter_registry,
                &health_registry,
                &kv_stores,
                &registry,
                &settings.governance,
                &broker,
                &features,
            ),
        )
        .map_err(|error| error.to_string())?;

        let management = WanakuManagementService::new(
            registry.clone(),
            broker.clone(),
            Some(audit_sink.clone()),
            features,
        );

        Ok(Self {
            registry,
            broker,
            audit_sink,
            audit_store,
            health_registry,
            kv_stores,
            pipelines,
            management,
        })
    }
}

fn build_registry(backend: Option<Arc<dyn PersistenceBackend>>) -> InMemoryRegistry {
    match backend {
        Some(backend) => {
            info!("file-based persistence enabled");
            let registry = InMemoryRegistry::with_persistence(backend);
            registry.load_persisted();
            registry
        }
        None => InMemoryRegistry::new(),
    }
}

struct FeaturePersistence {
    action_policy:
        Option<Arc<dyn wanaku_feature_action_policy::revision_persistence::RevisionPersistence>>,
    evaluator: Option<Arc<dyn wanaku_feature_evaluator::revision_persistence::RevisionPersistence>>,
}

/// Build the feature set around the shared audit feature.
///
/// The action-policy, evaluator, and simulation features share policy,
/// evaluator, and audit state here.
fn build_features(
    audit: wanaku_feature_audit::AuditFeature,
    metrics_store: &MetricsStore,
    settings: &AppSettings<'_>,
    registry: &InMemoryRegistry,
    persistence: FeaturePersistence,
) -> Vec<Box<dyn Feature>> {
    let action_policy = build_action_policy(persistence.action_policy);
    let evaluator = build_evaluator(
        metrics_store,
        audit.audit_handle(),
        &settings.governance,
        persistence.evaluator,
    );
    let simulation = wanaku_feature_simulation::SimulationFeature::new(
        wanaku_feature_simulation::SimulationDeps {
            policy: action_policy.state(),
            evaluators: evaluator.state(),
            registry: registry.clone(),
            governance: settings.governance.clone(),
            audit: audit.audit_handle(),
        },
    );

    vec![
        Box::new(audit),
        Box::new(wanaku_feature_metrics::MetricsFeature::new(
            metrics_store.clone(),
        )),
        Box::new(wanaku_feature_intercept::InterceptFeature::new()),
        Box::new(wanaku_feature_mcp_metadata::McpMetadataFeature::new()),
        Box::new(action_policy),
        Box::new(evaluator),
        Box::new(simulation),
        Box::new(wanaku_feature_plugins::PluginsFeature::new(
            settings.plugins_dir.clone(),
        )),
    ]
}

fn build_audit(
    metrics_store: &MetricsStore,
    wanaku_config: Option<&serde_yaml::Value>,
    backend: Option<Arc<dyn AuditPersistence>>,
) -> wanaku_feature_audit::AuditFeature {
    if backend.is_some() {
        info!("audit persistence enabled");
    }
    wanaku_feature_audit::AuditFeature::new(wanaku_feature_audit::AuditDeps {
        config_root: wanaku_config,
        persistence: backend,
        failure_observer: Some(Arc::new(metrics_store.clone())),
    })
}

fn build_action_policy(
    backend: Option<
        Arc<dyn wanaku_feature_action_policy::revision_persistence::RevisionPersistence>,
    >,
) -> wanaku_feature_action_policy::ActionPolicyFeature {
    let action_policy = wanaku_feature_action_policy::ActionPolicyFeature::new();
    match backend {
        Some(backend) => {
            info!("action policy revision persistence enabled");
            action_policy.with_revision_persistence(backend)
        }
        None => action_policy,
    }
}

fn build_evaluator(
    metrics_store: &MetricsStore,
    audit: AuditHandle,
    governance: &GovernanceConfig,
    backend: Option<Arc<dyn wanaku_feature_evaluator::revision_persistence::RevisionPersistence>>,
) -> wanaku_feature_evaluator::EvaluatorFeature {
    let evaluator = wanaku_feature_evaluator::EvaluatorFeature::new()
        .with_metrics(metrics_store.clone())
        .with_audit(audit)
        .with_governance(governance.clone());
    match backend {
        Some(backend) => {
            info!("evaluator revision persistence enabled");
            evaluator.with_revision_persistence(backend)
        }
        None => evaluator,
    }
}

fn load_config(
    wanaku_config: Option<&serde_yaml::Value>,
    wanaku_registry: &InMemoryRegistry,
    broker: &Arc<CredentialBroker>,
    audit_sink: Option<&dyn CredentialAuditSink>,
    features: &[Box<dyn Feature>],
) {
    if let Some(yaml) = wanaku_config {
        load_core_config(yaml, wanaku_registry, broker, audit_sink);
    }
    let empty = serde_yaml::Value::Mapping(serde_yaml::Mapping::new());
    for feature in features {
        feature.load_yaml_config(wanaku_config.unwrap_or(&empty));
        feature.load_env_config();
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "config loading requires sequential steps"
)]
fn load_core_config(
    config: &serde_yaml::Value,
    registry: &InMemoryRegistry,
    broker: &Arc<CredentialBroker>,
    audit_sink: Option<&dyn CredentialAuditSink>,
) {
    let mut forwards = Vec::new();
    if let Some(fwd_list) = config.get("forwards").and_then(|f| f.as_sequence()) {
        for fwd_value in fwd_list {
            match serde_yaml::from_value::<ForwardEntry>(fwd_value.clone()) {
                Ok(mut fwd) => {
                    fwd.available = false;
                    info!(forward = %fwd.name, address = %fwd.address, "registered forward from config");
                    registry.register_forward(fwd.clone());
                    forwards.push(fwd);
                }
                Err(e) => {
                    tracing::warn!(error = %e, "failed to deserialize forward entry from config");
                }
            }
        }
    }

    // Credential bindings are authored here, alongside the forwards that own
    // them. A binding must be registered before discovery runs, because a
    // forward that references a discovery binding fails closed when the binding
    // is absent.
    load_bindings(config, registry);

    if !forwards.is_empty() {
        let rt = match tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
        {
            Ok(rt) => rt,
            Err(e) => {
                tracing::error!(error = %e, "failed to create runtime for forward discovery");
                return;
            }
        };

        rt.block_on(async {
            for fwd in &forwards {
                info!(forward = %fwd.name, address = %fwd.address, "discovering from forward");
                crate::management::discover_and_update_forward(registry, broker, audit_sink, fwd)
                    .await;
            }
        });
    }
}

/// Register the credential bindings declared under the top-level `bindings`
/// key of the wanaku configuration.
///
/// Each entry deserializes into a [`CredentialBinding`] and is validated before
/// registration. Malformed or invalid bindings are logged and skipped so a
/// single bad entry does not stop the rest of the configuration from loading.
#[expect(clippy::too_many_lines, reason = "binding validation is sequential")]
fn load_bindings(config: &serde_yaml::Value, registry: &InMemoryRegistry) {
    let Some(binding_list) = config.get("bindings").and_then(|b| b.as_sequence()) else {
        return;
    };
    for binding_value in binding_list {
        match serde_yaml::from_value::<CredentialBinding>(binding_value.clone()) {
            Ok(mut binding) => {
                // The configured origin must be the exact normalized origin the
                // broker compares against. Normalize it here so an operator who
                // omits the explicit port (for example `https://api.example.com`
                // instead of `https://api.example.com:443`) does not register a
                // binding that logs success but silently fails closed on use.
                match NormalizedOrigin::from_address(binding.origin.as_str()) {
                    Ok(origin) => binding.origin = origin,
                    Err(e) => {
                        tracing::warn!(
                            binding_id = %binding.id,
                            error = %e,
                            "skipping credential binding with an invalid origin"
                        );
                        continue;
                    }
                }
                if let Err(e) = binding.validate() {
                    tracing::warn!(
                        binding_id = %binding.id,
                        error = %e,
                        "skipping invalid credential binding from config"
                    );
                    continue;
                }
                info!(
                    binding_id = %binding.id,
                    forward_id = %binding.forward_id,
                    "registered credential binding from config"
                );
                registry.register_binding(binding);
            }
            Err(e) => {
                tracing::warn!(error = %e, "failed to deserialize credential binding from config");
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::load_bindings;
    use wanaku_infra::registry::InMemoryRegistry;
    use wanaku_types::registry::BindingRegistry;

    #[test]
    fn load_bindings_registers_valid_binding_and_normalizes_origin() {
        // The origin omits the explicit port on purpose: the loader must
        // normalize it to the exact form the broker compares against.
        let config: serde_yaml::Value = serde_yaml::from_str(
            r#"
bindings:
  - id: my-binding
    forwardId: my-forward
    origin: "https://api.example.com"
    mechanism:
      type: bearer
    secretRefs:
      - "env:API_TOKEN"
    allowedPurposes:
      - discovery
"#,
        )
        .unwrap();
        let registry = InMemoryRegistry::new();

        load_bindings(&config, &registry);

        let binding = registry.get_binding("my-binding").unwrap();
        assert_eq!(binding.forward_id, "my-forward");
        assert_eq!(binding.secret_refs[0].scheme(), "env");
        assert_eq!(binding.origin.as_str(), "https://api.example.com:443");
    }

    #[test]
    fn load_bindings_skips_binding_with_invalid_origin() {
        let config: serde_yaml::Value = serde_yaml::from_str(
            r#"
bindings:
  - id: bad-origin
    forwardId: my-forward
    origin: "not-a-url"
    mechanism:
      type: bearer
    secretRefs:
      - "env:API_TOKEN"
    allowedPurposes:
      - discovery
"#,
        )
        .unwrap();
        let registry = InMemoryRegistry::new();

        load_bindings(&config, &registry);

        assert!(registry.get_binding("bad-origin").is_none());
        assert_eq!(registry.binding_count(), 0);
    }

    #[test]
    fn load_bindings_skips_invalid_binding() {
        // Basic auth requires two secret references; a single one is invalid.
        let config: serde_yaml::Value = serde_yaml::from_str(
            r#"
bindings:
  - id: bad-binding
    forwardId: my-forward
    origin: "https://api.example.com:443"
    mechanism:
      type: basic
    secretRefs:
      - "env:ONLY_ONE"
    allowedPurposes:
      - invocation
"#,
        )
        .unwrap();
        let registry = InMemoryRegistry::new();

        load_bindings(&config, &registry);

        assert!(registry.get_binding("bad-binding").is_none());
        assert_eq!(registry.binding_count(), 0);
    }

    #[test]
    fn load_bindings_is_noop_without_bindings_key() {
        let config: serde_yaml::Value = serde_yaml::from_str("forwards: []").unwrap();
        let registry = InMemoryRegistry::new();

        load_bindings(&config, &registry);

        assert_eq!(registry.binding_count(), 0);
    }
}
