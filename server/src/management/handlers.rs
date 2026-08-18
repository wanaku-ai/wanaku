use http::{Response, StatusCode};
use tracing::{info, warn};

use wanaku_praxis_apis::registry::{
    ForwardEntry, ForwardRegistry, InMemoryRegistry, NamespaceEntry, NamespaceRegistry,
    PromptEntry, PromptRegistry, ResourceEntry, ResourceRegistry,
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
        None => json_err(StatusCode::NOT_FOUND, &format!("tool not found: {name}")),
    }
}

pub(super) fn handle_tool_update(registry: &InMemoryRegistry, path_name: &str, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, name = %path_name, "tool update request body");
    let mut tool: ToolEntry = match serde_json::from_str(body) {
        Ok(t) => t,
        Err(e) => {
            warn!(error = %e, "invalid tool JSON");
            return json_err(StatusCode::BAD_REQUEST, &format!("invalid tool JSON: {e}"));
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
        None => json_err(StatusCode::NOT_FOUND, &format!("tool not found after update: {name}")),
    }
}

pub(super) fn handle_tool_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_tool(name) {
        info!(tool = %name, "removed tool via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(StatusCode::NOT_FOUND, &format!("tool not found: {name}"))
    }
}

pub(super) fn handle_resource_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let resources = registry.list_resources();
    json_ok(&serde_json::json!(resources))
}

pub(super) fn handle_resource_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_resource(name) {
        Some(resource) => json_ok(&serde_json::json!(resource)),
        None => json_err(StatusCode::NOT_FOUND, &format!("resource not found: {name}")),
    }
}

pub(super) fn handle_resource_update(registry: &InMemoryRegistry, path_name: &str, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, name = %path_name, "resource update request body");
    let mut resource: ResourceEntry = match serde_json::from_str(body) {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "invalid resource JSON");
            return json_err(StatusCode::BAD_REQUEST, &format!("invalid resource JSON: {e}"));
        }
    };

    if let Some(existing) = registry.get_resource(path_name) {
        for (k, v) in &existing.labels {
            if k.starts_with("wanaku.") {
                resource.labels.entry(k.clone()).or_insert_with(|| v.clone());
            }
        }
    }

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
        None => json_err(StatusCode::NOT_FOUND, &format!("resource not found after update: {name}")),
    }
}

pub(super) fn handle_resource_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_resource(name) {
        info!(resource = %name, "removed resource via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(StatusCode::NOT_FOUND, &format!("resource not found: {name}"))
    }
}

pub(super) fn handle_prompt_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let prompts = registry.list_prompts();
    json_ok(&serde_json::json!(prompts))
}

pub(super) fn handle_prompt_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_prompt(name) {
        Some(prompt) => json_ok(&serde_json::json!(prompt)),
        None => json_err(StatusCode::NOT_FOUND, &format!("prompt not found: {name}")),
    }
}

pub(super) fn handle_prompt_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_prompt(name) {
        info!(prompt = %name, "removed prompt via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(StatusCode::NOT_FOUND, &format!("prompt not found: {name}"))
    }
}

pub(super) fn handle_namespace_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let namespaces = registry.list_namespaces();
    json_ok(&serde_json::json!(namespaces))
}

pub(super) fn handle_namespace_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_namespace(name) {
        Some(ns) => json_ok(&serde_json::json!(ns)),
        None => json_err(StatusCode::NOT_FOUND, &format!("namespace not found: {name}")),
    }
}

pub(super) fn handle_namespace_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
    let namespace: NamespaceEntry = match serde_json::from_str(body) {
        Ok(n) => n,
        Err(e) => {
            warn!(error = %e, "invalid namespace JSON");
            return json_err(StatusCode::BAD_REQUEST, &format!("invalid namespace JSON: {e}"));
        }
    };

    let name = namespace.name.clone();
    registry.register_namespace(namespace);
    info!(namespace = %name, "registered namespace via management API");
    match registry.get_namespace(&name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(StatusCode::NOT_FOUND, &format!("namespace not found after registration: {name}")),
    }
}

pub(super) fn handle_namespace_update(registry: &InMemoryRegistry, path_name: &str, body: &str) -> Response<Vec<u8>> {
    let mut namespace: NamespaceEntry = match serde_json::from_str(body) {
        Ok(n) => n,
        Err(e) => {
            warn!(error = %e, "invalid namespace JSON");
            return json_err(StatusCode::BAD_REQUEST, &format!("invalid namespace JSON: {e}"));
        }
    };

    namespace.name = path_name.to_owned();
    namespace.id = None;
    registry.register_namespace(namespace);
    info!(namespace = %path_name, "updated namespace via management API");
    match registry.get_namespace(path_name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(StatusCode::NOT_FOUND, &format!("namespace not found after update: {path_name}")),
    }
}

pub(super) fn handle_namespace_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_namespace(name) {
        info!(namespace = %name, "removed namespace via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(StatusCode::NOT_FOUND, &format!("namespace not found: {name}"))
    }
}

pub(super) fn handle_forward_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let forwards = registry.list_forwards();
    json_ok(&serde_json::json!(forwards))
}

pub(super) fn handle_forward_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_forward(name) {
        Some(forward) => json_ok(&serde_json::json!(forward)),
        None => json_err(StatusCode::NOT_FOUND, &format!("forward not found: {name}")),
    }
}

pub(super) async fn handle_forward_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, "forward create request body");
    let mut forward: ForwardEntry = match serde_json::from_str(body) {
        Ok(f) => f,
        Err(e) => {
            warn!(error = %e, "invalid forward JSON");
            return json_err(StatusCode::BAD_REQUEST, &format!("invalid forward JSON: {e}"));
        }
    };

    let discovery = match wanaku_praxis_apis::mcp_client::discover_forward(&forward.address).await {
        Ok(d) => d,
        Err(e) => {
            warn!(forward = %forward.name, error = %e, "forward discovery failed");
            registry.register_forward(forward.clone());
            return json_ok(&serde_json::json!({
                "forward": &forward,
                "tools_discovered": 0,
                "resources_discovered": 0,
                "prompts_discovered": 0,
            }));
        }
    };

    forward.server_info = discovery.server_info;
    info!(forward = %forward.name, address = %forward.address, "registered forward via management API");
    registry.register_forward(forward.clone());

    let tools_count = register_discovered_tools(registry, &forward, &discovery.tools);
    let resources_count = register_discovered_resources(registry, &forward, &discovery.resources, &discovery.resource_templates);
    let prompts_count = register_discovered_prompts(registry, &forward, &discovery.prompts);

    json_ok(&serde_json::json!({
        "forward": &forward,
        "tools_discovered": tools_count,
        "resources_discovered": resources_count,
        "prompts_discovered": prompts_count,
    }))
}

pub(super) fn handle_forward_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    let forward = registry.get_forward(name);

    if !registry.remove_forward(name) {
        return json_err(StatusCode::NOT_FOUND, &format!("forward not found: {name}"));
    }

    if let Some(fwd) = forward {
        remove_forwarded_tools(registry, &fwd.address);
        remove_forwarded_resources(registry, &fwd.address);
        remove_forwarded_prompts(registry, &fwd.address);
    }

    info!(forward = %name, "removed forward via management API");
    json_ok(&serde_json::json!({"removed": name}))
}

pub(super) async fn handle_forward_refresh(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    let mut forward = match registry.get_forward(name) {
        Some(f) => f,
        None => return json_err(StatusCode::NOT_FOUND, &format!("forward not found: {name}")),
    };

    remove_forwarded_tools(registry, &forward.address);
    remove_forwarded_resources(registry, &forward.address);
    remove_forwarded_prompts(registry, &forward.address);

    let discovery = match wanaku_praxis_apis::mcp_client::discover_forward(&forward.address).await {
        Ok(d) => d,
        Err(e) => {
            warn!(forward = %name, error = %e, "forward refresh discovery failed");
            return json_ok(&serde_json::json!({"refreshed": name, "tools_discovered": 0, "resources_discovered": 0, "prompts_discovered": 0}));
        }
    };

    forward.server_info = discovery.server_info;
    registry.register_forward(forward.clone());

    let tools_count = register_discovered_tools(registry, &forward, &discovery.tools);
    let resources_count = register_discovered_resources(registry, &forward, &discovery.resources, &discovery.resource_templates);
    let prompts_count = register_discovered_prompts(registry, &forward, &discovery.prompts);

    info!(forward = %name, tools_discovered = tools_count, resources_discovered = resources_count, prompts_discovered = prompts_count, "refreshed forward");
    json_ok(&serde_json::json!({"refreshed": name, "tools_discovered": tools_count, "resources_discovered": resources_count, "prompts_discovered": prompts_count}))
}

pub async fn discover_and_update_forward(registry: &InMemoryRegistry, forward: &ForwardEntry) {
    let discovery = match wanaku_praxis_apis::mcp_client::discover_forward(&forward.address).await {
        Ok(d) => d,
        Err(e) => {
            warn!(forward = %forward.name, error = %e, "forward discovery failed at startup");
            return;
        }
    };

    let mut updated = forward.clone();
    updated.server_info = discovery.server_info;
    registry.register_forward(updated.clone());

    let tools_count = register_discovered_tools(registry, &updated, &discovery.tools);
    let resources_count = register_discovered_resources(registry, &updated, &discovery.resources, &discovery.resource_templates);
    let prompts_count = register_discovered_prompts(registry, &updated, &discovery.prompts);

    info!(
        forward = %forward.name,
        tools_discovered = tools_count,
        resources_discovered = resources_count,
        prompts_discovered = prompts_count,
        "forward discovery complete"
    );
}

pub async fn discover_tools_from_forward(registry: &InMemoryRegistry, forward: &ForwardEntry) -> usize {
    let tools = match wanaku_praxis_apis::mcp_client::list_tools(&forward.address).await {
        Ok(t) => t,
        Err(e) => {
            warn!(forward = %forward.name, error = %e, "failed to discover tools from forward");
            return 0;
        }
    };

    register_discovered_tools(registry, forward, &tools)
}

fn register_discovered_tools(registry: &InMemoryRegistry, forward: &ForwardEntry, tools: &[serde_json::Value]) -> usize {
    let namespace = forward.namespace.as_deref().unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);
    let mut count = 0;

    for tool_json in tools {
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

pub async fn discover_resources_from_forward(registry: &InMemoryRegistry, forward: &ForwardEntry) -> usize {
    let resources = match wanaku_praxis_apis::mcp_client::list_resources(&forward.address).await {
        Ok(r) => r,
        Err(e) => {
            warn!(forward = %forward.name, error = %e, "failed to discover resources from forward");
            return 0;
        }
    };

    let templates = match wanaku_praxis_apis::mcp_client::list_resource_templates(&forward.address).await {
        Ok(t) => t,
        Err(e) => {
            tracing::debug!(forward = %forward.name, error = %e, "no resource templates from forward (may not be supported)");
            Vec::new()
        }
    };

    register_discovered_resources(registry, forward, &resources, &templates)
}

fn register_discovered_resources(
    registry: &InMemoryRegistry,
    forward: &ForwardEntry,
    resources: &[serde_json::Value],
    templates: &[serde_json::Value],
) -> usize {
    let namespace = forward.namespace.as_deref().unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);
    let mut count = 0;

    for res_json in resources {
        let name = match res_json.get("name").and_then(|n| n.as_str()).map(str::trim) {
            Some(n) if !n.is_empty() => n,
            _ => {
                warn!(forward = %forward.name, "skipping forwarded resource with missing or empty name");
                continue;
            }
        };
        let description = res_json
            .get("description")
            .and_then(|d| d.as_str())
            .unwrap_or_default();
        let uri = match res_json.get("uri").and_then(|u| u.as_str()).map(str::trim) {
            Some(u) if !u.is_empty() => u,
            _ => {
                warn!(forward = %forward.name, resource = %name, "skipping forwarded resource with missing or empty uri");
                continue;
            }
        };
        let mime_type = res_json
            .get("mimeType")
            .and_then(|m| m.as_str())
            .unwrap_or_default();

        let mut labels = std::collections::HashMap::new();
        labels.insert(
            wanaku_praxis_apis::registry::FORWARD_ADDRESS_LABEL.to_owned(),
            forward.address.clone(),
        );

        let resource = ResourceEntry {
            name: name.to_owned(),
            description: description.to_owned(),
            location: uri.to_owned(),
            type_: MCP_FORWARD_TYPE.to_owned(),
            mime_type: mime_type.to_owned(),
            labels,
            id: None,
            namespace: Some(namespace.to_owned()),
            configuration_uri: None,
            secrets_uri: None,
        };

        info!(resource = %name, forward = %forward.name, "discovered forwarded resource");
        registry.register_resource(resource);
        count += 1;
    }

    for tmpl_json in templates {
        let name = match tmpl_json.get("name").and_then(|n| n.as_str()).map(str::trim) {
            Some(n) if !n.is_empty() => n,
            _ => {
                warn!(forward = %forward.name, "skipping forwarded template with missing or empty name");
                continue;
            }
        };
        let uri_template = match tmpl_json.get("uriTemplate").and_then(|u| u.as_str()).map(str::trim) {
            Some(u) if !u.is_empty() => u,
            _ => {
                warn!(forward = %forward.name, template = %name, "skipping forwarded template with missing or empty uriTemplate");
                continue;
            }
        };
        let description = tmpl_json
            .get("description")
            .and_then(|d| d.as_str())
            .unwrap_or_default();
        let mime_type = tmpl_json
            .get("mimeType")
            .and_then(|m| m.as_str())
            .unwrap_or_default();

        let mut labels = std::collections::HashMap::new();
        labels.insert(
            wanaku_praxis_apis::registry::FORWARD_ADDRESS_LABEL.to_owned(),
            forward.address.clone(),
        );
        labels.insert(
            wanaku_praxis_apis::registry::IS_TEMPLATE_LABEL.to_owned(),
            "true".to_owned(),
        );

        let resource = ResourceEntry {
            name: name.to_owned(),
            description: description.to_owned(),
            location: uri_template.to_owned(),
            type_: MCP_FORWARD_TYPE.to_owned(),
            mime_type: mime_type.to_owned(),
            labels,
            id: None,
            namespace: Some(namespace.to_owned()),
            configuration_uri: None,
            secrets_uri: None,
        };

        info!(template = %name, uri_template = %uri_template, forward = %forward.name, "discovered forwarded resource template");
        registry.register_resource(resource);
        count += 1;
    }

    count
}

fn remove_forwarded_resources(registry: &InMemoryRegistry, address: &str) {
    let forwarded: Vec<String> = registry
        .list_resources()
        .iter()
        .filter(|r| r.is_mcp_forward() && r.forward_address() == Some(address))
        .map(|r| r.name.clone())
        .collect();

    registry.remove_resources_batch(&forwarded);
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

pub async fn discover_prompts_from_forward(registry: &InMemoryRegistry, forward: &ForwardEntry) -> usize {
    let prompts = match wanaku_praxis_apis::mcp_client::list_prompts(&forward.address).await {
        Ok(p) => p,
        Err(e) => {
            warn!(forward = %forward.name, error = %e, "failed to discover prompts from forward");
            return 0;
        }
    };

    register_discovered_prompts(registry, forward, &prompts)
}

fn register_discovered_prompts(
    registry: &InMemoryRegistry,
    forward: &ForwardEntry,
    prompts: &[serde_json::Value],
) -> usize {
    let namespace = forward.namespace.as_deref().unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);
    let mut count = 0;

    for prompt_json in prompts {
        let name = match prompt_json.get("name").and_then(|n| n.as_str()).map(str::trim) {
            Some(n) if !n.is_empty() => n,
            _ => {
                warn!(forward = %forward.name, "skipping forwarded prompt with missing or empty name");
                continue;
            }
        };
        let description = prompt_json
            .get("description")
            .and_then(|d| d.as_str())
            .unwrap_or_default();

        let arguments: Vec<wanaku_praxis_apis::registry::PromptArgument> = prompt_json
            .get("arguments")
            .and_then(|a| a.as_array())
            .map(|arr| {
                arr.iter()
                    .filter_map(|arg| {
                        let arg_name = arg.get("name")?.as_str()?;
                        Some(wanaku_praxis_apis::registry::PromptArgument {
                            name: arg_name.to_owned(),
                            description: arg
                                .get("description")
                                .and_then(|d| d.as_str())
                                .unwrap_or_default()
                                .to_owned(),
                            required: arg
                                .get("required")
                                .and_then(|r| r.as_bool())
                                .unwrap_or(false),
                        })
                    })
                    .collect()
            })
            .unwrap_or_default();

        let prompt = PromptEntry {
            name: name.to_owned(),
            description: description.to_owned(),
            arguments,
            messages: Vec::new(),
            id: None,
            namespace: Some(namespace.to_owned()),
            configuration_uri: Some(forward.address.clone()),
        };

        info!(prompt = %name, forward = %forward.name, "discovered forwarded prompt");
        registry.register_prompt(prompt);
        count += 1;
    }

    count
}

fn remove_forwarded_prompts(registry: &InMemoryRegistry, address: &str) {
    let forwarded: Vec<String> = registry
        .list_prompts()
        .iter()
        .filter(|p| p.messages.is_empty() && p.configuration_uri.as_deref() == Some(address))
        .map(|p| p.name.clone())
        .collect();

    registry.remove_prompts_batch(&forwarded);
}

#[cfg(test)]
mod forward_helpers_tests {
    use super::*;
    use wanaku_praxis_apis::registry::{InMemoryRegistry, PromptEntry, PromptRegistry, ToolEntry, ToolRegistry, ResourceRegistry, FORWARD_ADDRESS_LABEL};
    use wanaku_praxis_apis::registry::{PromptMessage, PromptRole};
    use std::collections::HashMap;

    #[test]
    fn remove_forwarded_resources_clears_matching_resources() {
        let registry = InMemoryRegistry::new();
        let fwd_addr = "http://remote:8080";

        let mut fwd_labels = HashMap::new();
        fwd_labels.insert(FORWARD_ADDRESS_LABEL.to_owned(), fwd_addr.to_owned());

        registry.register_resource(ResourceEntry {
            name: "fwd-res".to_owned(),
            description: "forwarded".to_owned(),
            location: "file:///data/report.csv".to_owned(),
            type_: MCP_FORWARD_TYPE.to_owned(),
            mime_type: "text/csv".to_owned(),
            labels: fwd_labels,
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        });
        registry.register_resource(ResourceEntry {
            name: "local-res".to_owned(),
            description: "local".to_owned(),
            location: "/tmp/local.txt".to_owned(),
            type_: "file".to_owned(),
            mime_type: "text/plain".to_owned(),
            labels: HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        });

        remove_forwarded_resources(&registry, fwd_addr);

        assert!(registry.get_resource("fwd-res").is_none());
        assert!(registry.get_resource("local-res").is_some());
    }

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

    #[test]
    fn remove_forwarded_prompts_clears_matching_prompts() {
        let registry = InMemoryRegistry::new();
        let fwd_addr = "http://remote:8080";

        registry.register_prompt(PromptEntry {
            name: "fwd-prompt".to_owned(),
            description: "forwarded".to_owned(),
            arguments: Vec::new(),
            messages: Vec::new(),
            id: None,
            namespace: None,
            configuration_uri: Some(fwd_addr.to_owned()),
        });
        registry.register_prompt(PromptEntry {
            name: "local-prompt".to_owned(),
            description: "local".to_owned(),
            arguments: Vec::new(),
            messages: Vec::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
        });

        remove_forwarded_prompts(&registry, fwd_addr);

        assert!(registry.get_prompt("fwd-prompt").is_none());
        assert!(registry.get_prompt("local-prompt").is_some());
    }

    #[test]
    fn remove_forwarded_prompts_preserves_user_prompts_with_same_uri() {
        let registry = InMemoryRegistry::new();
        let fwd_addr = "http://remote:8080";

        registry.register_prompt(PromptEntry {
            name: "user-prompt".to_owned(),
            description: "user-created with configurationURI".to_owned(),
            arguments: Vec::new(),
            messages: vec![PromptMessage {
                role: PromptRole::User,
                content: serde_json::json!("Hello {name}"),
            }],
            id: None,
            namespace: None,
            configuration_uri: Some(fwd_addr.to_owned()),
        });

        remove_forwarded_prompts(&registry, fwd_addr);

        assert!(
            registry.get_prompt("user-prompt").is_some(),
            "user-created prompt with messages must not be removed"
        );
    }
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
    }))
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;

    use http::Response;
    use wanaku_praxis_apis::registry::{
        ForwardEntry, ForwardRegistry, InMemoryRegistry,
        PromptEntry, PromptRegistry, ResourceEntry, ResourceRegistry,
        ToolEntry, ToolRegistry,
    };

    use super::{
        handle_forward_delete, handle_forward_get, handle_forward_list,
        handle_namespace_create, handle_namespace_delete, handle_namespace_get,
        handle_namespace_list, handle_namespace_update,
        handle_prompt_delete, handle_prompt_get, handle_prompt_list,
        handle_resource_delete, handle_resource_get, handle_resource_list,
        handle_resource_update,
        handle_statistics,
        handle_tool_delete, handle_tool_get, handle_tool_list,
        handle_tool_update,
    };

    fn parse_body(resp: &Response<Vec<u8>>) -> serde_json::Value {
        serde_json::from_slice(resp.body()).unwrap_or_default()
    }

    fn data_field(resp: &Response<Vec<u8>>) -> serde_json::Value {
        let body = parse_body(resp);
        body.get("data").cloned().unwrap_or_default()
    }

    fn test_tool(name: &str) -> ToolEntry {
        ToolEntry {
            name: name.to_owned(),
            description: String::new(),
            uri: "u".to_owned(),
            type_: "x".to_owned(),
            input_schema: serde_json::json!({"type": "object"}),
            labels: HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        }
    }

    fn test_resource(name: &str) -> ResourceEntry {
        ResourceEntry {
            name: name.to_owned(),
            description: String::new(),
            location: "/x".to_owned(),
            type_: "file".to_owned(),
            mime_type: String::new(),
            labels: HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        }
    }

    fn test_prompt(name: &str) -> PromptEntry {
        PromptEntry {
            name: name.to_owned(),
            description: String::new(),
            arguments: Vec::new(),
            messages: Vec::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
        }
    }

    // ---- Tool handlers ----

    #[test]
    fn tool_list_empty_then_populated() {
        let registry = InMemoryRegistry::new();
        let resp = handle_tool_list(&registry);
        assert_eq!(resp.status(), 200);
        assert_eq!(data_field(&resp).as_array().map(|a| a.len()), Some(0));

        registry.register_tool(test_tool("t1"));

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
        registry.register_tool(test_tool("to-delete"));

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
    fn tool_update_changes_description() {
        let registry = InMemoryRegistry::new();
        let mut tool = test_tool("upd");
        tool.description = "old".to_owned();
        registry.register_tool(tool);

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
        registry.register_tool(test_tool("old-name"));

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
    fn resource_list_empty_then_populated() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            data_field(&handle_resource_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(0)
        );

        registry.register_resource(test_resource("r1"));
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
        registry.register_resource(test_resource("del-res"));

        assert_eq!(handle_resource_delete(&registry, "del-res").status(), 200);
        assert_eq!(handle_resource_get(&registry, "del-res").status(), 404);
    }

    #[test]
    fn resource_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_resource_delete(&registry, "nope").status(), 404);
    }

    #[test]
    fn resource_update_changes_description() {
        let registry = InMemoryRegistry::new();
        let mut res = test_resource("res");
        res.description = "old".to_owned();
        res.location = "/a".to_owned();
        registry.register_resource(res);

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
        registry.register_resource(test_resource("old-res"));

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

    #[test]
    fn resource_update_preserves_internal_labels() {
        let registry = InMemoryRegistry::new();
        let mut res = test_resource("fwd-res");
        res.description = "old".to_owned();
        res.location = "file:///data".to_owned();
        res.type_ = "mcp-forward".to_owned();
        res.labels.insert("wanaku.forward_address".to_owned(), "http://remote:8080".to_owned());
        registry.register_resource(res);

        let resp = handle_resource_update(
            &registry, "fwd-res",
            r#"{"name":"fwd-res","description":"new","location":"file:///data","type":"mcp-forward"}"#,
        );
        assert_eq!(resp.status(), 200);

        let data = data_field(&handle_resource_get(&registry, "fwd-res"));
        assert_eq!(data.get("description").and_then(|v| v.as_str()), Some("new"));
        let labels = data.get("labels").and_then(|v| v.as_object());
        assert_eq!(
            labels.and_then(|l| l.get("wanaku.forward_address")).and_then(|v| v.as_str()),
            Some("http://remote:8080"),
        );
    }

    // ---- Prompt handlers ----

    #[test]
    fn prompt_list_empty_then_populated() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            data_field(&handle_prompt_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(0)
        );

        registry.register_prompt(test_prompt("p1"));
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
        registry.register_prompt(test_prompt("del-p"));
        assert_eq!(handle_prompt_delete(&registry, "del-p").status(), 200);
        assert_eq!(handle_prompt_get(&registry, "del-p").status(), 404);
    }

    #[test]
    fn prompt_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_prompt_delete(&registry, "nope").status(), 404);
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

    // ---- Forward handlers (sync-only, skipping async create/refresh) ----

    #[test]
    fn forward_list_and_get() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(ForwardEntry {
            name: "upstream".to_owned(),
            address: "http://remote:8080".to_owned(),
            namespace: None,
            server_info: None,
            labels: HashMap::new(),
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
            server_info: None,
            labels: HashMap::new(),
        });
        assert_eq!(handle_forward_delete(&registry, "del-fwd").status(), 200);
        assert_eq!(handle_forward_get(&registry, "del-fwd").status(), 404);
    }

    #[test]
    fn forward_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_forward_delete(&registry, "nope").status(), 404);
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

        registry.register_tool(test_tool("t1"));
        registry.register_tool(test_tool("t2"));
        registry.register_resource(test_resource("r1"));
        registry.register_prompt(test_prompt("p1"));
        registry.register_forward(ForwardEntry {
            name: "f1".to_owned(),
            address: "http://x:1".to_owned(),
            namespace: None,
            server_info: None,
            labels: HashMap::new(),
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

    // ---- Serialization format (camelCase) ----

    #[test]
    fn tool_serializes_camel_case_keys() {
        let registry = InMemoryRegistry::new();
        let mut tool = test_tool("cc");
        tool.input_schema = serde_json::json!({"type":"object","properties":{"msg":{"type":"string"}}});
        registry.register_tool(tool);

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
        let mut tool = test_tool("cc-opt");
        tool.configuration_uri = Some("cfg://a".to_owned());
        tool.secrets_uri = Some("sec://b".to_owned());
        registry.register_tool(tool);

        let data = data_field(&handle_tool_get(&registry, "cc-opt"));
        assert!(data.get("configurationURI").is_some());
        assert!(data.get("configuration_uri").is_none());
        assert!(data.get("secretsURI").is_some());
        assert!(data.get("secrets_uri").is_none());
    }

    #[test]
    fn resource_serializes_camel_case_keys() {
        let registry = InMemoryRegistry::new();
        let mut res = test_resource("cc-res");
        res.mime_type = "text/plain".to_owned();
        registry.register_resource(res);

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

