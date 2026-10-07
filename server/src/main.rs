#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

#[cfg(unix)]
#[global_allocator]
static GLOBAL: tikv_jemallocator::Jemalloc = tikv_jemallocator::Jemalloc;

use std::sync::Arc;

use clap::Parser;
use praxis_core::PingoraServerRuntime;
use praxis_core::config::{Config, ProtocolKind};
use praxis_core::health::build_health_registry;
use praxis_filter::FilterRegistry;
use praxis_protocol::http::PingoraHttp;
use praxis_protocol::{ListenerPipelines, Protocol as _};
use tracing::info;

use wanaku_infra::credentials::{CredentialAuditSink, CredentialBroker};
use wanaku_infra::persistence::FilePersistence;
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::credentials::{
    CredentialBinding, EnvResolver, NormalizedOrigin, ResolverRegistry,
};
use wanaku_types::feature::Feature;
use wanaku_types::governance::GovernanceConfig;
use wanaku_types::registry::{BindingRegistry, ForwardEntry, ForwardRegistry};

#[expect(clippy::too_many_lines, reason = "server bootstrap")]
fn main() {
    let args = ServerArgs::parse();
    let config =
        wanaku_server::load_config(args.pipeline_config.as_deref()).unwrap_or_else(|e| fatal(&e));

    let _tracing_guard = praxis_core::logging::init_tracing(&config).unwrap_or_else(|e| fatal(&e));

    // Praxis enables no built-in rustls provider. Install its OpenSSL-backed
    // provider before anything builds a TLS configuration.
    praxis_tls::provider::install();
    if !praxis_tls::provider::installed() {
        fatal(&format!(
            "failed to install the {} crypto provider",
            praxis_tls::provider::name()
        ));
    }

    let metrics_store = wanaku_infra::metrics::MetricsStore::new();

    let wanaku_registry = match FilePersistence::from_config() {
        Some(backend) => {
            info!("file-based persistence enabled");
            let registry = InMemoryRegistry::with_persistence(backend);
            registry.load_persisted();
            registry
        }
        None => InMemoryRegistry::new(),
    };

    // Paired with InterceptFeature: adds x-request-id to tool schemas for conversation tracking
    wanaku_registry.enable_request_id_injection();

    // The credential broker holds a shared, single-flight resolution cache. It
    // is built once and shared across startup discovery, every request pipeline,
    // and the management and reconnect services.
    let credential_broker = Arc::new(build_credential_broker());

    let wanaku_config = load_wanaku_yaml(&args.wanaku_config).unwrap_or_else(|error| fatal(&error));
    let governance = load_governance_config(wanaku_config.as_ref()).unwrap_or_else(|e| fatal(&e));
    // Feature status and pipeline filters use the same immutable startup posture.
    let (features, credential_audit_sink) =
        build_features(&args, &metrics_store, wanaku_config.as_ref(), &governance);

    load_config(
        wanaku_config.as_ref(),
        &wanaku_registry,
        &credential_broker,
        Some(credential_audit_sink.as_ref()),
        &features,
    );
    for feature in &features {
        feature
            .validate_startup()
            .unwrap_or_else(|error| fatal(&error));
    }

    let mut filter_registry = wanaku_server::build_full_registry();
    for feature in &features {
        feature.register_filters(&mut filter_registry);
    }

    let service_deps = ServiceDeps {
        health_registry: build_health_registry(&config.clusters),
        kv_stores: praxis_core::kv::KvStoreRegistry::new(),
        mgmt_registry: wanaku_registry.clone(),
        governance,
        broker: credential_broker,
        audit_sink: credential_audit_sink,
        features,
    };

    let pipelines = build_pipelines(
        &config,
        &wanaku_registry,
        &mut filter_registry,
        &service_deps,
    );

    info!("initializing server");
    let mut server = PingoraServerRuntime::new(&config);

    setup_management_service(&config, &pipelines, service_deps, &mut server);

    info!("starting wanaku server");
    server.run()
}

fn build_pipelines(
    config: &Config,
    wanaku_registry: &InMemoryRegistry,
    filter_registry: &mut FilterRegistry,
    service_deps: &ServiceDeps,
) -> ListenerPipelines {
    info!("building wanaku pipelines");
    let pipeline_deps = wanaku_server::pipelines::PipelineDeps::new(
        filter_registry,
        &service_deps.health_registry,
        &service_deps.kv_stores,
        wanaku_registry,
        &service_deps.governance,
        &service_deps.broker,
        &service_deps.features,
    );
    wanaku_server::pipelines::resolve_pipelines(config, &pipeline_deps)
        .unwrap_or_else(|e| fatal(&e))
}

/// Build the shared credential broker.
///
/// The broker ships with the built-in `env:` resolver. Bindings are
/// operator-controlled, so request data can never select an environment
/// variable. Unknown resolver schemes fail closed.
fn build_credential_broker() -> CredentialBroker {
    let resolvers = ResolverRegistry::new().with_resolver(Arc::new(EnvResolver::new()));
    CredentialBroker::new(resolvers)
}

struct ServiceDeps {
    health_registry: praxis_core::health::HealthRegistry,
    kv_stores: praxis_core::kv::KvStoreRegistry,
    mgmt_registry: InMemoryRegistry,
    governance: GovernanceConfig,
    broker: Arc<CredentialBroker>,
    audit_sink: Arc<dyn CredentialAuditSink>,
    features: Vec<Box<dyn Feature>>,
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

fn load_governance_config(root: Option<&serde_yaml::Value>) -> Result<GovernanceConfig, String> {
    let config = root
        .and_then(|value| value.get("governance"))
        .map(|value| serde_yaml::from_value::<GovernanceConfig>(value.clone()))
        .transpose()
        .map_err(|error| format!("invalid governance configuration: {error}"))?
        .unwrap_or_default();
    config
        .validate()
        .map_err(|error| format!("invalid governance configuration: {error}"))?;
    Ok(config)
}

#[expect(clippy::too_many_lines, reason = "service registration is sequential")]
fn setup_management_service(
    config: &praxis_core::config::Config,
    pipelines: &praxis_protocol::ListenerPipelines,
    deps: ServiceDeps,
    server: &mut PingoraServerRuntime,
) {
    if config
        .listeners
        .iter()
        .any(|listener| listener.protocol == ProtocolKind::Http)
    {
        let _cert_shutdowns = Box::new(PingoraHttp)
            .register(server, config, pipelines)
            .unwrap_or_else(|e| fatal(&e));
    }

    if let Some(admin_addr) = &config.admin.address {
        praxis_protocol::http::pingora::health::add_admin_endpoints_to_pingora_server(
            server.server_mut(),
            admin_addr,
            praxis_protocol::http::pingora::health::AdminEndpointOptions {
                health_registry: Some(deps.health_registry),
                kv_registry: Some(deps.kv_stores),
                verbose: config.admin.verbose,
                ..Default::default()
            },
        );
    }

    let mgmt_registry = deps.mgmt_registry.clone();
    let persistence_registry = deps.mgmt_registry.clone();
    let reconnect_broker = deps.broker.clone();
    let reconnect_audit_sink = deps.audit_sink.clone();

    let mgmt_addr = &wanaku_types::config::ENV.mgmt_listen;
    let mgmt = wanaku_server::management::WanakuManagementService::new(
        deps.mgmt_registry,
        deps.broker,
        Some(deps.audit_sink),
        deps.features,
    );
    let mut mgmt_service =
        pingora_core::services::listening::Service::new("wanaku-management".to_owned(), mgmt);
    mgmt_service.add_tcp(mgmt_addr);
    server.server_mut().add_service(mgmt_service);
    info!(address = %mgmt_addr, "management API enabled");

    register_forward_reconnect_service(
        mgmt_registry,
        reconnect_broker,
        Some(reconnect_audit_sink),
        server,
    );
    register_registry_persistence_service(persistence_registry, server);
}

fn register_registry_persistence_service(
    registry: InMemoryRegistry,
    server: &mut PingoraServerRuntime,
) {
    if !registry.persistence_status().enabled {
        return;
    }
    let service = wanaku_server::management::registry_persistence_service(registry);
    let background_service = pingora_core::services::background::GenBackgroundService::new(
        "wanaku-registry-persistence".to_owned(),
        service,
    );
    server.server_mut().add_service(background_service);
}

/// Registers the periodic background service that re-probes forwards currently
/// marked unavailable, flipping them back to available once they recover. This
/// runs on the persistent Pingora runtime (the startup discovery runtime is a
/// one-shot). Disabled when `WANAKU_FORWARD_HEALTHCHECK_INTERVAL` is `0`.
fn register_forward_reconnect_service(
    registry: InMemoryRegistry,
    broker: Arc<CredentialBroker>,
    audit_sink: Option<Arc<dyn CredentialAuditSink>>,
    server: &mut PingoraServerRuntime,
) {
    let Some(interval) = wanaku_types::config::ENV.forward_healthcheck_interval else {
        info!("forward reconnect loop disabled (WANAKU_FORWARD_HEALTHCHECK_INTERVAL=0)");
        return;
    };

    let task = wanaku_server::management::reconnect_service(registry, broker, audit_sink, interval);
    let reconnect_service = pingora_core::services::background::GenBackgroundService::new(
        "wanaku-forward-reconnect".to_owned(),
        task,
    );
    server.server_mut().add_service(reconnect_service);
    info!(
        interval_secs = interval.as_secs(),
        "forward reconnect loop enabled"
    );
}

/// Build the feature set and the credential audit sink.
///
/// The sink adapts credential brokerage records into audit events on the shared
/// audit store. The request pipeline gets it from the request extensions; the
/// forward discovery path runs outside the pipeline, so this returns the sink for
/// the server to thread into the management, reconnect, and startup discovery
/// callers explicitly.
fn build_features(
    args: &ServerArgs,
    metrics_store: &wanaku_infra::metrics::MetricsStore,
    wanaku_config: Option<&serde_yaml::Value>,
    governance: &GovernanceConfig,
) -> (Vec<Box<dyn Feature>>, Arc<dyn CredentialAuditSink>) {
    let mut audit = wanaku_feature_audit::AuditFeature::new().with_metrics(metrics_store.clone());
    if let Some(backend) = wanaku_feature_audit::persistence::FileAuditPersistence::from_config() {
        info!("audit persistence enabled");
        audit = audit.with_persistence(backend);
    }
    audit = audit.configured_from(wanaku_config);
    let credential_audit_sink = audit.credential_audit_sink();
    let mut action_policy = wanaku_feature_action_policy::ActionPolicyFeature::new();
    if let Some(backend) =
        wanaku_feature_action_policy::revision_persistence::FileRevisionPersistence::from_config()
    {
        info!("action policy revision persistence enabled");
        action_policy = action_policy.with_revision_persistence(backend);
    }
    let evaluator = build_evaluator(metrics_store, audit.store(), governance);

    let features: Vec<Box<dyn Feature>> = vec![
        Box::new(audit),
        Box::new(wanaku_feature_metrics::MetricsFeature::new(
            metrics_store.clone(),
        )),
        Box::new(wanaku_feature_intercept::InterceptFeature::new()),
        Box::new(wanaku_feature_mcp_metadata::McpMetadataFeature::new()),
        Box::new(action_policy),
        Box::new(evaluator),
        Box::new(wanaku_feature_plugins::PluginsFeature::new(
            args.plugins_dir(),
        )),
    ];
    (features, credential_audit_sink)
}

/// Returns the explicit `--plugins-path`, or `<persist-dir>/plugins` when
/// persistence is enabled. Returns `None` when neither is available.
fn resolve_plugins_path(
    explicit: Option<&str>,
    persist: Option<&wanaku_types::config::PersistEnv>,
) -> Option<std::path::PathBuf> {
    match explicit {
        Some(path) => Some(std::path::PathBuf::from(path)),
        None => persist.map(|p| p.dir.join("plugins")),
    }
}

fn build_evaluator(
    metrics_store: &wanaku_infra::metrics::MetricsStore,
    audit: wanaku_types::audit::InMemoryAuditStore,
    governance: &GovernanceConfig,
) -> wanaku_feature_evaluator::EvaluatorFeature {
    let mut evaluator = wanaku_feature_evaluator::EvaluatorFeature::new()
        .with_metrics(metrics_store.clone())
        .with_audit(audit)
        .with_governance(governance.clone());
    if let Some(backend) =
        wanaku_feature_evaluator::revision_persistence::FileRevisionPersistence::from_config()
    {
        info!("evaluator revision persistence enabled");
        evaluator = evaluator.with_revision_persistence(backend);
    }
    evaluator
}

#[derive(Debug, Parser)]
#[command(version, about)]
struct ServerArgs {
    /// Pipeline configuration file. Uses the embedded configuration when omitted.
    #[arg(long, value_name = "PATH")]
    pipeline_config: Option<String>,

    /// Wanaku bootstrap configuration file.
    #[arg(long, value_name = "PATH", default_value = "wanaku.yaml")]
    wanaku_config: String,

    /// Directory containing UI plugin subdirectories.
    /// Defaults to `<WANAKU_PERSIST_PATH>/plugins` when persistence is enabled.
    #[arg(long, value_name = "PATH")]
    plugins_path: Option<String>,
}

impl ServerArgs {
    fn plugins_dir(&self) -> Option<std::path::PathBuf> {
        resolve_plugins_path(
            self.plugins_path.as_deref(),
            wanaku_types::config::ENV.persist.as_ref(),
        )
    }
}

fn load_wanaku_yaml(path: &str) -> Result<Option<serde_yaml::Value>, String> {
    let content = match std::fs::read_to_string(path) {
        Ok(content) => content,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            tracing::warn!(path, "wanaku config not found, using defaults");
            return Ok(None);
        }
        Err(_) => return Err("cannot read wanaku configuration".to_owned()),
    };
    let root: serde_yaml::Value = serde_yaml::from_str(&content)
        .map_err(|_| "invalid wanaku configuration YAML".to_owned())?;
    match root {
        serde_yaml::Value::Mapping(_) => Ok(Some(root)),
        serde_yaml::Value::Null => Ok(None),
        _ => Err("wanaku configuration must be a YAML mapping".to_owned()),
    }
}

#[expect(
    clippy::cognitive_complexity,
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
                wanaku_server::management::discover_and_update_forward(
                    registry, broker, audit_sink, fwd,
                )
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

#[expect(
    clippy::print_stderr,
    clippy::exit,
    reason = "fatal error before runtime is available"
)]
fn fatal(err: &dyn std::fmt::Display) -> ! {
    eprintln!("fatal: {err}");
    std::process::exit(1)
}

#[cfg(test)]
mod tests {
    use super::{load_bindings, load_governance_config, load_wanaku_yaml, resolve_plugins_path};
    use std::path::PathBuf;
    use wanaku_infra::registry::InMemoryRegistry;
    use wanaku_types::config::PersistEnv;
    use wanaku_types::governance::{AuditLevel, EnforcementMode, FailureBehavior, NoMatchBehavior};
    use wanaku_types::registry::BindingRegistry;

    #[test]
    fn plugins_path_prefers_explicit_value() {
        let persist = PersistEnv {
            dir: PathBuf::from("/data/wanaku"),
        };
        assert_eq!(
            resolve_plugins_path(Some("/custom/plugins"), Some(&persist)),
            Some(PathBuf::from("/custom/plugins"))
        );
    }

    #[test]
    fn plugins_path_defaults_to_persist_dir() {
        let persist = PersistEnv {
            dir: PathBuf::from("/data/wanaku"),
        };
        assert_eq!(
            resolve_plugins_path(None, Some(&persist)),
            Some(PathBuf::from("/data/wanaku/plugins"))
        );
    }

    #[test]
    fn plugins_path_is_none_without_persistence() {
        assert_eq!(resolve_plugins_path(None, None), None);
        assert_eq!(
            resolve_plugins_path(Some("/custom/plugins"), None),
            Some(PathBuf::from("/custom/plugins"))
        );
    }

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

    #[test]
    fn malformed_bootstrap_yaml_is_not_missing_configuration() {
        let file = tempfile::NamedTempFile::new().unwrap();
        for invalid in ["evaluators: [", "[evaluators]", "not-a-mapping"] {
            std::fs::write(file.path(), invalid).unwrap();
            assert!(load_wanaku_yaml(file.path().to_str().unwrap()).is_err());
        }
        std::fs::write(file.path(), "evaluators: []").unwrap();
        assert!(
            load_wanaku_yaml(file.path().to_str().unwrap())
                .unwrap()
                .is_some()
        );
    }

    #[test]
    fn absent_governance_config_uses_fail_safe_defaults() {
        let config = load_governance_config(None).unwrap();
        let posture = config.resolve("default");

        assert_eq!(posture.mode, EnforcementMode::Enforce);
        assert_eq!(posture.no_match, NoMatchBehavior::Deny);
        assert_eq!(posture.on_failure, FailureBehavior::Deny);
        assert_eq!(posture.audit_level, AuditLevel::Basic);
    }

    #[test]
    fn governance_config_loads_namespace_overrides() {
        let root: serde_yaml::Value = serde_yaml::from_str(
            r#"
governance:
  namespaces:
    sandbox:
      mode: audit
      no_match: allow
"#,
        )
        .unwrap();
        let config = load_governance_config(Some(&root)).unwrap();
        let posture = config.resolve("sandbox");

        assert_eq!(posture.mode, EnforcementMode::Audit);
        assert_eq!(posture.no_match, NoMatchBehavior::Allow);
        assert_eq!(posture.on_failure, FailureBehavior::Deny);
    }

    #[test]
    fn governance_config_rejects_disabled_scope_without_reason() {
        let root: serde_yaml::Value = serde_yaml::from_str(
            r#"
governance:
  namespaces:
    maintenance:
      mode: disabled
"#,
        )
        .unwrap();

        let error = load_governance_config(Some(&root)).unwrap_err();
        assert!(error.contains("requires a reason"));
    }
}
