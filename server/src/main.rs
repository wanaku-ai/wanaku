#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

#[cfg(unix)]
#[global_allocator]
static GLOBAL: tikv_jemallocator::Jemalloc = tikv_jemallocator::Jemalloc;

use std::sync::Arc;

use clap::Parser;
use praxis_core::PingoraServerRuntime;
use praxis_core::config::ProtocolKind;
use praxis_protocol::Protocol as _;
use praxis_protocol::http::PingoraHttp;
use tracing::info;

use wanaku_infra::credentials::{CredentialAuditSink, CredentialBroker};
use wanaku_infra::persistence::FilePersistence;
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_server::app::{App, AppPersistence, AppResources, AppSettings};
use wanaku_types::credentials::{EnvResolver, ResolverRegistry};
use wanaku_types::governance::GovernanceConfig;

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

    let wanaku_config = load_wanaku_yaml(&args.wanaku_config).unwrap_or_else(|error| fatal(&error));
    let governance = load_governance_config(wanaku_config.as_ref()).unwrap_or_else(|e| fatal(&e));

    // The credential broker holds a shared, single-flight resolution cache. It
    // is built once and shared across startup discovery, every request pipeline,
    // and the management and reconnect services.
    let resources = AppResources {
        persistence: AppPersistence {
            registry: FilePersistence::from_config(),
            audit: wanaku_feature_audit::persistence::FileAuditPersistence::from_config(),
            action_policy:
                wanaku_feature_action_policy::revision_persistence::FileRevisionPersistence::from_config(),
            evaluator:
                wanaku_feature_evaluator::revision_persistence::FileRevisionPersistence::from_config(),
        },
        broker: Arc::new(build_credential_broker()),
    };
    // Feature status and pipeline filters use the same immutable startup posture.
    let settings = AppSettings {
        pipeline: &config,
        wanaku_config: wanaku_config.as_ref(),
        governance,
        plugins_dir: args.plugins_dir(),
    };
    let app = App::build(&settings, resources).unwrap_or_else(|e| fatal(&e));

    info!("initializing server");
    let mut server = PingoraServerRuntime::new(&config);

    setup_management_service(&config, app, &mut server);

    info!("starting wanaku server");
    server.run()
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
    app: App,
    server: &mut PingoraServerRuntime,
) {
    if config
        .listeners
        .iter()
        .any(|listener| listener.protocol == ProtocolKind::Http)
    {
        let _cert_shutdowns = Box::new(PingoraHttp)
            .register(server, config, &app.pipelines)
            .unwrap_or_else(|e| fatal(&e));
    }

    if let Some(admin_addr) = &config.admin.address {
        praxis_protocol::http::pingora::health::add_admin_endpoints_to_pingora_server(
            server.server_mut(),
            admin_addr,
            praxis_protocol::http::pingora::health::AdminEndpointOptions {
                health_registry: Some(app.health_registry),
                kv_registry: Some(app.kv_stores),
                verbose: config.admin.verbose,
                ..Default::default()
            },
        );
    }

    let mgmt_addr = &wanaku_types::config::ENV.mgmt_listen;
    let mut mgmt_service = pingora_core::services::listening::Service::new(
        "wanaku-management".to_owned(),
        app.management,
    );
    mgmt_service.add_tcp(mgmt_addr);
    server.server_mut().add_service(mgmt_service);
    info!(address = %mgmt_addr, "management API enabled");

    register_forward_reconnect_service(
        app.registry.clone(),
        app.broker,
        Some(app.audit_sink),
        server,
    );
    register_registry_persistence_service(app.registry, server);
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
    use super::{load_governance_config, load_wanaku_yaml, resolve_plugins_path};
    use std::path::PathBuf;
    use wanaku_types::config::PersistEnv;
    use wanaku_types::governance::{AuditLevel, EnforcementMode, FailureBehavior, NoMatchBehavior};

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
