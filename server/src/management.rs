use async_trait::async_trait;
use http::Response;
use pingora_core::apps::http_app::ServeHttp;
use pingora_core::protocols::http::ServerSession;
use tracing::{info, warn};

use wanaku_praxis_apis::interactions::{InMemoryInteractionStore, InteractionStore};
use wanaku_praxis_apis::registry::{
    ForwardEntry, ForwardRegistry, InMemoryRegistry, NamespaceEntry, NamespaceRegistry,
    PromptEntry, PromptRegistry, ResourceEntry, ResourceRegistry, ServiceEntry, ServiceRegistry,
    ToolEntry, ToolRegistry, MCP_FORWARD_TYPE,
};

#[derive(rust_embed::Embed)]
#[folder = "../ui/admin/dist/"]
#[prefix = ""]
struct AdminUi;

const MAX_BODY_BYTES: usize = 1_048_576;

pub struct WanakuManagementService {
    registry: InMemoryRegistry,
    interactions: InMemoryInteractionStore,
    proxy: Option<crate::proxy::ClassicProxy>,
    ui_path: Option<std::path::PathBuf>,
}

impl WanakuManagementService {
    pub fn new(registry: InMemoryRegistry, interactions: InMemoryInteractionStore) -> Self {
        let proxy = crate::proxy::ClassicProxy::from_config();
        if proxy.is_some() {
            info!("Classic proxy enabled via WANAKU_CLASSIC_URL");
        }

        let ui_path = wanaku_praxis_apis::config::ENV.ui_path.clone();
        if let Some(p) = &ui_path {
            info!(path = %p.display(), "Admin UI serving enabled");
        }

        Self {
            registry,
            interactions,
            proxy,
            ui_path,
        }
    }
}

#[derive(Debug, PartialEq, Eq)]
enum ToolRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}

fn resolve_tool_route(method: &str, path: &str) -> ToolRoute {
    let suffix = match path.strip_prefix("/api/v1/tools") {
        Some(s) => s,
        None => return ToolRoute::NotFound,
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => ToolRoute::List,
        ("GET", Some(n)) => ToolRoute::GetByName(n.to_owned()),
        ("POST", None | Some("payloads")) => ToolRoute::Create,
        ("DELETE", Some(n)) => ToolRoute::Delete(n.to_owned()),
        _ => ToolRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
enum ResourceRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}

fn resolve_resource_route(method: &str, path: &str) -> ResourceRoute {
    let suffix = match path.strip_prefix("/api/v1/resources") {
        Some(s) => s,
        None => return ResourceRoute::NotFound,
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => ResourceRoute::List,
        ("GET", Some(n)) => ResourceRoute::GetByName(n.to_owned()),
        ("POST", None | Some("payloads")) => ResourceRoute::Create,
        ("DELETE", Some(n)) => ResourceRoute::Delete(n.to_owned()),
        _ => ResourceRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
enum PromptRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}

fn resolve_prompt_route(method: &str, path: &str) -> PromptRoute {
    let suffix = match path.strip_prefix("/api/v1/prompts") {
        Some(s) => s,
        None => return PromptRoute::NotFound,
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => PromptRoute::List,
        ("GET", Some(n)) => PromptRoute::GetByName(n.to_owned()),
        ("POST", None | Some("payloads")) => PromptRoute::Create,
        ("DELETE", Some(n)) => PromptRoute::Delete(n.to_owned()),
        _ => PromptRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
enum InteractionRoute {
    List,
    Clear,
    NotFound,
}

fn resolve_interaction_route(method: &str, path: &str) -> InteractionRoute {
    let suffix = match path.strip_prefix("/api/v1/interactions") {
        Some(s) => s,
        None => return InteractionRoute::NotFound,
    };

    if !suffix.is_empty() && suffix != "/" {
        return InteractionRoute::NotFound;
    }

    match method {
        "GET" => InteractionRoute::List,
        "DELETE" => InteractionRoute::Clear,
        _ => InteractionRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
enum ManagementRoute {
    Statistics,
    NotFound,
}

fn resolve_management_route(method: &str, path: &str) -> ManagementRoute {
    let suffix = match path.strip_prefix("/api/v1/management") {
        Some(s) => s,
        None => return ManagementRoute::NotFound,
    };

    match (method, suffix) {
        ("GET", "/statistics") => ManagementRoute::Statistics,
        _ => ManagementRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
enum CapabilityRoute {
    List,
    ToolsState,
    ResourcesState,
    FleetStatus,
    NotFound,
}

fn resolve_capability_route(method: &str, path: &str) -> CapabilityRoute {
    let suffix = match path.strip_prefix("/api/v1/capabilities") {
        Some(s) => s,
        None => return CapabilityRoute::NotFound,
    };

    if method != "GET" {
        return CapabilityRoute::NotFound;
    }

    match suffix {
        "" | "/" => CapabilityRoute::List,
        "/tools/state" => CapabilityRoute::ToolsState,
        "/resources/state" => CapabilityRoute::ResourcesState,
        "/fleet/status" => CapabilityRoute::FleetStatus,
        _ => CapabilityRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
enum ServiceRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}

fn resolve_service_route(method: &str, path: &str) -> ServiceRoute {
    let suffix = match path.strip_prefix("/api/v1/services") {
        Some(s) => s,
        None => return ServiceRoute::NotFound,
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => ServiceRoute::List,
        ("GET", Some(n)) => ServiceRoute::GetByName(n.to_owned()),
        ("POST", None | Some("payloads")) => ServiceRoute::Create,
        ("DELETE", Some(n)) => ServiceRoute::Delete(n.to_owned()),
        _ => ServiceRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
enum ForwardRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    Refresh(String),
    NotFound,
}

fn resolve_forward_route(method: &str, path: &str) -> ForwardRoute {
    let suffix = match path.strip_prefix("/api/v1/forwards") {
        Some(s) => s,
        None => return ForwardRoute::NotFound,
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => ForwardRoute::List,
        ("GET", Some(n)) => ForwardRoute::GetByName(n.to_owned()),
        ("POST", None) => ForwardRoute::Create,
        ("DELETE", Some(n)) if !n.contains('/') => ForwardRoute::Delete(n.to_owned()),
        ("POST", Some(n)) => {
            if let Some(name) = n.strip_suffix("/refreshes") {
                ForwardRoute::Refresh(name.to_owned())
            } else {
                ForwardRoute::NotFound
            }
        }
        _ => ForwardRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
enum NamespaceRoute {
    List,
    GetByName(String),
    Create,
    Update(String),
    Delete(String),
    NotFound,
}

fn resolve_namespace_route(method: &str, path: &str) -> NamespaceRoute {
    let suffix = match path.strip_prefix("/api/v1/namespaces") {
        Some(s) => s,
        None => return NamespaceRoute::NotFound,
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => NamespaceRoute::List,
        ("GET", Some(n)) => NamespaceRoute::GetByName(n.to_owned()),
        ("POST", None) => NamespaceRoute::Create,
        ("PUT", Some(n)) => NamespaceRoute::Update(n.to_owned()),
        ("DELETE", Some(n)) => NamespaceRoute::Delete(n.to_owned()),
        _ => NamespaceRoute::NotFound,
    }
}

#[async_trait]
impl ServeHttp for WanakuManagementService {
    async fn response(&self, http_session: &mut ServerSession) -> Response<Vec<u8>> {
        let path = http_session.req_header().uri.path().to_owned();
        let method = http_session.req_header().method.as_str().to_owned();

        if path == "/healthz" || path == "/health" {
            return json_ok(&serde_json::json!({"status": "ok"}));
        }

        if path == "/openapi.json" {
            let body = crate::openapi::openapi_json();
            return raw_json_response(body);
        }

        if path == "/" {
            return redirect_response("/admin/");
        }

        if path.starts_with("/admin") {
            return serve_ui(&self.ui_path, &path);
        }

        let mgmt_route = resolve_management_route(&method, &path);
        if mgmt_route != ManagementRoute::NotFound {
            return match mgmt_route {
                ManagementRoute::Statistics => handle_statistics(&self.registry),
                ManagementRoute::NotFound => json_err(404, "not found"),
            };
        }

        let capability_route = resolve_capability_route(&method, &path);
        if capability_route != CapabilityRoute::NotFound {
            return match capability_route {
                CapabilityRoute::List => handle_capability_list(&self.registry),
                CapabilityRoute::ToolsState => handle_capability_state(),
                CapabilityRoute::ResourcesState => handle_capability_state(),
                CapabilityRoute::FleetStatus => json_ok(&serde_json::json!({})),
                CapabilityRoute::NotFound => json_err(404, "not found"),
            };
        }

        tracing::debug!(%method, %path, "management API request");

        let tool_route = resolve_tool_route(&method, &path);
        if tool_route != ToolRoute::NotFound {
            return match tool_route {
                ToolRoute::List => handle_tool_list(&self.registry),
                ToolRoute::GetByName(name) => handle_tool_get(&self.registry, &name),
                ToolRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_tool_create(&self.registry, &body),
                    Err(resp) => resp,
                },
                ToolRoute::Delete(name) => handle_tool_delete(&self.registry, &name),
                ToolRoute::NotFound => json_err(404, "not found"),
            };
        }

        let resource_route = resolve_resource_route(&method, &path);
        if resource_route != ResourceRoute::NotFound {
            return match resource_route {
                ResourceRoute::List => handle_resource_list(&self.registry),
                ResourceRoute::GetByName(name) => handle_resource_get(&self.registry, &name),
                ResourceRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_resource_create(&self.registry, &body),
                    Err(resp) => resp,
                },
                ResourceRoute::Delete(name) => handle_resource_delete(&self.registry, &name),
                ResourceRoute::NotFound => json_err(404, "not found"),
            };
        }

        let prompt_route = resolve_prompt_route(&method, &path);
        if prompt_route != PromptRoute::NotFound {
            return match prompt_route {
                PromptRoute::List => handle_prompt_list(&self.registry),
                PromptRoute::GetByName(name) => handle_prompt_get(&self.registry, &name),
                PromptRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_prompt_create(&self.registry, &body),
                    Err(resp) => resp,
                },
                PromptRoute::Delete(name) => handle_prompt_delete(&self.registry, &name),
                PromptRoute::NotFound => json_err(404, "not found"),
            };
        }

        let ns_route = resolve_namespace_route(&method, &path);
        if ns_route != NamespaceRoute::NotFound {
            return match ns_route {
                NamespaceRoute::List => handle_namespace_list(&self.registry),
                NamespaceRoute::GetByName(name) => handle_namespace_get(&self.registry, &name),
                NamespaceRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_namespace_create(&self.registry, &body),
                    Err(resp) => resp,
                },
                NamespaceRoute::Update(id) => match read_body(http_session).await {
                    Ok(body) => handle_namespace_update(&self.registry, &id, &body),
                    Err(resp) => resp,
                },
                NamespaceRoute::Delete(name) => handle_namespace_delete(&self.registry, &name),
                NamespaceRoute::NotFound => json_err(404, "not found"),
            };
        }

        let service_route = resolve_service_route(&method, &path);
        if service_route != ServiceRoute::NotFound {
            return match service_route {
                ServiceRoute::List => handle_service_list(&self.registry),
                ServiceRoute::GetByName(name) => handle_service_get(&self.registry, &name),
                ServiceRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_service_create(&self.registry, &body),
                    Err(resp) => resp,
                },
                ServiceRoute::Delete(name) => handle_service_delete(&self.registry, &name),
                ServiceRoute::NotFound => json_err(404, "not found"),
            };
        }

        let interaction_route = resolve_interaction_route(&method, &path);
        if interaction_route != InteractionRoute::NotFound {
            return match interaction_route {
                InteractionRoute::List => {
                    let items = self.interactions.list();
                    json_ok(&serde_json::json!(items))
                }
                InteractionRoute::Clear => {
                    self.interactions.clear();
                    json_ok(&serde_json::json!({"cleared": true}))
                }
                InteractionRoute::NotFound => json_err(404, "not found"),
            };
        }

        let forward_route = resolve_forward_route(&method, &path);
        if forward_route != ForwardRoute::NotFound {
            return match forward_route {
                ForwardRoute::List => handle_forward_list(&self.registry),
                ForwardRoute::GetByName(name) => handle_forward_get(&self.registry, &name),
                ForwardRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_forward_create(&self.registry, &body).await,
                    Err(resp) => resp,
                },
                ForwardRoute::Delete(name) => handle_forward_delete(&self.registry, &name),
                ForwardRoute::Refresh(name) => handle_forward_refresh(&self.registry, &name).await,
                ForwardRoute::NotFound => json_err(404, "not found"),
            };
        }

        if crate::proxy::ClassicProxy::should_proxy(&path) {
            if let Some(proxy) = &self.proxy {
                let body = match read_body(http_session).await {
                    Ok(b) if b.is_empty() => None,
                    Ok(b) => Some(b),
                    Err(resp) => return resp,
                };
                return proxy.forward(&method, &path, body).await;
            }
            return json_err(503, "Classic backend not configured (set WANAKU_CLASSIC_URL)");
        }

        json_err(404, "not found")
    }
}

fn handle_tool_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let tools = registry.list_tools();
    json_ok(&serde_json::json!(tools))
}

fn handle_tool_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_tool(name) {
        Some(tool) => json_ok(&serde_json::json!(tool)),
        None => json_err(404, &format!("tool not found: {name}")),
    }
}

fn handle_tool_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, "tool create request body");
    let tool: ToolEntry = match serde_json::from_str(body) {
        Ok(t) => t,
        Err(e) => {
            warn!(error = %e, "invalid tool JSON");
            return json_err(400, &format!("invalid tool JSON: {e}"));
        }
    };

    let name = tool.name.clone();
    registry.register_tool(tool);
    info!(tool = %name, "registered tool via management API");
    match registry.get_tool(&name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(404, &format!("tool not found after registration: {name}")),
    }
}

fn handle_tool_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_tool(name) {
        info!(tool = %name, "removed tool via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("tool not found: {name}"))
    }
}

fn handle_resource_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let resources = registry.list_resources();
    json_ok(&serde_json::json!(resources))
}

fn handle_resource_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_resource(name) {
        Some(resource) => json_ok(&serde_json::json!(resource)),
        None => json_err(404, &format!("resource not found: {name}")),
    }
}

fn handle_resource_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, "resource create request body");
    let resource: ResourceEntry = match serde_json::from_str(body) {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "invalid resource JSON");
            return json_err(400, &format!("invalid resource JSON: {e}"));
        }
    };

    let name = resource.name.clone();
    registry.register_resource(resource);
    info!(resource = %name, "registered resource via management API");
    match registry.get_resource(&name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(404, &format!("resource not found after registration: {name}")),
    }
}

fn handle_resource_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_resource(name) {
        info!(resource = %name, "removed resource via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("resource not found: {name}"))
    }
}

fn handle_prompt_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let prompts = registry.list_prompts();
    json_ok(&serde_json::json!(prompts))
}

fn handle_prompt_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_prompt(name) {
        Some(prompt) => json_ok(&serde_json::json!(prompt)),
        None => json_err(404, &format!("prompt not found: {name}")),
    }
}

fn handle_prompt_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, "prompt create request body");
    let prompt: PromptEntry = match serde_json::from_str(body) {
        Ok(p) => p,
        Err(e) => {
            warn!(error = %e, "invalid prompt JSON");
            return json_err(400, &format!("invalid prompt JSON: {e}"));
        }
    };

    let name = prompt.name.clone();
    registry.register_prompt(prompt);
    info!(prompt = %name, "registered prompt via management API");
    match registry.get_prompt(&name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(404, &format!("prompt not found after registration: {name}")),
    }
}

fn handle_prompt_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_prompt(name) {
        info!(prompt = %name, "removed prompt via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("prompt not found: {name}"))
    }
}

fn handle_namespace_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let namespaces = registry.list_namespaces();
    json_ok(&serde_json::json!(namespaces))
}

fn handle_namespace_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_namespace(name) {
        Some(ns) => json_ok(&serde_json::json!(ns)),
        None => json_err(404, &format!("namespace not found: {name}")),
    }
}

fn handle_namespace_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
    let namespace: NamespaceEntry = match serde_json::from_str(body) {
        Ok(n) => n,
        Err(e) => {
            warn!(error = %e, "invalid namespace JSON");
            return json_err(400, &format!("invalid namespace JSON: {e}"));
        }
    };

    let name = namespace.name.clone();
    registry.register_namespace(namespace);
    info!(namespace = %name, "registered namespace via management API");
    match registry.get_namespace(&name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(404, &format!("namespace not found after registration: {name}")),
    }
}

fn handle_namespace_update(registry: &InMemoryRegistry, path_name: &str, body: &str) -> Response<Vec<u8>> {
    let mut namespace: NamespaceEntry = match serde_json::from_str(body) {
        Ok(n) => n,
        Err(e) => {
            warn!(error = %e, "invalid namespace JSON");
            return json_err(400, &format!("invalid namespace JSON: {e}"));
        }
    };

    namespace.name = path_name.to_owned();
    namespace.id = None;
    registry.register_namespace(namespace);
    info!(namespace = %path_name, "updated namespace via management API");
    match registry.get_namespace(path_name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(404, &format!("namespace not found after update: {path_name}")),
    }
}

fn handle_namespace_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_namespace(name) {
        info!(namespace = %name, "removed namespace via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("namespace not found: {name}"))
    }
}

fn handle_service_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let services = registry.list_services();
    json_ok(&serde_json::json!(services))
}

fn handle_service_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    let services: Vec<ServiceEntry> = registry
        .list_services()
        .into_iter()
        .filter(|s| s.name == name)
        .collect();

    if services.is_empty() {
        json_err(404, &format!("service not found: {name}"))
    } else {
        json_ok(&serde_json::json!(services))
    }
}

fn handle_service_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, "service create request body");
    let service: ServiceEntry = match serde_json::from_str(body) {
        Ok(s) => s,
        Err(e) => {
            warn!(error = %e, "invalid service JSON");
            return json_err(400, &format!("invalid service JSON: {e}"));
        }
    };

    let name = service.name.clone();
    let svc_type = service.service_type.clone();
    registry.register_service(service);
    info!(service = %name, service_type = %svc_type, "registered service via management API");
    match registry.get_service(&name, &svc_type) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(404, &format!("service not found after registration: {name}")),
    }
}

fn handle_service_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    let services: Vec<ServiceEntry> = registry
        .list_services()
        .into_iter()
        .filter(|s| s.name == name)
        .collect();

    if services.is_empty() {
        return json_err(404, &format!("service not found: {name}"));
    }

    let mut removed_count = 0;
    for svc in &services {
        if registry.remove_service(&svc.name, &svc.service_type) {
            removed_count += 1;
        }
    }

    info!(service = %name, count = removed_count, "removed service(s) via management API");
    json_ok(&serde_json::json!({"removed": name, "count": removed_count}))
}

fn handle_forward_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let forwards = registry.list_forwards();
    json_ok(&serde_json::json!(forwards))
}

fn handle_forward_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_forward(name) {
        Some(forward) => json_ok(&serde_json::json!(forward)),
        None => json_err(404, &format!("forward not found: {name}")),
    }
}

async fn handle_forward_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, "forward create request body");
    let forward: ForwardEntry = match serde_json::from_str(body) {
        Ok(f) => f,
        Err(e) => {
            warn!(error = %e, "invalid forward JSON");
            return json_err(400, &format!("invalid forward JSON: {e}"));
        }
    };

    info!(forward = %forward.name, address = %forward.address, "registered forward via management API");
    registry.register_forward(forward.clone());

    let count = discover_tools_from_forward(registry, &forward).await;

    json_ok(&serde_json::json!({
        "forward": &forward,
        "tools_discovered": count,
    }))
}

fn handle_forward_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    let forward = registry.get_forward(name);

    if !registry.remove_forward(name) {
        return json_err(404, &format!("forward not found: {name}"));
    }

    if let Some(fwd) = forward {
        remove_forwarded_tools(registry, &fwd.address);
    }

    info!(forward = %name, "removed forward via management API");
    json_ok(&serde_json::json!({"removed": name}))
}

async fn handle_forward_refresh(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    let forward = match registry.get_forward(name) {
        Some(f) => f,
        None => return json_err(404, &format!("forward not found: {name}")),
    };

    remove_forwarded_tools(registry, &forward.address);
    let count = discover_tools_from_forward(registry, &forward).await;

    info!(forward = %name, tools_discovered = count, "refreshed forward");
    json_ok(&serde_json::json!({"refreshed": name, "tools_discovered": count}))
}

pub async fn discover_tools_from_forward(registry: &InMemoryRegistry, forward: &ForwardEntry) -> usize {
    let tools = match wanaku_praxis_apis::mcp_client::list_tools(&forward.address).await {
        Ok(t) => t,
        Err(e) => {
            warn!(forward = %forward.name, error = %e, "failed to discover tools from forward");
            return 0;
        }
    };

    let namespace = forward.namespace.as_deref().unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);
    let mut count = 0;

    for tool_json in &tools {
        let name = match tool_json.get("name").and_then(|n| n.as_str()).map(str::trim) {
            Some(n) if !n.is_empty() => n,
            _ => {
                warn!(forward = %forward.name, "skipping forwarded tool with missing or empty name");
                continue;
            }
        };
        let description = tool_json
            .get("description")
            .and_then(|d| d.as_str())
            .unwrap_or_default();
        let input_schema = tool_json
            .get("inputSchema")
            .cloned()
            .unwrap_or(serde_json::json!({"type": "object"}));

        let tool = ToolEntry {
            name: name.to_owned(),
            description: description.to_owned(),
            uri: forward.address.clone(),
            type_: MCP_FORWARD_TYPE.to_owned(),
            input_schema,
            labels: std::collections::HashMap::new(),
            id: None,
            namespace: Some(namespace.to_owned()),
            configuration_uri: None,
            secrets_uri: None,
        };

        info!(tool = %name, forward = %forward.name, "discovered forwarded tool");
        registry.register_tool(tool);
        count += 1;
    }

    count
}

fn remove_forwarded_tools(registry: &InMemoryRegistry, address: &str) {
    let forwarded: Vec<String> = registry
        .list_tools()
        .iter()
        .filter(|t| t.is_mcp_forward() && t.uri == address)
        .map(|t| t.name.clone())
        .collect();

    registry.remove_tools_batch(&forwarded);
}

fn handle_capability_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let services = registry.list_services();
    let targets: Vec<serde_json::Value> = services
        .iter()
        .map(|s| {
            let (host, port) = s
                .address
                .rsplit_once(':')
                .map(|(h, p)| (h.to_owned(), p.parse::<u16>().unwrap_or(0)))
                .unwrap_or_else(|| (s.address.clone(), 0));

            serde_json::json!({
                "id": format!("{}:{}", s.name, s.service_type),
                "serviceName": s.name,
                "host": host,
                "port": port,
                "serviceType": s.service_type,
            })
        })
        .collect();
    json_ok(&serde_json::json!(targets))
}

fn handle_capability_state() -> Response<Vec<u8>> {
    let empty: std::collections::HashMap<String, Vec<serde_json::Value>> =
        std::collections::HashMap::new();
    json_ok(&serde_json::json!(empty))
}

fn handle_statistics(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let tools_count = registry.tool_count() as i64;
    let resources_count = registry.resource_count() as i64;
    let prompts_count = registry.prompt_count() as i64;
    let forwards_count = registry.list_forwards().len() as i64;

    json_ok(&serde_json::json!({
        "toolsCount": tools_count,
        "resourcesCount": resources_count,
        "promptsCount": prompts_count,
        "forwardsCount": forwards_count,
        "dataStoresCount": 0,
        "toolCapabilities": {
            "total": 0,
            "healthy": 0,
            "unhealthy": 0,
            "down": 0,
            "pending": 0
        },
        "resourceCapabilities": {
            "total": 0,
            "healthy": 0,
            "unhealthy": 0,
            "down": 0,
            "pending": 0
        }
    }))
}

#[expect(clippy::expect_used, reason = "valid static response")]
fn redirect_response(location: &str) -> Response<Vec<u8>> {
    Response::builder()
        .status(301)
        .header("Location", location)
        .body(Vec::new())
        .expect("valid redirect")
}

#[expect(clippy::expect_used, reason = "valid static response")]
fn raw_json_response(body: Vec<u8>) -> Response<Vec<u8>> {
    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid json response")
}

#[expect(clippy::expect_used, reason = "valid static response")]
fn json_ok(data: &serde_json::Value) -> Response<Vec<u8>> {
    let wrapper = serde_json::json!({
        "data": data,
        "error": null,
    });
    let body = serde_json::to_vec(&wrapper).unwrap_or_default();
    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid json response")
}

fn json_err(status: u16, message: &str) -> Response<Vec<u8>> {
    crate::http_response::json_err(status, message)
}

async fn read_body(session: &mut ServerSession) -> Result<String, Response<Vec<u8>>> {
    let mut buf = Vec::new();
    loop {
        match session.read_request_body().await {
            Ok(Some(chunk)) => {
                if buf.len() + chunk.len() > MAX_BODY_BYTES {
                    warn!(limit = MAX_BODY_BYTES, "management request body exceeded size limit");
                    return Err(json_err(413, "request body too large"));
                }
                buf.extend_from_slice(&chunk);
            }
            Ok(None) => break,
            Err(e) => {
                warn!(error = %e, "management request body read failed");
                return Err(json_err(502, "request body read failed"));
            }
        }
    }
    String::from_utf8(buf).map_err(|_| json_err(400, "request body is not valid UTF-8"))
}

#[expect(clippy::expect_used, reason = "valid static response")]
fn serve_ui(ui_override: &Option<std::path::PathBuf>, request_path: &str) -> Response<Vec<u8>> {
    let relative = request_path
        .strip_prefix("/admin")
        .unwrap_or("")
        .trim_start_matches('/');

    let asset_path = if relative.is_empty() {
        "index.html"
    } else {
        relative
    };

    if let Some(ui_root) = ui_override {
        return serve_from_filesystem(ui_root, relative);
    }

    if let Some(file) = AdminUi::get(asset_path) {
        let content_type = mime_for_path(asset_path);
        return Response::builder()
            .status(200)
            .header("Content-Type", content_type)
            .header("Content-Length", file.data.len())
            .body(file.data.into_owned())
            .expect("valid static response");
    }

    if !relative.contains('.') {
        if let Some(index) = AdminUi::get("index.html") {
            return Response::builder()
                .status(200)
                .header("Content-Type", "text/html; charset=utf-8")
                .header("Content-Length", index.data.len())
                .body(index.data.into_owned())
                .expect("valid static response");
        }
    }

    json_err(404, "file not found")
}

#[expect(clippy::expect_used, reason = "valid static response")]
fn serve_from_filesystem(ui_root: &std::path::Path, relative: &str) -> Response<Vec<u8>> {
    let file_path = if relative.is_empty() {
        ui_root.join("index.html")
    } else {
        ui_root.join(relative)
    };

    let canonical = match file_path.canonicalize() {
        Ok(p) => p,
        Err(_) => {
            if !relative.contains('.') {
                if let Ok(index) = std::fs::read(ui_root.join("index.html")) {
                    return Response::builder()
                        .status(200)
                        .header("Content-Type", "text/html; charset=utf-8")
                        .header("Content-Length", index.len())
                        .body(index)
                        .expect("valid static response");
                }
            }
            return json_err(404, "file not found");
        }
    };

    let canonical_root = match ui_root.canonicalize() {
        Ok(r) => r,
        Err(_) => return json_err(500, "UI root path not found"),
    };

    if !canonical.starts_with(&canonical_root) {
        return json_err(403, "forbidden");
    }

    let body = match std::fs::read(&canonical) {
        Ok(b) => b,
        Err(_) => return json_err(404, "file not found"),
    };

    let content_type = mime_for_path(canonical.to_str().unwrap_or(""));

    Response::builder()
        .status(200)
        .header("Content-Type", content_type)
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid static response")
}

fn mime_for_path(path: &str) -> &'static str {
    match path.rsplit('.').next() {
        Some("html") => "text/html; charset=utf-8",
        Some("js") | Some("mjs") => "application/javascript",
        Some("css") => "text/css",
        Some("json") => "application/json",
        Some("svg") => "image/svg+xml",
        Some("png") => "image/png",
        Some("ico") => "image/x-icon",
        Some("woff2") => "font/woff2",
        Some("woff") => "font/woff",
        _ => "application/octet-stream",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn route_list() {
        assert_eq!(resolve_tool_route("GET", "/api/v1/tools"), ToolRoute::List);
    }

    #[test]
    fn route_get_by_name() {
        assert_eq!(
            resolve_tool_route("GET", "/api/v1/tools/my-tool"),
            ToolRoute::GetByName("my-tool".to_owned())
        );
    }

    #[test]
    fn route_create() {
        assert_eq!(resolve_tool_route("POST", "/api/v1/tools"), ToolRoute::Create);
    }

    #[test]
    fn route_delete() {
        assert_eq!(
            resolve_tool_route("DELETE", "/api/v1/tools/my-tool"),
            ToolRoute::Delete("my-tool".to_owned())
        );
    }

    #[test]
    fn route_unknown_path() {
        assert_eq!(resolve_tool_route("GET", "/api/v1/other"), ToolRoute::NotFound);
    }

    #[test]
    fn route_delete_without_name() {
        assert_eq!(resolve_tool_route("DELETE", "/api/v1/tools"), ToolRoute::NotFound);
    }

    #[test]
    fn resource_route_list() {
        assert_eq!(resolve_resource_route("GET", "/api/v1/resources"), ResourceRoute::List);
    }

    #[test]
    fn resource_route_get_by_name() {
        assert_eq!(
            resolve_resource_route("GET", "/api/v1/resources/my-res"),
            ResourceRoute::GetByName("my-res".to_owned())
        );
    }

    #[test]
    fn resource_route_create() {
        assert_eq!(resolve_resource_route("POST", "/api/v1/resources"), ResourceRoute::Create);
    }

    #[test]
    fn resource_route_create_payloads() {
        assert_eq!(resolve_resource_route("POST", "/api/v1/resources/payloads"), ResourceRoute::Create);
    }

    #[test]
    fn resource_route_delete() {
        assert_eq!(
            resolve_resource_route("DELETE", "/api/v1/resources/my-res"),
            ResourceRoute::Delete("my-res".to_owned())
        );
    }
}
