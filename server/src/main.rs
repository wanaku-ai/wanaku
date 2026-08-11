#![deny(unsafe_code)]

#[cfg(unix)]
#[global_allocator]
static GLOBAL: tikv_jemallocator::Jemalloc = tikv_jemallocator::Jemalloc;

use clap::Parser;
use praxis_core::config::ProtocolKind;
use praxis_core::health::build_health_registry;
use praxis_core::PingoraServerRuntime;
use praxis_protocol::Protocol as _;
use praxis_protocol::http::PingoraHttp;
use tracing::info;

use wanaku_praxis_apis::feature::Feature;
use wanaku_praxis_apis::grpc::GrpcPool;
use wanaku_praxis_apis::persistence::FilePersistence;
use wanaku_praxis_apis::registry::{
    ForwardEntry, ForwardRegistry, InMemoryRegistry, ServiceEntry, ServiceRegistry, ToolEntry,
    ToolRegistry,
};

fn main() {
    let args = ServerArgs::parse();
    let config = wanaku_praxis::load_config(args.praxis_config.as_deref())
        .unwrap_or_else(|e| fatal(&e));

    praxis_core::logging::init_tracing(&config)
        .unwrap_or_else(|e| fatal(&e));

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

    let features: Vec<Box<dyn Feature>> = vec![
        Box::new(wanaku_feature_intercept::InterceptFeature::new()),
        Box::new(wanaku_feature_mcp_metadata::McpMetadataFeature::new()),
        Box::new(wanaku_feature_evaluator::EvaluatorFeature::new()),
        Box::new(wanaku_feature_chat::ChatFeature::new(
            format!(
                "http://127.0.0.1:{}{}",
                wanaku_praxis_apis::config::ENV.inference_proxy_port(),
                wanaku_praxis_apis::config::ENV.inference_path_prefix,
            ),
            wanaku_praxis_apis::config::ENV.inference_tls_sni.clone(),
            wanaku_praxis_apis::config::ENV.inference_api_key.clone(),
        )),
    ];

    let wanaku_config = load_wanaku_yaml(&args.wanaku_config);
    if let Some(ref yaml) = wanaku_config {
        load_core_config(yaml, &wanaku_registry);
        for feature in &features {
            feature.load_yaml_config(yaml);
        }
    }

    for feature in &features {
        feature.load_env_config();
    }

    let grpc_pool = GrpcPool::new();

    let mut filter_registry = wanaku_praxis::build_full_registry();
    for feature in &features {
        feature.register_filters(&mut filter_registry);
    }

    let health_registry = build_health_registry(&config.clusters);
    let kv_stores = praxis_core::kv::KvStoreRegistry::new();

    let mgmt_registry = wanaku_registry.clone();

    info!("building wanaku pipelines");
    let pipelines = wanaku_praxis::pipelines::resolve_pipelines(
        &config,
        &filter_registry,
        &health_registry,
        &kv_stores,
        wanaku_registry,
        grpc_pool,
        &features,
    )
    .unwrap_or_else(|e| fatal(&e));

    info!("initializing server");
    let mut server = PingoraServerRuntime::new(&config);

    if config
        .listeners
        .iter()
        .any(|l| l.protocol == ProtocolKind::Http)
    {
        let _cert_shutdowns = Box::new(PingoraHttp)
            .register(&mut server, &config, &pipelines)
            .unwrap_or_else(|e| fatal(&e));
    }

    if let Some(admin_addr) = &config.admin.address {
        praxis_protocol::http::pingora::health::add_admin_endpoints_to_pingora_server(
            server.server_mut(),
            admin_addr,
            Some(health_registry),
            Some(kv_stores),
            config.admin.verbose,
        );
    }

    let mgmt_addr = &wanaku_praxis_apis::config::ENV.mgmt_listen;
    let mgmt = wanaku_praxis::management::WanakuManagementService::new(
        mgmt_registry,
        features,
    );
    let mut mgmt_service = pingora_core::services::listening::Service::new(
        "wanaku-management".to_owned(),
        mgmt,
    );
    mgmt_service.add_tcp(&mgmt_addr);
    server.server_mut().add_service(mgmt_service);
    info!(address = %mgmt_addr, "management API enabled");

    info!("starting wanaku-praxis server");
    server.run()
}

#[derive(Debug, Parser)]
#[command(version, about)]
struct ServerArgs {
    /// Praxis pipeline configuration file. Uses the embedded configuration when omitted.
    #[arg(long, value_name = "PATH")]
    praxis_config: Option<String>,

    /// Wanaku bootstrap configuration file.
    #[arg(long, value_name = "PATH", default_value = "wanaku.yaml")]
    wanaku_config: String,
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

fn load_core_config(config: &serde_yaml::Value, registry: &InMemoryRegistry) {
    if let Some(tools) = config.get("tools").and_then(|t| t.as_sequence()) {
        for tool_value in tools {
            if let Ok(tool) = serde_yaml::from_value::<ToolEntry>(tool_value.clone()) {
                info!(tool = %tool.name, "registered tool from config");
                registry.register_tool(tool);
            }
        }
    }

    if let Some(services) = config.get("services").and_then(|s| s.as_sequence()) {
        for svc_value in services {
            if let Ok(svc) = serde_yaml::from_value::<ServiceEntry>(svc_value.clone()) {
                info!(service = %svc.name, address = %svc.address, "registered service from config");
                registry.register_service(svc);
            }
        }
    }

    let mut forwards = Vec::new();
    if let Some(fwd_list) = config.get("forwards").and_then(|f| f.as_sequence()) {
        for fwd_value in fwd_list {
            if let Ok(fwd) = serde_yaml::from_value::<ForwardEntry>(fwd_value.clone()) {
                info!(forward = %fwd.name, address = %fwd.address, "registered forward from config");
                registry.register_forward(fwd.clone());
                forwards.push(fwd);
            }
        }
    }

    if !forwards.is_empty() {
        let reg = registry.clone();
        let handle = std::thread::spawn(move || {
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
                    info!(forward = %fwd.name, address = %fwd.address, "discovering tools from forward");
                    let count =
                        wanaku_praxis::management::discover_tools_from_forward(&reg, fwd).await;
                    info!(forward = %fwd.name, tools_discovered = count, "forward discovery complete");
                }
            });
        });

        if let Err(e) = handle.join() {
            tracing::error!("forward discovery thread panicked: {e:?}");
        }
    }
}

#[expect(clippy::print_stderr, clippy::exit, reason = "fatal error before runtime is available")]
fn fatal(err: &dyn std::fmt::Display) -> ! {
    eprintln!("fatal: {err}");
    std::process::exit(1)
}
