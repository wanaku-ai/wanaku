use http::Response;
use tracing::{info, warn};

use wanaku_praxis_apis::registry::{
    ForwardEntry, ForwardRegistry, InMemoryRegistry, NamespaceEntry, NamespaceRegistry,
    PromptEntry, PromptRegistry, ResourceEntry, ResourceRegistry, ServiceEntry, ServiceRegistry,
    ToolEntry, ToolRegistry, MCP_FORWARD_TYPE,
};
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
    let mut tool: ToolEntry = match serde_json::from_str(body) {
        Ok(t) => t,
        Err(e) => {
            warn!(error = %e, "invalid tool JSON");
            return json_err(400, &format!("invalid tool JSON: {e}"));
        }
    };

    tool.name = tool.name.trim().to_owned();
    if tool.name.is_empty() {
        warn!("rejected tool with empty name");
        return json_err(400, "tool name must not be empty");
    }

    let name = tool.name.clone();
    registry.register_tool(tool);
    info!(tool = %name, "registered tool via management API");
    match registry.get_tool(&name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(404, &format!("tool not found after registration: {name}")),
    }
}

pub(super) fn handle_tool_update(registry: &InMemoryRegistry, path_name: &str, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, name = %path_name, "tool update request body");
    let mut tool: ToolEntry = match serde_json::from_str(body) {
        Ok(t) => t,
        Err(e) => {
            warn!(error = %e, "invalid tool JSON");
            return json_err(400, &format!("invalid tool JSON: {e}"));
        }
    };

    let new_name = tool.name.trim().to_owned();
    if !new_name.is_empty() && new_name != path_name {
        registry.remove_tool(path_name);
        tool.name = new_name;
    } else {
        tool.name = path_name.to_owned();
    }

    let name = tool.name.clone();
    registry.register_tool(tool);
    info!(tool = %name, "updated tool via management API");
    match registry.get_tool(&name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(404, &format!("tool not found after update: {name}")),
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

pub(super) fn handle_resource_update(registry: &InMemoryRegistry, path_name: &str, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, name = %path_name, "resource update request body");
    let mut resource: ResourceEntry = match serde_json::from_str(body) {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "invalid resource JSON");
            return json_err(400, &format!("invalid resource JSON: {e}"));
        }
    };

    let new_name = resource.name.trim().to_owned();
    if !new_name.is_empty() && new_name != path_name {
        registry.remove_resource(path_name);
        resource.name = new_name;
    } else {
        resource.name = path_name.to_owned();
    }

    let name = resource.name.clone();
    registry.register_resource(resource);
    info!(resource = %name, "updated resource via management API");
    match registry.get_resource(&name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(404, &format!("resource not found after update: {name}")),
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

#[cfg(test)]
mod forward_helpers_tests {
    use super::*;
    use wanaku_praxis_apis::registry::{InMemoryRegistry, ToolEntry, ToolRegistry};
    use std::collections::HashMap;

    #[test]
    fn remove_forwarded_tools_clears_matching_tools() {
        let registry = InMemoryRegistry::new();
        let fwd_addr = "http://remote:8080";

        registry.register_tool(ToolEntry {
            name: "fwd-tool".to_owned(),
            description: "forwarded".to_owned(),
            uri: fwd_addr.to_owned(),
            type_: MCP_FORWARD_TYPE.to_owned(),
            input_schema: serde_json::json!({"type": "object"}),
            labels: HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        });
        registry.register_tool(ToolEntry {
            name: "local-tool".to_owned(),
            description: "local".to_owned(),
            uri: "echo://test".to_owned(),
            type_: "echo".to_owned(),
            input_schema: serde_json::json!({"type": "object"}),
            labels: HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        });

        remove_forwarded_tools(&registry, fwd_addr);

        assert!(registry.get_tool("fwd-tool").is_none());
        assert!(registry.get_tool("local-tool").is_some());
    }
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

#[cfg(test)]
mod tests {
    use http::Response;
    use wanaku_praxis_apis::registry::{
        ForwardEntry, ForwardRegistry, InMemoryRegistry, ServiceEntry, ServiceRegistry,
        ToolRegistry,
    };

    use super::{
        handle_capability_list, handle_capability_state,
        handle_forward_delete, handle_forward_get, handle_forward_list,
        handle_namespace_create, handle_namespace_delete, handle_namespace_get,
        handle_namespace_list, handle_namespace_update,
        handle_prompt_create, handle_prompt_delete, handle_prompt_get, handle_prompt_list,
        handle_resource_create, handle_resource_delete, handle_resource_get, handle_resource_list,
        handle_resource_update,
        handle_service_create, handle_service_delete, handle_service_get, handle_service_list,
        handle_statistics,
        handle_tool_create, handle_tool_delete, handle_tool_get, handle_tool_list,
        handle_tool_update,
    };

    fn parse_body(resp: &Response<Vec<u8>>) -> serde_json::Value {
        serde_json::from_slice(resp.body()).unwrap_or_default()
    }

    fn data_field(resp: &Response<Vec<u8>>) -> serde_json::Value {
        let body = parse_body(resp);
        body.get("data").cloned().unwrap_or_default()
    }

    fn error_message(resp: &Response<Vec<u8>>) -> Option<String> {
        let body = parse_body(resp);
        body.get("error")
            .and_then(|v| v.as_str())
            .map(|s| s.to_owned())
    }

    // ---- Tool handlers ----

    #[test]
    fn tool_create_and_get_roundtrip() {
        let registry = InMemoryRegistry::new();
        let body = r#"{
            "name":"my-tool",
            "description":"desc",
            "uri":"echo://t",
            "type":"echo",
            "input_schema":{"type":"object"}
        }"#;

        let create_resp = handle_tool_create(&registry, body);
        assert_eq!(create_resp.status(), 200);

        let get_resp = handle_tool_get(&registry, "my-tool");
        assert_eq!(get_resp.status(), 200);

        let data = data_field(&get_resp);
        assert_eq!(data.get("name").and_then(|v| v.as_str()), Some("my-tool"));
        assert_eq!(
            data.get("description").and_then(|v| v.as_str()),
            Some("desc")
        );
    }

    #[test]
    fn tool_list_empty_then_populated() {
        let registry = InMemoryRegistry::new();
        let resp = handle_tool_list(&registry);
        assert_eq!(resp.status(), 200);
        assert_eq!(data_field(&resp).as_array().map(|a| a.len()), Some(0));

        let body =
            r#"{"name":"t1","description":"","uri":"u","type":"x","input_schema":{"type":"object"}}"#;
        handle_tool_create(&registry, body);

        let resp = handle_tool_list(&registry);
        assert_eq!(data_field(&resp).as_array().map(|a| a.len()), Some(1));
    }

    #[test]
    fn tool_get_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        let resp = handle_tool_get(&registry, "no-such-tool");
        assert_eq!(resp.status(), 404);
    }

    #[test]
    fn tool_delete_existing() {
        let registry = InMemoryRegistry::new();
        let body =
            r#"{"name":"to-delete","description":"","uri":"u","type":"x","input_schema":{"type":"object"}}"#;
        handle_tool_create(&registry, body);

        let resp = handle_tool_delete(&registry, "to-delete");
        assert_eq!(resp.status(), 200);

        assert_eq!(handle_tool_get(&registry, "to-delete").status(), 404);
    }

    #[test]
    fn tool_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_tool_delete(&registry, "ghost").status(), 404);
    }

    #[test]
    fn tool_create_invalid_json_returns_400() {
        let registry = InMemoryRegistry::new();
        let resp = handle_tool_create(&registry, "not valid json");
        assert_eq!(resp.status(), 400);
        assert!(error_message(&resp).is_some());
    }

    #[test]
    fn tool_create_empty_name_returns_400() {
        let registry = InMemoryRegistry::new();
        let resp = handle_tool_create(
            &registry,
            r#"{"name":"","description":"d","uri":"u","type":"x","input_schema":{"type":"object"}}"#,
        );
        assert_eq!(resp.status(), 400);
        assert!(error_message(&resp)
            .map(|m| m.contains("empty"))
            .unwrap_or(false));
    }

    #[test]
    fn tool_create_whitespace_name_returns_400() {
        let registry = InMemoryRegistry::new();
        let resp = handle_tool_create(
            &registry,
            r#"{"name":"  ","description":"d","uri":"u","type":"x","input_schema":{"type":"object"}}"#,
        );
        assert_eq!(resp.status(), 400);
        assert!(error_message(&resp)
            .map(|m| m.contains("empty"))
            .unwrap_or(false));
    }

    #[test]
    fn tool_create_with_input_schema_alias() {
        let registry = InMemoryRegistry::new();
        let body = r#"{
            "name":"alias-tool",
            "description":"",
            "uri":"u",
            "type":"x",
            "inputSchema":{"type":"object","properties":{"msg":{"type":"string"}}}
        }"#;

        let resp = handle_tool_create(&registry, body);
        assert_eq!(resp.status(), 200);
        assert!(registry.get_tool("alias-tool").is_some());
    }

    #[test]
    fn tool_update_changes_description() {
        let registry = InMemoryRegistry::new();
        let body =
            r#"{"name":"upd","description":"old","uri":"u","type":"x","input_schema":{"type":"object"}}"#;
        handle_tool_create(&registry, body);

        let update_body =
            r#"{"name":"upd","description":"new","uri":"u2","type":"y","input_schema":{"type":"object"}}"#;
        let resp = handle_tool_update(&registry, "upd", update_body);
        assert_eq!(resp.status(), 200);

        let data = data_field(&handle_tool_get(&registry, "upd"));
        assert_eq!(
            data.get("description").and_then(|v| v.as_str()),
            Some("new")
        );
        assert_eq!(data.get("uri").and_then(|v| v.as_str()), Some("u2"));
    }

    #[test]
    fn tool_update_rename_removes_old_entry() {
        let registry = InMemoryRegistry::new();
        let body =
            r#"{"name":"old-name","description":"d","uri":"u","type":"x","input_schema":{"type":"object"}}"#;
        handle_tool_create(&registry, body);

        let update_body =
            r#"{"name":"new-name","description":"d","uri":"u","type":"x","input_schema":{"type":"object"}}"#;
        let resp = handle_tool_update(&registry, "old-name", update_body);
        assert_eq!(resp.status(), 200);

        assert_eq!(handle_tool_get(&registry, "old-name").status(), 404);
        assert_eq!(handle_tool_get(&registry, "new-name").status(), 200);
    }

    #[test]
    fn tool_update_invalid_json_returns_400() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            handle_tool_update(&registry, "t", "???").status(),
            400
        );
    }

    // ---- Resource handlers ----

    #[test]
    fn resource_create_and_get_roundtrip() {
        let registry = InMemoryRegistry::new();
        let body = r#"{
            "name":"my-res",
            "description":"d",
            "location":"/tmp/f",
            "type":"file",
            "mime_type":"text/plain"
        }"#;

        let create_resp = handle_resource_create(&registry, body);
        assert_eq!(create_resp.status(), 200);

        let get_resp = handle_resource_get(&registry, "my-res");
        assert_eq!(get_resp.status(), 200);

        let data = data_field(&get_resp);
        assert_eq!(data.get("name").and_then(|v| v.as_str()), Some("my-res"));
        assert_eq!(
            data.get("location").and_then(|v| v.as_str()),
            Some("/tmp/f")
        );
    }

    #[test]
    fn resource_list_empty_then_populated() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            data_field(&handle_resource_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(0)
        );

        handle_resource_create(&registry, r#"{"name":"r1","location":"/x","type":"file"}"#);
        assert_eq!(
            data_field(&handle_resource_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(1)
        );
    }

    #[test]
    fn resource_get_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_resource_get(&registry, "nope").status(), 404);
    }

    #[test]
    fn resource_delete_existing() {
        let registry = InMemoryRegistry::new();
        handle_resource_create(&registry, r#"{"name":"del-res","location":"/x","type":"file"}"#);

        assert_eq!(handle_resource_delete(&registry, "del-res").status(), 200);
        assert_eq!(handle_resource_get(&registry, "del-res").status(), 404);
    }

    #[test]
    fn resource_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_resource_delete(&registry, "nope").status(), 404);
    }

    #[test]
    fn resource_create_invalid_json_returns_400() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_resource_create(&registry, "{bad}").status(), 400);
    }

    #[test]
    fn resource_update_changes_description() {
        let registry = InMemoryRegistry::new();
        handle_resource_create(&registry, r#"{"name":"res","description":"old","location":"/a","type":"file"}"#);

        let resp = handle_resource_update(
            &registry, "res",
            r#"{"name":"res","description":"new","location":"/b","type":"file"}"#,
        );
        assert_eq!(resp.status(), 200);

        let data = data_field(&handle_resource_get(&registry, "res"));
        assert_eq!(data.get("description").and_then(|v| v.as_str()), Some("new"));
        assert_eq!(data.get("location").and_then(|v| v.as_str()), Some("/b"));
    }

    #[test]
    fn resource_update_rename_removes_old_entry() {
        let registry = InMemoryRegistry::new();
        handle_resource_create(&registry, r#"{"name":"old-res","description":"d","location":"/x","type":"file"}"#);

        let resp = handle_resource_update(
            &registry, "old-res",
            r#"{"name":"new-res","description":"d","location":"/x","type":"file"}"#,
        );
        assert_eq!(resp.status(), 200);
        assert_eq!(handle_resource_get(&registry, "old-res").status(), 404);
        assert_eq!(handle_resource_get(&registry, "new-res").status(), 200);
    }

    #[test]
    fn resource_update_invalid_json_returns_400() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_resource_update(&registry, "r", "???").status(), 400);
    }

    // ---- Prompt handlers ----

    #[test]
    fn prompt_create_and_get_roundtrip() {
        let registry = InMemoryRegistry::new();
        let body = r#"{"name":"my-prompt","description":"A prompt"}"#;

        assert_eq!(handle_prompt_create(&registry, body).status(), 200);

        let get_resp = handle_prompt_get(&registry, "my-prompt");
        assert_eq!(get_resp.status(), 200);

        let data = data_field(&get_resp);
        assert_eq!(
            data.get("name").and_then(|v| v.as_str()),
            Some("my-prompt")
        );
    }

    #[test]
    fn prompt_list_empty_then_populated() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            data_field(&handle_prompt_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(0)
        );

        handle_prompt_create(&registry, r#"{"name":"p1","description":"x"}"#);
        assert_eq!(
            data_field(&handle_prompt_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(1)
        );
    }

    #[test]
    fn prompt_get_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_prompt_get(&registry, "nope").status(), 404);
    }

    #[test]
    fn prompt_delete_existing() {
        let registry = InMemoryRegistry::new();
        handle_prompt_create(&registry, r#"{"name":"del-p","description":""}"#);
        assert_eq!(handle_prompt_delete(&registry, "del-p").status(), 200);
        assert_eq!(handle_prompt_get(&registry, "del-p").status(), 404);
    }

    #[test]
    fn prompt_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_prompt_delete(&registry, "nope").status(), 404);
    }

    #[test]
    fn prompt_create_invalid_json_returns_400() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_prompt_create(&registry, "[]").status(), 400);
    }

    // ---- Namespace handlers ----

    #[test]
    fn namespace_create_and_get_roundtrip() {
        let registry = InMemoryRegistry::new();
        let body = r#"{"name":"finance","path":"/finance"}"#;

        assert_eq!(handle_namespace_create(&registry, body).status(), 200);

        let get_resp = handle_namespace_get(&registry, "finance");
        assert_eq!(get_resp.status(), 200);

        let data = data_field(&get_resp);
        assert_eq!(data.get("name").and_then(|v| v.as_str()), Some("finance"));
        assert_eq!(data.get("path").and_then(|v| v.as_str()), Some("/finance"));
        assert_eq!(data.get("id").and_then(|v| v.as_str()), Some("finance"));
    }

    #[test]
    fn namespace_update_uses_path_parameter_for_name() {
        let registry = InMemoryRegistry::new();
        handle_namespace_create(&registry, r#"{"name":"original","path":"/orig"}"#);

        let update_body = r#"{"name":"ignored","path":"/updated"}"#;
        let resp = handle_namespace_update(&registry, "original", update_body);
        assert_eq!(resp.status(), 200);

        let data = data_field(&handle_namespace_get(&registry, "original"));
        assert_eq!(data.get("path").and_then(|v| v.as_str()), Some("/updated"));
        assert_eq!(
            data.get("name").and_then(|v| v.as_str()),
            Some("original")
        );
    }

    #[test]
    fn namespace_list_has_default_then_grows() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            data_field(&handle_namespace_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(1)
        );

        handle_namespace_create(&registry, r#"{"name":"ns1","path":"/ns1"}"#);
        assert_eq!(
            data_field(&handle_namespace_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(2)
        );
    }

    #[test]
    fn namespace_get_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_namespace_get(&registry, "nope").status(), 404);
    }

    #[test]
    fn namespace_delete_existing() {
        let registry = InMemoryRegistry::new();
        handle_namespace_create(&registry, r#"{"name":"del-ns","path":"/"}"#);
        assert_eq!(handle_namespace_delete(&registry, "del-ns").status(), 200);
        assert_eq!(handle_namespace_get(&registry, "del-ns").status(), 404);
    }

    #[test]
    fn namespace_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_namespace_delete(&registry, "ghost").status(), 404);
    }

    #[test]
    fn namespace_create_invalid_json_returns_400() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_namespace_create(&registry, "!!!").status(), 400);
    }

    #[test]
    fn namespace_update_invalid_json_returns_400() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            handle_namespace_update(&registry, "x", "???").status(),
            400
        );
    }

    // ---- Service handlers ----

    #[test]
    fn service_create_and_get_roundtrip() {
        let registry = InMemoryRegistry::new();
        let body =
            r#"{"name":"echo","address":"localhost:9191","service_type":"tool-invoker"}"#;

        let create_resp = handle_service_create(&registry, body);
        assert_eq!(create_resp.status(), 200);

        let get_resp = handle_service_get(&registry, "echo");
        assert_eq!(get_resp.status(), 200);

        let data = data_field(&get_resp);
        let arr = data.as_array();
        assert_eq!(arr.map(|a| a.len()), Some(1));
    }

    #[test]
    fn service_list_empty_then_populated() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            data_field(&handle_service_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(0)
        );

        handle_service_create(
            &registry,
            r#"{"name":"s1","address":"h:1","service_type":"t"}"#,
        );
        assert_eq!(
            data_field(&handle_service_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(1)
        );
    }

    #[test]
    fn service_get_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_service_get(&registry, "nope").status(), 404);
    }

    #[test]
    fn service_delete_existing() {
        let registry = InMemoryRegistry::new();
        handle_service_create(
            &registry,
            r#"{"name":"del-svc","address":"h:1","service_type":"t"}"#,
        );
        let resp = handle_service_delete(&registry, "del-svc");
        assert_eq!(resp.status(), 200);

        let data = data_field(&resp);
        assert_eq!(
            data.get("removed").and_then(|v| v.as_str()),
            Some("del-svc")
        );
    }

    #[test]
    fn service_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_service_delete(&registry, "nope").status(), 404);
    }

    #[test]
    fn service_create_invalid_json_returns_400() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_service_create(&registry, "nope").status(), 400);
    }

    // ---- Forward handlers (sync-only, skipping async create/refresh) ----

    #[test]
    fn forward_list_and_get() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(ForwardEntry {
            name: "upstream".to_owned(),
            address: "http://remote:8080".to_owned(),
            namespace: None,
        });

        let list_resp = handle_forward_list(&registry);
        assert_eq!(list_resp.status(), 200);
        assert_eq!(
            data_field(&list_resp).as_array().map(|a| a.len()),
            Some(1)
        );

        let get_resp = handle_forward_get(&registry, "upstream");
        assert_eq!(get_resp.status(), 200);
        assert_eq!(
            data_field(&get_resp)
                .get("address")
                .and_then(|v| v.as_str()),
            Some("http://remote:8080")
        );
    }

    #[test]
    fn forward_get_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_forward_get(&registry, "missing").status(), 404);
    }

    #[test]
    fn forward_delete_existing() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(ForwardEntry {
            name: "del-fwd".to_owned(),
            address: "http://x:1".to_owned(),
            namespace: None,
        });
        assert_eq!(handle_forward_delete(&registry, "del-fwd").status(), 200);
        assert_eq!(handle_forward_get(&registry, "del-fwd").status(), 404);
    }

    #[test]
    fn forward_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_forward_delete(&registry, "nope").status(), 404);
    }

    // ---- Capability handlers ----

    #[test]
    fn capability_list_reflects_services() {
        let registry = InMemoryRegistry::new();
        let resp = handle_capability_list(&registry);
        assert_eq!(resp.status(), 200);
        assert_eq!(data_field(&resp).as_array().map(|a| a.len()), Some(0));

        registry.register_service(ServiceEntry {
            name: "echo".to_owned(),
            address: "host:9191".to_owned(),
            service_type: "tool-invoker".to_owned(),
        });

        let resp = handle_capability_list(&registry);
        let data = data_field(&resp);
        let arr = data.as_array();
        assert_eq!(arr.map(|a| a.len()), Some(1));

        if let Some(first) = arr.and_then(|a| a.first()) {
            assert_eq!(
                first.get("serviceName").and_then(|v| v.as_str()),
                Some("echo")
            );
            assert_eq!(first.get("host").and_then(|v| v.as_str()), Some("host"));
            assert_eq!(first.get("port").and_then(|v| v.as_u64()), Some(9191));
        }
    }

    #[test]
    fn capability_list_parses_address_without_port() {
        let registry = InMemoryRegistry::new();
        registry.register_service(ServiceEntry {
            name: "no-port".to_owned(),
            address: "just-a-host".to_owned(),
            service_type: "t".to_owned(),
        });

        let data = data_field(&handle_capability_list(&registry));
        if let Some(first) = data.as_array().and_then(|a| a.first()) {
            assert_eq!(first.get("port").and_then(|v| v.as_u64()), Some(0));
        }
    }

    #[test]
    fn capability_state_returns_empty_object() {
        let resp = handle_capability_state();
        assert_eq!(resp.status(), 200);

        let data = data_field(&resp);
        assert!(data.is_object());
        assert_eq!(data.as_object().map(|m| m.len()), Some(0));
    }

    // ---- Statistics handler ----

    #[test]
    fn statistics_empty_registry() {
        let registry = InMemoryRegistry::new();
        let resp = handle_statistics(&registry);
        assert_eq!(resp.status(), 200);

        let data = data_field(&resp);
        assert_eq!(data.get("toolsCount").and_then(|v| v.as_i64()), Some(0));
        assert_eq!(
            data.get("resourcesCount").and_then(|v| v.as_i64()),
            Some(0)
        );
        assert_eq!(
            data.get("promptsCount").and_then(|v| v.as_i64()),
            Some(0)
        );
        assert_eq!(
            data.get("forwardsCount").and_then(|v| v.as_i64()),
            Some(0)
        );
    }

    #[test]
    fn statistics_populated_registry() {
        let registry = InMemoryRegistry::new();

        handle_tool_create(
            &registry,
            r#"{"name":"t1","description":"","uri":"u","type":"x","input_schema":{"type":"object"}}"#,
        );
        handle_tool_create(
            &registry,
            r#"{"name":"t2","description":"","uri":"u","type":"x","input_schema":{"type":"object"}}"#,
        );
        handle_resource_create(&registry, r#"{"name":"r1","location":"/x","type":"file"}"#);
        handle_prompt_create(&registry, r#"{"name":"p1","description":""}"#);
        registry.register_forward(ForwardEntry {
            name: "f1".to_owned(),
            address: "http://x:1".to_owned(),
            namespace: None,
        });

        let data = data_field(&handle_statistics(&registry));
        assert_eq!(data.get("toolsCount").and_then(|v| v.as_i64()), Some(2));
        assert_eq!(
            data.get("resourcesCount").and_then(|v| v.as_i64()),
            Some(1)
        );
        assert_eq!(
            data.get("promptsCount").and_then(|v| v.as_i64()),
            Some(1)
        );
        assert_eq!(
            data.get("forwardsCount").and_then(|v| v.as_i64()),
            Some(1)
        );
    }

    #[test]
    fn statistics_includes_capability_fields() {
        let registry = InMemoryRegistry::new();
        let data = data_field(&handle_statistics(&registry));

        let tool_caps = data.get("toolCapabilities");
        assert!(tool_caps.is_some());
        assert_eq!(
            tool_caps
                .and_then(|c| c.get("total"))
                .and_then(|v| v.as_i64()),
            Some(0)
        );

        let resource_caps = data.get("resourceCapabilities");
        assert!(resource_caps.is_some());
        assert_eq!(
            resource_caps
                .and_then(|c| c.get("total"))
                .and_then(|v| v.as_i64()),
            Some(0)
        );
    }

    // ---- Serialization format (camelCase) ----

    #[test]
    fn tool_serializes_camel_case_keys() {
        let registry = InMemoryRegistry::new();
        handle_tool_create(
            &registry,
            r#"{"name":"cc","description":"","uri":"u","type":"x","input_schema":{"type":"object","properties":{"msg":{"type":"string"}}}}"#,
        );

        let data = data_field(&handle_tool_get(&registry, "cc"));
        assert!(
            data.get("inputSchema").is_some(),
            "expected camelCase key 'inputSchema' in serialized output"
        );
        assert!(
            data.get("input_schema").is_none(),
            "snake_case key 'input_schema' should not appear in serialized output"
        );
    }

    #[test]
    fn tool_serializes_optional_camel_case_keys() {
        let registry = InMemoryRegistry::new();
        handle_tool_create(
            &registry,
            r#"{"name":"cc-opt","description":"","uri":"u","type":"x","input_schema":{"type":"object"},"configurationURI":"cfg://a","secretsURI":"sec://b"}"#,
        );

        let data = data_field(&handle_tool_get(&registry, "cc-opt"));
        assert!(data.get("configurationURI").is_some());
        assert!(data.get("configuration_uri").is_none());
        assert!(data.get("secretsURI").is_some());
        assert!(data.get("secrets_uri").is_none());
    }

    #[test]
    fn resource_serializes_camel_case_keys() {
        let registry = InMemoryRegistry::new();
        handle_resource_create(
            &registry,
            r#"{"name":"cc-res","location":"/x","type":"file","mime_type":"text/plain"}"#,
        );

        let data = data_field(&handle_resource_get(&registry, "cc-res"));
        assert!(
            data.get("mimeType").is_some(),
            "expected camelCase key 'mimeType' in serialized output"
        );
        assert!(
            data.get("mime_type").is_none(),
            "snake_case key 'mime_type' should not appear in serialized output"
        );
    }

    // ---- Response envelope ----

    #[test]
    fn success_response_has_null_error() {
        let registry = InMemoryRegistry::new();
        let resp = handle_tool_list(&registry);
        let body = parse_body(&resp);
        assert!(body.get("error").is_some());
        assert!(body["error"].is_null());
    }

    #[test]
    fn error_response_has_null_data() {
        let registry = InMemoryRegistry::new();
        let resp = handle_tool_get(&registry, "nonexistent");
        let body = parse_body(&resp);
        assert!(body.get("data").is_some());
        assert!(body["data"].is_null());
        assert!(body.get("error").and_then(|v| v.as_str()).is_some());
    }
}

