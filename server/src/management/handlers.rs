use http::Response;
use tracing::{info, warn};

use wanaku_praxis_apis::registry::{
    ForwardEntry, ForwardRegistry, InMemoryRegistry, NamespaceEntry, NamespaceRegistry,
    PromptEntry, PromptRegistry, ResourceEntry, ResourceRegistry, ServiceEntry, ServiceRegistry,
    ToolEntry, ToolRegistry, MCP_FORWARD_TYPE,
};
use wanaku_praxis_apis::safety::{SafetyConfig, SafetyState};

use super::response::{json_ok, json_err};

pub(super) fn handle_tool_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let tools = registry.list_tools();
    json_ok(&serde_json::json!(tools))
}

pub(super) fn handle_tool_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_tool(name) {
        Some(tool) => json_ok(&serde_json::json!(tool)),
        None => json_err(404, &format!("tool not found: {name}")),
    }
}

pub(super) fn handle_tool_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
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

pub(super) fn handle_tool_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_tool(name) {
        info!(tool = %name, "removed tool via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("tool not found: {name}"))
    }
}

pub(super) fn handle_resource_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let resources = registry.list_resources();
    json_ok(&serde_json::json!(resources))
}

pub(super) fn handle_resource_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_resource(name) {
        Some(resource) => json_ok(&serde_json::json!(resource)),
        None => json_err(404, &format!("resource not found: {name}")),
    }
}

pub(super) fn handle_resource_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
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

pub(super) fn handle_resource_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_resource(name) {
        info!(resource = %name, "removed resource via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("resource not found: {name}"))
    }
}

pub(super) fn handle_prompt_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let prompts = registry.list_prompts();
    json_ok(&serde_json::json!(prompts))
}

pub(super) fn handle_prompt_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_prompt(name) {
        Some(prompt) => json_ok(&serde_json::json!(prompt)),
        None => json_err(404, &format!("prompt not found: {name}")),
    }
}

pub(super) fn handle_prompt_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
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

pub(super) fn handle_prompt_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_prompt(name) {
        info!(prompt = %name, "removed prompt via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("prompt not found: {name}"))
    }
}

pub(super) fn handle_namespace_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let namespaces = registry.list_namespaces();
    json_ok(&serde_json::json!(namespaces))
}

pub(super) fn handle_namespace_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_namespace(name) {
        Some(ns) => json_ok(&serde_json::json!(ns)),
        None => json_err(404, &format!("namespace not found: {name}")),
    }
}

pub(super) fn handle_namespace_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
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

pub(super) fn handle_namespace_update(registry: &InMemoryRegistry, path_name: &str, body: &str) -> Response<Vec<u8>> {
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

pub(super) fn handle_namespace_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_namespace(name) {
        info!(namespace = %name, "removed namespace via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("namespace not found: {name}"))
    }
}

pub(super) fn handle_service_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let services = registry.list_services();
    json_ok(&serde_json::json!(services))
}

pub(super) fn handle_service_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
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

pub(super) fn handle_service_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
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

pub(super) fn handle_service_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
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

pub(super) fn handle_forward_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let forwards = registry.list_forwards();
    json_ok(&serde_json::json!(forwards))
}

pub(super) fn handle_forward_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_forward(name) {
        Some(forward) => json_ok(&serde_json::json!(forward)),
        None => json_err(404, &format!("forward not found: {name}")),
    }
}

pub(super) async fn handle_forward_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
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

pub(super) fn handle_forward_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
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

pub(super) async fn handle_forward_refresh(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
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
            skip_safety_check: false,
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

pub(super) fn handle_capability_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
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

pub(super) fn handle_capability_state() -> Response<Vec<u8>> {
    let empty: std::collections::HashMap<String, Vec<serde_json::Value>> =
        std::collections::HashMap::new();
    json_ok(&serde_json::json!(empty))
}

pub(super) fn handle_statistics(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
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

pub(super) fn handle_safety_get(state: &SafetyState) -> Response<Vec<u8>> {
    json_ok(&serde_json::json!({
        "data": state.current_config(),
        "error": serde_json::Value::Null,
    }))
}

pub(super) fn handle_safety_update(state: &SafetyState, body: &str) -> Response<Vec<u8>> {
    let config: SafetyConfig = match serde_json::from_str(body) {
        Ok(c) => c,
        Err(e) => return json_err(400, &format!("invalid safety config: {e}")),
    };

    info!(model = %config.llm_model, url = %config.llm_url, "safety classifier updated via management API");
    state.configure(config.clone());

    json_ok(&serde_json::json!({
        "data": config,
        "error": serde_json::Value::Null,
    }))
}

pub(super) fn handle_safety_delete(state: &SafetyState) -> Response<Vec<u8>> {
    state.disable();
    info!("safety classifier disabled via management API");
    json_ok(&serde_json::json!({
        "data": serde_json::Value::Null,
        "error": serde_json::Value::Null,
    }))
}
