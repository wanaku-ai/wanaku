#![deny(unsafe_code)]

#[cfg(unix)]
#[global_allocator]
static GLOBAL: tikv_jemallocator::Jemalloc = tikv_jemallocator::Jemalloc;

use praxis_core::config::ProtocolKind;
use praxis_core::health::build_health_registry;
use praxis_core::PingoraServerRuntime;
use praxis_protocol::Protocol as _;
use praxis_protocol::http::PingoraHttp;
use tracing::info;

use wanaku_praxis_apis::grpc::GrpcPool;
use wanaku_praxis_apis::interactions::InMemoryInteractionStore;
use wanaku_praxis_apis::persistence::FilePersistence;
use wanaku_praxis_apis::registry::{
    InMemoryRegistry, ServiceEntry, ServiceRegistry, ToolEntry, ToolRegistry,
};

fn main() {
    let config_path = std::env::args().nth(1);
    let config = wanaku_praxis::load_config(config_path.as_deref())
        .unwrap_or_else(|e| fatal(&e));

    praxis_core::logging::init_tracing(&config)
        .unwrap_or_else(|e| fatal(&e));

    let wanaku_config_path = std::env::args()
        .nth(2)
        .unwrap_or_else(|| "wanaku.yaml".to_owned());

    let wanaku_registry = match FilePersistence::from_env() {
        Some(backend) => {
            info!("file-based persistence enabled");
            let registry = InMemoryRegistry::with_persistence(backend);
            registry.load_persisted();
            registry
        }
        None => InMemoryRegistry::new(),
    };
    load_wanaku_config(&wanaku_config_path, &wanaku_registry);
    let grpc_pool = GrpcPool::new();
    let interaction_store = InMemoryInteractionStore::new(1000);

    let filter_registry = wanaku_praxis::build_full_registry();
    let health_registry = build_health_registry(&config.clusters);
    let kv_stores = praxis_core::kv::KvStoreRegistry::new();

    let mgmt_registry = wanaku_registry.clone();
    let mgmt_interactions = interaction_store.clone();

    info!("building wanaku pipelines");
    let pipelines = wanaku_praxis::pipelines::resolve_pipelines(
        &config,
        &filter_registry,
        &health_registry,
        &kv_stores,
        wanaku_registry,
        grpc_pool,
        interaction_store,
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

    let mgmt_addr = std::env::var("WANAKU_MGMT_LISTEN")
        .unwrap_or_else(|_| "0.0.0.0:9090".to_owned());
    let mgmt = wanaku_praxis::management::WanakuManagementService::new(mgmt_registry, mgmt_interactions);
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

fn load_wanaku_config(path: &str, registry: &InMemoryRegistry) {
    let content = match std::fs::read_to_string(path) {
        Ok(c) => c,
        Err(e) => {
            tracing::warn!(path = %path, error = %e, "wanaku config not found, starting with empty registry");
            return;
        }
    };

    let config: serde_yaml::Value = match serde_yaml::from_str(&content) {
        Ok(c) => c,
        Err(e) => {
            tracing::warn!(path = %path, error = %e, "failed to parse wanaku config");
            return;
        }
    };

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
}

#[expect(clippy::print_stderr, clippy::exit, reason = "fatal error before runtime is available")]
fn fatal(err: &dyn std::fmt::Display) -> ! {
    eprintln!("fatal: {err}");
    std::process::exit(1)
}
