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

use wanaku_apis::feature::Feature;
use wanaku_apis::persistence::FilePersistence;
use wanaku_apis::registry::{
    ForwardEntry, ForwardRegistry, InMemoryRegistry,
};

#[expect(clippy::too_many_lines, reason = "server bootstrap")]
fn main() {
    let args = ServerArgs::parse();
    let config = wanaku_server::load_config(args.pipeline_config.as_deref())
        .unwrap_or_else(|e| fatal(&e));

    praxis_core::logging::init_tracing(&config)
        .unwrap_or_else(|e| fatal(&e));

    let metrics_store = wanaku_apis::metrics::MetricsStore::new();

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

    let features: Vec<Box<dyn Feature>> = build_features(&args, &metrics_store);

    load_config(&args, &wanaku_registry, &features);

    let mut filter_registry = wanaku_server::build_full_registry();
    for feature in &features {
        feature.register_filters(&mut filter_registry);
    }

    let service_deps = ServiceDeps {
        health_registry: build_health_registry(&config.clusters),
        kv_stores: praxis_core::kv::KvStoreRegistry::new(),
        mgmt_registry: wanaku_registry.clone(),
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
        &service_deps.features,
    );
    wanaku_server::pipelines::resolve_pipelines(config, &pipeline_deps)
        .unwrap_or_else(|e| fatal(&e))
}

struct ServiceDeps {
    health_registry: praxis_core::health::HealthRegistry,
    kv_stores: praxis_core::kv::KvStoreRegistry,
    mgmt_registry: InMemoryRegistry,
    features: Vec<Box<dyn Feature>>,
}

fn load_config(args: &ServerArgs, wanaku_registry: &InMemoryRegistry, features: &Vec<Box<dyn Feature>>) {
    let wanaku_config = load_wanaku_yaml(&args.wanaku_config);
    if let Some(ref yaml) = wanaku_config {
        load_core_config(yaml, wanaku_registry);
        for feature in features {
            feature.load_yaml_config(yaml);
        }
    }

    for feature in features {
        feature.load_env_config();
    }
}

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

    let mgmt_addr = &wanaku_apis::config::ENV.mgmt_listen;
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
}

fn build_features(
    args: &ServerArgs,
    metrics_store: &wanaku_apis::metrics::MetricsStore,
) -> Vec<Box<dyn Feature>> {
    vec![
        Box::new(wanaku_feature_metrics::MetricsFeature::new(metrics_store.clone())),
        Box::new(wanaku_feature_intercept::InterceptFeature::new()),
        Box::new(wanaku_feature_mcp_metadata::McpMetadataFeature::new()),
        Box::new(wanaku_feature_evaluator::EvaluatorFeature::new()
            .with_metrics(metrics_store.clone())),
        Box::new(wanaku_feature_chat::ChatFeature::new(
            format!(
                "http://127.0.0.1:{}{}",
                wanaku_apis::config::ENV.inference_proxy_port(),
                wanaku_apis::config::ENV.inference_path_prefix,
            ),
            wanaku_apis::config::ENV.inference_tls_sni.clone(),
            wanaku_apis::config::ENV.inference_api_key.clone(),
        )),
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
