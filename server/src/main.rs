#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

#[cfg(unix)]
#[global_allocator]
static GLOBAL: tikv_jemallocator::Jemalloc = tikv_jemallocator::Jemalloc;

use clap::Parser;
use praxis_core::config::{Config, ProtocolKind};
use praxis_core::health::build_health_registry;
use praxis_core::PingoraServerRuntime;
use praxis_filter::FilterRegistry;
use praxis_protocol::{ListenerPipelines, Protocol as _};
use praxis_protocol::http::PingoraHttp;
use tracing::info;

use wanaku_infra::persistence::FilePersistence;
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::feature::Feature;
use wanaku_types::governance::GovernanceConfig;
use wanaku_types::registry::{ForwardEntry, ForwardRegistry};

#[expect(clippy::too_many_lines, reason = "server bootstrap")]
fn main() {
    let args = ServerArgs::parse();
    let config = wanaku_server::load_config(args.pipeline_config.as_deref())
        .unwrap_or_else(|e| fatal(&e));

    praxis_core::logging::init_tracing(&config)
        .unwrap_or_else(|e| fatal(&e));

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

    let wanaku_config = load_wanaku_yaml(&args.wanaku_config);
    let governance = load_governance_config(wanaku_config.as_ref()).unwrap_or_else(|e| fatal(&e));
    let features: Vec<Box<dyn Feature>> =
        build_features(&args, &metrics_store, wanaku_config.as_ref());

    load_config(wanaku_config.as_ref(), &wanaku_registry, &features);

    let mut filter_registry = wanaku_server::build_full_registry();
    for feature in &features {
        feature.register_filters(&mut filter_registry);
    }

    let service_deps = ServiceDeps {
        health_registry: build_health_registry(&config.clusters),
        kv_stores: praxis_core::kv::KvStoreRegistry::new(),
        mgmt_registry: wanaku_registry.clone(),
        governance,
        features,
    };

    let pipelines = build_pipelines(&config, &wanaku_registry, &mut filter_registry, &service_deps);

    info!("initializing server");
    let mut server = PingoraServerRuntime::new(&config);

    setup_management_service(&config, &pipelines, service_deps, &mut server);

    info!("starting wanaku server");
    server.run()
}

fn build_pipelines(config: &Config, wanaku_registry: &InMemoryRegistry, filter_registry: &mut FilterRegistry, service_deps: &ServiceDeps) -> ListenerPipelines {
    info!("building wanaku pipelines");
    let pipeline_deps = wanaku_server::pipelines::PipelineDeps::new(
        filter_registry,
        &service_deps.health_registry,
        &service_deps.kv_stores,
        wanaku_registry,
        &service_deps.governance,
        &service_deps.features,
    );
    wanaku_server::pipelines::resolve_pipelines(config, &pipeline_deps)
        .unwrap_or_else(|e| fatal(&e))
}

struct ServiceDeps {
    health_registry: praxis_core::health::HealthRegistry,
    kv_stores: praxis_core::kv::KvStoreRegistry,
    mgmt_registry: InMemoryRegistry,
    governance: GovernanceConfig,
    features: Vec<Box<dyn Feature>>,
}

fn load_config(
    wanaku_config: Option<&serde_yaml::Value>,
    wanaku_registry: &InMemoryRegistry,
    features: &[Box<dyn Feature>],
) {
    if let Some(yaml) = wanaku_config {
        load_core_config(yaml, wanaku_registry);
        for feature in features {
            feature.load_yaml_config(yaml);
        }
    }

    for feature in features {
        feature.load_env_config();
    }
}

fn load_governance_config(
    root: Option<&serde_yaml::Value>,
) -> Result<GovernanceConfig, String> {
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
            Some(deps.health_registry),
            Some(deps.kv_stores),
            config.admin.verbose,
        );
    }

    let mgmt_registry = deps.mgmt_registry.clone();

    let mgmt_addr = &wanaku_types::config::ENV.mgmt_listen;
    let mgmt = wanaku_server::management::WanakuManagementService::new(
        deps.mgmt_registry,
        deps.features,
    );
    let mut mgmt_service = pingora_core::services::listening::Service::new(
        "wanaku-management".to_owned(),
        mgmt,
    );
    mgmt_service.add_tcp(mgmt_addr);
    server.server_mut().add_service(mgmt_service);
    info!(address = %mgmt_addr, "management API enabled");

    register_forward_reconnect_service(mgmt_registry, server);
}

/// Registers the periodic background service that re-probes forwards currently
/// marked unavailable, flipping them back to available once they recover. This
/// runs on the persistent Pingora runtime (the startup discovery runtime is a
/// one-shot). Disabled when `WANAKU_FORWARD_HEALTHCHECK_INTERVAL` is `0`.
fn register_forward_reconnect_service(
    registry: InMemoryRegistry,
    server: &mut PingoraServerRuntime,
) {
    let Some(interval) = wanaku_types::config::ENV.forward_healthcheck_interval else {
        info!("forward reconnect loop disabled (WANAKU_FORWARD_HEALTHCHECK_INTERVAL=0)");
        return;
    };

    let task = wanaku_server::management::reconnect_service(registry, interval);
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

fn build_features(
    args: &ServerArgs,
    metrics_store: &wanaku_infra::metrics::MetricsStore,
    wanaku_config: Option<&serde_yaml::Value>,
) -> Vec<Box<dyn Feature>> {
    let mut audit = wanaku_feature_audit::AuditFeature::new().with_metrics(metrics_store.clone());
    if let Some(backend) = wanaku_feature_audit::persistence::FileAuditPersistence::from_config() {
        info!("audit persistence enabled");
        audit = audit.with_persistence(backend);
    }
    audit = audit.configured_from(wanaku_config);
    let mut action_policy = wanaku_feature_action_policy::ActionPolicyFeature::new();
    if let Some(backend) =
        wanaku_feature_action_policy::revision_persistence::FileRevisionPersistence::from_config()
    {
        info!("action policy revision persistence enabled");
        action_policy = action_policy.with_revision_persistence(backend);
    }
    let mut evaluator = wanaku_feature_evaluator::EvaluatorFeature::new()
        .with_metrics(metrics_store.clone())
        .with_audit(audit.store());
    if let Some(backend) =
        wanaku_feature_evaluator::revision_persistence::FileRevisionPersistence::from_config()
    {
        info!("evaluator revision persistence enabled");
        evaluator = evaluator.with_revision_persistence(backend);
    }

    vec![
        Box::new(audit),
        Box::new(wanaku_feature_metrics::MetricsFeature::new(metrics_store.clone())),
        Box::new(wanaku_feature_intercept::InterceptFeature::new()),
        Box::new(wanaku_feature_mcp_metadata::McpMetadataFeature::new()),
        Box::new(action_policy),
        Box::new(evaluator),
        Box::new(wanaku_feature_plugins::PluginsFeature::new(args.plugins_path.as_deref())),
    ]
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
    #[arg(long, value_name = "PATH")]
    plugins_path: Option<String>,
}

fn load_wanaku_yaml(path: &str) -> Option<serde_yaml::Value> {
    let content = match std::fs::read_to_string(path) {
        Ok(c) => c,
        Err(e) => {
            tracing::warn!(path = %path, error = %e, "wanaku config not found, starting with empty registry");
            return None;
        }
    };

    match serde_yaml::from_str(&content) {
        Ok(c) => Some(c),
        Err(e) => {
            tracing::warn!(path = %path, error = %e, "failed to parse wanaku config");
            None
        }
    }
}

#[expect(clippy::cognitive_complexity, clippy::too_many_lines, reason = "config loading requires sequential steps")]
fn load_core_config(config: &serde_yaml::Value, registry: &InMemoryRegistry) {
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
                wanaku_server::management::discover_and_update_forward(registry, fwd).await;
            }
        });
    }
}

#[expect(clippy::print_stderr, clippy::exit, reason = "fatal error before runtime is available")]
fn fatal(err: &dyn std::fmt::Display) -> ! {
    eprintln!("fatal: {err}");
    std::process::exit(1)
}

#[cfg(test)]
mod tests {
    use super::load_governance_config;
    use wanaku_types::governance::{
        AuditLevel, EnforcementMode, FailureBehavior, NoMatchBehavior,
    };

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
