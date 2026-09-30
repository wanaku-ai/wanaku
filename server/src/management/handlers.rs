use http::{Response, StatusCode};
use tracing::{info, warn};

use crate::http_response::{json_err, json_ok};
use wanaku_infra::credentials::{
    CredentialAuditSink, CredentialBroker, CredentialRedactor, DiscoveryExchange,
    resolve_discovery_headers,
};
use wanaku_infra::mcp_client::ForwardDiscovery;
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::credentials::CredentialPurpose;
use wanaku_types::credentials::binding::NormalizedOrigin;
use wanaku_types::registry::{
    BindingRegistry, ForwardEntry, ForwardRegistry, MCP_FORWARD_TYPE, McpServerInfo,
    NamespaceEntry, NamespaceRegistry, PromptEntry, PromptRegistry, ResourceEntry,
    ResourceRegistry, ToolEntry, ToolRegistry,
};

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

pub(super) fn handle_tool_update(
    registry: &InMemoryRegistry,
    path_name: &str,
    body: &str,
) -> Response<Vec<u8>> {
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
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("tool not found after update: {name}"),
        ),
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
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("resource not found: {name}"),
        ),
    }
}

#[expect(clippy::cognitive_complexity, reason = "sequential validation steps")]
pub(super) fn handle_resource_update(
    registry: &InMemoryRegistry,
    path_name: &str,
    body: &str,
) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, name = %path_name, "resource update request body");
    let mut resource: ResourceEntry = match serde_json::from_str(body) {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "invalid resource JSON");
            return json_err(
                StatusCode::BAD_REQUEST,
                &format!("invalid resource JSON: {e}"),
            );
        }
    };

    if let Some(existing) = registry.get_resource(path_name) {
        for (k, v) in &existing.labels {
            if k.starts_with("wanaku.") {
                resource
                    .labels
                    .entry(k.clone())
                    .or_insert_with(|| v.clone());
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
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("resource not found after update: {name}"),
        ),
    }
}

pub(super) fn handle_resource_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_resource(name) {
        info!(resource = %name, "removed resource via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(
            StatusCode::NOT_FOUND,
            &format!("resource not found: {name}"),
        )
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
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("namespace not found: {name}"),
        ),
    }
}

/// Lists credential-binding metadata.
///
/// The response contains only non-secret binding metadata. Bindings hold
/// opaque secret references, never resolved secret values, so this endpoint
/// cannot leak credentials.
pub(super) fn handle_binding_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let bindings = registry.list_bindings();
    json_ok(&serde_json::json!(bindings))
}

/// Returns the non-secret metadata of a single credential binding by id.
pub(super) fn handle_binding_get(registry: &InMemoryRegistry, id: &str) -> Response<Vec<u8>> {
    match registry.get_binding(id) {
        Some(binding) => json_ok(&serde_json::json!(binding)),
        None => json_err(StatusCode::NOT_FOUND, &format!("binding not found: {id}")),
    }
}

pub(super) fn handle_namespace_create(
    registry: &InMemoryRegistry,
    body: &str,
) -> Response<Vec<u8>> {
    let namespace: NamespaceEntry = match serde_json::from_str(body) {
        Ok(n) => n,
        Err(e) => {
            warn!(error = %e, "invalid namespace JSON");
            return json_err(
                StatusCode::BAD_REQUEST,
                &format!("invalid namespace JSON: {e}"),
            );
        }
    };

    if let Err(reason) = wanaku_types::registry::validate_namespace_name(&namespace.name) {
        warn!(name = %namespace.name, reason = %reason, "namespace name validation failed");
        return json_err(StatusCode::BAD_REQUEST, &reason);
    }

    let name = namespace.name.clone();
    registry.register_namespace(namespace);
    info!(namespace = %name, "registered namespace via management API");
    match registry.get_namespace(&name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("namespace not found after registration: {name}"),
        ),
    }
}

pub(super) fn handle_namespace_update(
    registry: &InMemoryRegistry,
    path_name: &str,
    body: &str,
) -> Response<Vec<u8>> {
    let mut namespace: NamespaceEntry = match serde_json::from_str(body) {
        Ok(n) => n,
        Err(e) => {
            warn!(error = %e, "invalid namespace JSON");
            return json_err(
                StatusCode::BAD_REQUEST,
                &format!("invalid namespace JSON: {e}"),
            );
        }
    };

    if let Err(reason) = wanaku_types::registry::validate_namespace_name(path_name) {
        warn!(name = %path_name, reason = %reason, "namespace name validation failed");
        return json_err(StatusCode::BAD_REQUEST, &reason);
    }

    namespace.name = path_name.to_owned();
    registry.register_namespace(namespace);
    info!(namespace = %path_name, "updated namespace via management API");
    match registry.get_namespace(path_name) {
        Some(entry) => json_ok(&serde_json::json!(entry)),
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("namespace not found after update: {path_name}"),
        ),
    }
}

pub(super) fn handle_namespace_delete(
    registry: &InMemoryRegistry,
    name: &str,
) -> Response<Vec<u8>> {
    if registry.remove_namespace(name) {
        info!(namespace = %name, "removed namespace via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(
            StatusCode::NOT_FOUND,
            &format!("namespace not found: {name}"),
        )
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

#[expect(
    clippy::cognitive_complexity,
    clippy::too_many_lines,
    reason = "sequential discovery and registration"
)]
pub(super) async fn handle_forward_create(
    registry: &InMemoryRegistry,
    broker: &CredentialBroker,
    audit_sink: Option<&dyn CredentialAuditSink>,
    body: &str,
) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, "forward create request body");
    let mut forward: ForwardEntry = match serde_json::from_str(body) {
        Ok(f) => f,
        Err(e) => {
            warn!(error = %e, "invalid forward JSON");
            return json_err(
                StatusCode::BAD_REQUEST,
                &format!("invalid forward JSON: {e}"),
            );
        }
    };

    // A re-registration that changes the upstream address must drop credentials
    // cached against the previous origin before anything is brokered anew.
    if let Some(existing) = registry.get_forward(&forward.name)
        && existing.address != forward.address
    {
        info!(forward = %forward.name, "forward address changed; revalidating credentials");
        revalidate_forward_credentials(registry, broker, &forward);
    }

    let DiscoveryExchange {
        headers: discovery_headers,
        redactor,
    } = match discovery_headers_or_status(registry, broker, audit_sink, &forward).await {
        Ok(exchange) => exchange,
        Err(message) => {
            warn!(forward = %forward.name, "forward discovery credential brokerage failed");
            forward.available = false;
            forward.status_message = Some(message);
            registry.register_forward(forward.clone());
            return json_ok(&serde_json::json!({
                "forward": &forward,
                "tools_discovered": 0,
                "resources_discovered": 0,
                "prompts_discovered": 0,
            }));
        }
    };

    let discovery =
        match wanaku_infra::mcp_client::discover_forward(&forward.address, discovery_headers).await
        {
            Ok(d) => d,
            Err(e) => {
                // Redact any injected discovery secret the upstream echoed into
                // its error before it reaches the operator or the logs.
                let message = redactor.redact(&e.to_string());
                warn!(forward = %forward.name, error = %message, "forward discovery failed");
                forward.available = false;
                forward.status_message = Some(message);
                registry.register_forward(forward.clone());
                return json_ok(&serde_json::json!({
                    "forward": &forward,
                    "tools_discovered": 0,
                    "resources_discovered": 0,
                    "prompts_discovered": 0,
                }));
            }
        };

    let discovery = redact_discovery(&redactor, discovery);
    forward.server_info = discovery.server_info;
    forward.available = true;
    forward.status_message = None;
    info!(forward = %forward.name, address = %forward.address, "registered forward via management API");
    registry.register_forward(forward.clone());

    let tools_count = register_discovered_tools(registry, &forward, &discovery.tools);
    let resources_count = register_discovered_resources(
        registry,
        &forward,
        &discovery.resources,
        &discovery.resource_templates,
    );
    let prompts_count = register_discovered_prompts(registry, &forward, &discovery.prompts);

    json_ok(&serde_json::json!({
        "forward": &forward,
        "tools_discovered": tools_count,
        "resources_discovered": resources_count,
        "prompts_discovered": prompts_count,
    }))
}

pub(super) fn handle_forward_delete(
    registry: &InMemoryRegistry,
    broker: &CredentialBroker,
    name: &str,
) -> Response<Vec<u8>> {
    let forward = registry.get_forward(name);

    if !registry.remove_forward(name) {
        return json_err(StatusCode::NOT_FOUND, &format!("forward not found: {name}"));
    }

    if let Some(fwd) = forward {
        // Drop credentials cached for the forward so no secret outlives it.
        broker.cache().invalidate_forward(fwd.forward_id());
        remove_forwarded_tools(registry, fwd.forward_id());
        remove_forwarded_resources(registry, fwd.forward_id());
        remove_forwarded_prompts(registry, fwd.forward_id());
    }

    info!(forward = %name, "removed forward via management API");
    json_ok(&serde_json::json!({"removed": name}))
}

pub(super) async fn handle_forward_refresh(
    registry: &InMemoryRegistry,
    broker: &CredentialBroker,
    audit_sink: Option<&dyn CredentialAuditSink>,
    name: &str,
) -> Response<Vec<u8>> {
    let Some(mut forward) = registry.get_forward(name) else {
        return json_err(StatusCode::NOT_FOUND, &format!("forward not found: {name}"));
    };

    remove_forwarded_tools(registry, forward.forward_id());
    remove_forwarded_resources(registry, forward.forward_id());
    remove_forwarded_prompts(registry, forward.forward_id());

    let DiscoveryExchange {
        headers: discovery_headers,
        redactor,
    } = match discovery_headers_or_status(registry, broker, audit_sink, &forward).await {
        Ok(exchange) => exchange,
        Err(message) => {
            warn!(forward = %name, "forward refresh credential brokerage failed");
            forward.available = false;
            forward.status_message = Some(message);
            registry.register_forward(forward.clone());
            return json_ok(
                &serde_json::json!({"refreshed": name, "tools_discovered": 0, "resources_discovered": 0, "prompts_discovered": 0}),
            );
        }
    };

    let discovery = match wanaku_infra::mcp_client::discover_forward(
        &forward.address,
        discovery_headers,
    )
    .await
    {
        Ok(d) => d,
        Err(e) => {
            // Redact any injected discovery secret the upstream echoed into its
            // error before it reaches the operator or the logs.
            let message = redactor.redact(&e.to_string());
            warn!(forward = %name, error = %message, "forward refresh discovery failed");
            forward.available = false;
            forward.status_message = Some(message);
            registry.register_forward(forward.clone());
            return json_ok(
                &serde_json::json!({"refreshed": name, "tools_discovered": 0, "resources_discovered": 0, "prompts_discovered": 0}),
            );
        }
    };

    let discovery = redact_discovery(&redactor, discovery);
    forward.server_info = discovery.server_info;
    forward.available = true;
    forward.status_message = None;
    registry.register_forward(forward.clone());

    let tools_count = register_discovered_tools(registry, &forward, &discovery.tools);
    let resources_count = register_discovered_resources(
        registry,
        &forward,
        &discovery.resources,
        &discovery.resource_templates,
    );
    let prompts_count = register_discovered_prompts(registry, &forward, &discovery.prompts);

    info!(forward = %name, tools_discovered = tools_count, resources_discovered = resources_count, prompts_discovered = prompts_count, "refreshed forward");
    json_ok(
        &serde_json::json!({"refreshed": name, "tools_discovered": tools_count, "resources_discovered": resources_count, "prompts_discovered": prompts_count}),
    )
}

/// React to a forward whose upstream address changed.
///
/// Drops every cached credential scoped to the forward so a stale secret
/// resolved for the previous origin can never be reused. Then revalidates each
/// referenced binding against the new origin and warns (secret-free) when a
/// binding no longer matches. A mismatched binding fails closed at use time; the
/// warning is an early operator signal to update the binding.
fn revalidate_forward_credentials(
    registry: &InMemoryRegistry,
    broker: &CredentialBroker,
    forward: &ForwardEntry,
) {
    broker.cache().invalidate_forward(forward.forward_id());

    let new_origin = NormalizedOrigin::from_address(&forward.address);
    for purpose in [CredentialPurpose::Discovery, CredentialPurpose::Invocation] {
        let Some(binding_id) = forward.binding_for(purpose) else {
            continue;
        };
        let Some(binding) = registry.get_binding(binding_id) else {
            warn!(
                forward = %forward.name,
                binding_id = %binding_id,
                "forward references a missing credential binding after address change"
            );
            continue;
        };
        match &new_origin {
            Ok(origin) if origin.as_str() == binding.origin.as_str() => {}
            Ok(_) => warn!(
                forward = %forward.name,
                binding_id = %binding_id,
                "forward address changed to a different origin; credential binding no longer matches and will be denied until updated"
            ),
            Err(_) => warn!(
                forward = %forward.name,
                binding_id = %binding_id,
                "forward address changed to an invalid origin; credential binding will be denied until updated"
            ),
        }
    }
}

/// Resolve the discovery exchange for a forward, failing closed.
///
/// Returns the brokered headers and the response redactor on success, or a
/// redacted status message when a configured discovery binding cannot be
/// brokered. Callers must not run discovery when this returns an error.
async fn discovery_headers_or_status(
    registry: &InMemoryRegistry,
    broker: &CredentialBroker,
    audit_sink: Option<&dyn CredentialAuditSink>,
    forward: &ForwardEntry,
) -> Result<DiscoveryExchange, String> {
    resolve_discovery_headers(broker, registry, forward, audit_sink)
        .await
        .map_err(|e| format!("discovery credential brokerage failed: {e}"))
}

/// Redact any brokered discovery credential the upstream echoed into its
/// discovery response before the metadata is persisted and served to agents.
///
/// The upstream can reflect an injected discovery credential into a tool
/// description, a resource, a prompt, or its server identity. The management API
/// persists this metadata and serves it to agents through `tools/list`,
/// `resources/list`, and `prompts/list`. This strips the injected secret first.
/// An empty redactor (no discovery binding) leaves the response unchanged.
fn redact_discovery(
    redactor: &CredentialRedactor,
    discovery: ForwardDiscovery,
) -> ForwardDiscovery {
    if redactor.is_empty() {
        return discovery;
    }
    ForwardDiscovery {
        server_info: discovery
            .server_info
            .map(|info| redact_server_info(redactor, info)),
        tools: redactor.redact_json_each(&discovery.tools),
        resources: redactor.redact_json_each(&discovery.resources),
        resource_templates: redactor.redact_json_each(&discovery.resource_templates),
        prompts: redactor.redact_json_each(&discovery.prompts),
    }
}

/// Redact the fields of a discovered server identity.
///
/// The upstream controls every field it returns. `capabilities` uses a fixed
/// vocabulary and is unlikely to carry a secret, but `extensions` holds arbitrary
/// upstream-chosen keys, so both lists are redacted for consistency with the rest
/// of the discovery response. Redaction is a no-op on values that hold no secret.
fn redact_server_info(redactor: &CredentialRedactor, info: McpServerInfo) -> McpServerInfo {
    McpServerInfo {
        server_name: redactor.redact(&info.server_name),
        version: redactor.redact(&info.version),
        description: info.description.map(|s| redactor.redact(&s)),
        website_url: info.website_url.map(|s| redactor.redact(&s)),
        capabilities: info
            .capabilities
            .iter()
            .map(|s| redactor.redact(s))
            .collect(),
        extensions: info.extensions.iter().map(|s| redactor.redact(s)).collect(),
        instructions: info.instructions.map(|s| redactor.redact(&s)),
    }
}

pub async fn discover_and_update_forward(
    registry: &InMemoryRegistry,
    broker: &CredentialBroker,
    audit_sink: Option<&dyn CredentialAuditSink>,
    forward: &ForwardEntry,
) {
    let DiscoveryExchange {
        headers: discovery_headers,
        redactor,
    } = match discovery_headers_or_status(registry, broker, audit_sink, forward).await {
        Ok(exchange) => exchange,
        Err(message) => {
            warn!(forward = %forward.name, "forward discovery credential brokerage failed");
            let mut unavailable = forward.clone();
            unavailable.available = false;
            unavailable.status_message = Some(message);
            registry.register_forward(unavailable);
            return;
        }
    };

    let discovery = match wanaku_infra::mcp_client::discover_forward(
        &forward.address,
        discovery_headers,
    )
    .await
    {
        Ok(d) => d,
        Err(e) => {
            // Redact any injected discovery secret the upstream echoed into
            // its error before it reaches the operator or the logs.
            let message = redactor.redact(&e.to_string());
            warn!(forward = %forward.name, error = %message, "forward discovery failed at startup");
            let mut unavailable = forward.clone();
            unavailable.available = false;
            unavailable.status_message = Some(message);
            registry.register_forward(unavailable);
            return;
        }
    };

    let discovery = redact_discovery(&redactor, discovery);
    let mut updated = forward.clone();
    updated.server_info = discovery.server_info;
    updated.available = true;
    updated.status_message = None;
    registry.register_forward(updated.clone());

    let tools_count = register_discovered_tools(registry, &updated, &discovery.tools);
    let resources_count = register_discovered_resources(
        registry,
        &updated,
        &discovery.resources,
        &discovery.resource_templates,
    );
    let prompts_count = register_discovered_prompts(registry, &updated, &discovery.prompts);

    info!(
        forward = %forward.name,
        tools_discovered = tools_count,
        resources_discovered = resources_count,
        prompts_discovered = prompts_count,
        "forward discovery complete"
    );
}

pub async fn discover_tools_from_forward(
    registry: &InMemoryRegistry,
    forward: &ForwardEntry,
) -> usize {
    let tools = match wanaku_infra::mcp_client::list_tools(&forward.address).await {
        Ok(t) => t,
        Err(e) => {
            warn!(forward = %forward.name, error = %e, "failed to discover tools from forward");
            return 0;
        }
    };

    register_discovered_tools(registry, forward, &tools)
}

#[expect(clippy::too_many_lines, reason = "sequential tool registration")]
fn register_discovered_tools(
    registry: &InMemoryRegistry,
    forward: &ForwardEntry,
    tools: &[serde_json::Value],
) -> usize {
    let namespace = forward
        .namespace
        .as_deref()
        .unwrap_or(wanaku_types::registry::DEFAULT_NAMESPACE);
    let mut batch = Vec::with_capacity(tools.len());

    for tool_json in tools {
        let name = match tool_json
            .get("name")
            .and_then(|n| n.as_str())
            .map(str::trim)
        {
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

        info!(tool = %name, forward = %forward.name, "discovered forwarded tool");
        batch.push(ToolEntry {
            name: name.to_owned(),
            description: description.to_owned(),
            uri: forward.address.clone(),
            type_: MCP_FORWARD_TYPE.to_owned(),
            input_schema,
            labels: forward.labels.clone(),
            id: None,
            namespace: Some(namespace.to_owned()),
            forward_id: Some(forward.forward_id().to_owned()),
        });
    }

    let count = batch.len();
    registry.register_tools_batch(batch);
    count
}

pub async fn discover_resources_from_forward(
    registry: &InMemoryRegistry,
    forward: &ForwardEntry,
) -> usize {
    let resources = match wanaku_infra::mcp_client::list_resources(&forward.address).await {
        Ok(r) => r,
        Err(e) => {
            warn!(forward = %forward.name, error = %e, "failed to discover resources from forward");
            return 0;
        }
    };

    let templates = match wanaku_infra::mcp_client::list_resource_templates(&forward.address).await
    {
        Ok(t) => t,
        Err(e) => {
            tracing::debug!(forward = %forward.name, error = %e, "no resource templates from forward (may not be supported)");
            Vec::new()
        }
    };

    register_discovered_resources(registry, forward, &resources, &templates)
}

#[expect(
    clippy::cognitive_complexity,
    clippy::too_many_lines,
    reason = "sequential resource and template registration"
)]
fn register_discovered_resources(
    registry: &InMemoryRegistry,
    forward: &ForwardEntry,
    resources: &[serde_json::Value],
    templates: &[serde_json::Value],
) -> usize {
    let namespace = forward
        .namespace
        .as_deref()
        .unwrap_or(wanaku_types::registry::DEFAULT_NAMESPACE);
    let mut batch = Vec::with_capacity(resources.len() + templates.len());

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

        info!(resource = %name, forward = %forward.name, "discovered forwarded resource");
        batch.push(ResourceEntry {
            name: name.to_owned(),
            description: description.to_owned(),
            location: uri.to_owned(),
            type_: MCP_FORWARD_TYPE.to_owned(),
            mime_type: mime_type.to_owned(),
            labels: std::collections::HashMap::new(),
            id: None,
            namespace: Some(namespace.to_owned()),
            forward_id: Some(forward.forward_id().to_owned()),
        });
    }

    for tmpl_json in templates {
        let name = match tmpl_json
            .get("name")
            .and_then(|n| n.as_str())
            .map(str::trim)
        {
            Some(n) if !n.is_empty() => n,
            _ => {
                warn!(forward = %forward.name, "skipping forwarded template with missing or empty name");
                continue;
            }
        };
        let uri_template = match tmpl_json
            .get("uriTemplate")
            .and_then(|u| u.as_str())
            .map(str::trim)
        {
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
            wanaku_types::registry::IS_TEMPLATE_LABEL.to_owned(),
            "true".to_owned(),
        );

        info!(template = %name, uri_template = %uri_template, forward = %forward.name, "discovered forwarded resource template");
        batch.push(ResourceEntry {
            name: name.to_owned(),
            description: description.to_owned(),
            location: uri_template.to_owned(),
            type_: MCP_FORWARD_TYPE.to_owned(),
            mime_type: mime_type.to_owned(),
            labels,
            id: None,
            namespace: Some(namespace.to_owned()),
            forward_id: Some(forward.forward_id().to_owned()),
        });
    }

    let count = batch.len();
    registry.register_resources_batch(batch);
    count
}

fn remove_forwarded_resources(registry: &InMemoryRegistry, forward_id: &str) {
    let forwarded: Vec<String> = registry
        .list_resources()
        .iter()
        .filter(|r| r.is_mcp_forward() && r.forward_id.as_deref() == Some(forward_id))
        .map(|r| r.name.clone())
        .collect();

    registry.remove_resources_batch(&forwarded);
}

fn remove_forwarded_tools(registry: &InMemoryRegistry, forward_id: &str) {
    let forwarded: Vec<String> = registry
        .list_tools()
        .iter()
        .filter(|t| t.is_mcp_forward() && t.forward_id.as_deref() == Some(forward_id))
        .map(|t| t.name.clone())
        .collect();

    registry.remove_tools_batch(&forwarded);
}

pub async fn discover_prompts_from_forward(
    registry: &InMemoryRegistry,
    forward: &ForwardEntry,
) -> usize {
    let prompts = match wanaku_infra::mcp_client::list_prompts(&forward.address).await {
        Ok(p) => p,
        Err(e) => {
            warn!(forward = %forward.name, error = %e, "failed to discover prompts from forward");
            return 0;
        }
    };

    register_discovered_prompts(registry, forward, &prompts)
}

#[expect(clippy::too_many_lines, reason = "sequential prompt registration")]
fn register_discovered_prompts(
    registry: &InMemoryRegistry,
    forward: &ForwardEntry,
    prompts: &[serde_json::Value],
) -> usize {
    let namespace = forward
        .namespace
        .as_deref()
        .unwrap_or(wanaku_types::registry::DEFAULT_NAMESPACE);
    let mut batch = Vec::with_capacity(prompts.len());

    for prompt_json in prompts {
        let name = match prompt_json
            .get("name")
            .and_then(|n| n.as_str())
            .map(str::trim)
        {
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

        let arguments: Vec<wanaku_types::registry::PromptArgument> = prompt_json
            .get("arguments")
            .and_then(|a| a.as_array())
            .map(|arr| {
                arr.iter()
                    .filter_map(|arg| {
                        let arg_name = arg.get("name")?.as_str()?;
                        Some(wanaku_types::registry::PromptArgument {
                            name: arg_name.to_owned(),
                            description: arg
                                .get("description")
                                .and_then(|d| d.as_str())
                                .unwrap_or_default()
                                .to_owned(),
                            required: arg
                                .get("required")
                                .and_then(serde_json::Value::as_bool)
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
            forward_id: Some(forward.forward_id().to_owned()),
        };

        info!(prompt = %name, forward = %forward.name, "discovered forwarded prompt");
        batch.push(prompt);
    }

    let count = batch.len();
    registry.register_prompts_batch(batch);
    count
}

fn remove_forwarded_prompts(registry: &InMemoryRegistry, forward_id: &str) {
    let forwarded: Vec<String> = registry
        .list_prompts()
        .iter()
        .filter(|p| p.forward_id.as_deref() == Some(forward_id))
        .map(|p| p.name.clone())
        .collect();

    registry.remove_prompts_batch(&forwarded);
}

#[cfg(test)]
mod forward_helpers_tests {
    use super::*;
    use std::collections::HashMap;
    use wanaku_infra::registry::InMemoryRegistry;
    use wanaku_types::registry::{
        PromptEntry, PromptRegistry, ResourceRegistry, ToolEntry, ToolRegistry,
    };

    #[test]
    fn remove_forwarded_resources_clears_matching_resources() {
        let registry = InMemoryRegistry::new();
        let forward_id = "remote-forward";

        registry.register_resource(ResourceEntry {
            name: "fwd-res".to_owned(),
            description: "forwarded".to_owned(),
            location: "file:///data/report.csv".to_owned(),
            type_: MCP_FORWARD_TYPE.to_owned(),
            mime_type: "text/csv".to_owned(),
            labels: HashMap::new(),
            id: None,
            namespace: None,
            forward_id: Some(forward_id.to_owned()),
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
            forward_id: None,
        });

        remove_forwarded_resources(&registry, forward_id);

        assert!(registry.get_resource("fwd-res").is_none());
        assert!(registry.get_resource("local-res").is_some());
    }

    #[test]
    fn remove_forwarded_tools_clears_matching_tools() {
        let registry = InMemoryRegistry::new();
        let forward_id = "remote-forward";

        registry.register_tool(ToolEntry {
            name: "fwd-tool".to_owned(),
            description: "forwarded".to_owned(),
            uri: "http://remote:8080".to_owned(),
            type_: MCP_FORWARD_TYPE.to_owned(),
            input_schema: serde_json::json!({"type": "object"}),
            labels: HashMap::new(),
            id: None,
            namespace: None,
            forward_id: Some(forward_id.to_owned()),
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
            forward_id: None,
        });

        remove_forwarded_tools(&registry, forward_id);

        assert!(registry.get_tool("fwd-tool").is_none());
        assert!(registry.get_tool("local-tool").is_some());
    }

    #[test]
    fn remove_forwarded_prompts_clears_matching_prompts() {
        let registry = InMemoryRegistry::new();
        let forward_id = "remote-forward";

        registry.register_prompt(PromptEntry {
            name: "fwd-prompt".to_owned(),
            description: "forwarded".to_owned(),
            arguments: Vec::new(),
            messages: Vec::new(),
            id: None,
            namespace: None,
            forward_id: Some(forward_id.to_owned()),
        });
        registry.register_prompt(PromptEntry {
            name: "local-prompt".to_owned(),
            description: "local".to_owned(),
            arguments: Vec::new(),
            messages: Vec::new(),
            id: None,
            namespace: None,
            forward_id: None,
        });

        remove_forwarded_prompts(&registry, forward_id);

        assert!(registry.get_prompt("fwd-prompt").is_none());
        assert!(registry.get_prompt("local-prompt").is_some());
    }

    #[test]
    fn remove_forwarded_prompts_preserves_prompts_from_other_forwards() {
        let registry = InMemoryRegistry::new();

        registry.register_prompt(PromptEntry {
            name: "other-prompt".to_owned(),
            description: "from another forward".to_owned(),
            arguments: Vec::new(),
            messages: Vec::new(),
            id: None,
            namespace: None,
            forward_id: Some("other-forward".to_owned()),
        });

        remove_forwarded_prompts(&registry, "remote-forward");

        assert!(
            registry.get_prompt("other-prompt").is_some(),
            "prompt from a different forward must not be removed"
        );
    }
}

#[expect(
    clippy::cast_possible_wrap,
    reason = "registry counts won't exceed i64::MAX"
)]
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

pub(super) fn handle_info() -> Response<Vec<u8>> {
    json_ok(&serde_json::json!({
        "name": env!("CARGO_PKG_NAME"),
        "version": env!("CARGO_PKG_VERSION"),
    }))
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;

    use http::Response;
    use wanaku_infra::credentials::CredentialBroker;
    use wanaku_infra::registry::InMemoryRegistry;
    use wanaku_types::registry::{
        BindingRegistry, ForwardEntry, ForwardRegistry, PromptEntry, PromptRegistry, ResourceEntry,
        ResourceRegistry, ToolEntry, ToolRegistry,
    };

    use super::{
        handle_binding_get, handle_binding_list, handle_forward_delete, handle_forward_get,
        handle_forward_list, handle_info, handle_namespace_create, handle_namespace_delete,
        handle_namespace_get, handle_namespace_list, handle_namespace_update, handle_prompt_delete,
        handle_prompt_get, handle_prompt_list, handle_resource_delete, handle_resource_get,
        handle_resource_list, handle_resource_update, handle_statistics, handle_tool_delete,
        handle_tool_get, handle_tool_list, handle_tool_update,
    };

    fn test_broker() -> CredentialBroker {
        CredentialBroker::new(wanaku_types::credentials::ResolverRegistry::new())
    }

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
            forward_id: None,
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
            forward_id: None,
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
            forward_id: None,
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

        let update_body = r#"{"name":"upd","description":"new","uri":"u2","type":"y","input_schema":{"type":"object"}}"#;
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

        let update_body = r#"{"name":"new-name","description":"d","uri":"u","type":"x","input_schema":{"type":"object"}}"#;
        let resp = handle_tool_update(&registry, "old-name", update_body);
        assert_eq!(resp.status(), 200);

        assert_eq!(handle_tool_get(&registry, "old-name").status(), 404);
        assert_eq!(handle_tool_get(&registry, "new-name").status(), 200);
    }

    #[test]
    fn tool_update_invalid_json_returns_400() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_tool_update(&registry, "t", "???").status(), 400);
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
            &registry,
            "res",
            r#"{"name":"res","description":"new","location":"/b","type":"file"}"#,
        );
        assert_eq!(resp.status(), 200);

        let data = data_field(&handle_resource_get(&registry, "res"));
        assert_eq!(
            data.get("description").and_then(|v| v.as_str()),
            Some("new")
        );
        assert_eq!(data.get("location").and_then(|v| v.as_str()), Some("/b"));
    }

    #[test]
    fn resource_update_rename_removes_old_entry() {
        let registry = InMemoryRegistry::new();
        registry.register_resource(test_resource("old-res"));

        let resp = handle_resource_update(
            &registry,
            "old-res",
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
        res.labels.insert(
            "wanaku.forward_address".to_owned(),
            "http://remote:8080".to_owned(),
        );
        registry.register_resource(res);

        let resp = handle_resource_update(
            &registry,
            "fwd-res",
            r#"{"name":"fwd-res","description":"new","location":"file:///data","type":"mcp-forward"}"#,
        );
        assert_eq!(resp.status(), 200);

        let data = data_field(&handle_resource_get(&registry, "fwd-res"));
        assert_eq!(
            data.get("description").and_then(|v| v.as_str()),
            Some("new")
        );
        let labels = data.get("labels").and_then(|v| v.as_object());
        assert_eq!(
            labels
                .and_then(|l| l.get("wanaku.forward_address"))
                .and_then(|v| v.as_str()),
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
        let body = r#"{"name":"finance"}"#;

        assert_eq!(handle_namespace_create(&registry, body).status(), 200);

        let get_resp = handle_namespace_get(&registry, "finance");
        assert_eq!(get_resp.status(), 200);

        let data = data_field(&get_resp);
        assert_eq!(data.get("name").and_then(|v| v.as_str()), Some("finance"));
    }

    #[test]
    fn namespace_create_with_legacy_path_field() {
        let registry = InMemoryRegistry::new();
        let body = r#"{"path":"finance"}"#;

        assert_eq!(handle_namespace_create(&registry, body).status(), 200);

        let get_resp = handle_namespace_get(&registry, "finance");
        assert_eq!(get_resp.status(), 200);
    }

    #[test]
    fn namespace_create_validates_name() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            handle_namespace_create(&registry, r#"{"name":"Bad Name"}"#).status(),
            400
        );
        assert_eq!(
            handle_namespace_create(&registry, r#"{"name":"-leading"}"#).status(),
            400
        );
        assert_eq!(
            handle_namespace_create(&registry, r#"{"name":"trailing-"}"#).status(),
            400
        );
        assert_eq!(
            handle_namespace_create(&registry, r#"{"name":""}"#).status(),
            400
        );
    }

    #[test]
    fn namespace_update_uses_url_parameter() {
        let registry = InMemoryRegistry::new();
        handle_namespace_create(&registry, r#"{"name":"original"}"#);

        let update_body = r#"{"name":"ignored"}"#;
        let resp = handle_namespace_update(&registry, "original", update_body);
        assert_eq!(resp.status(), 200);

        let data = data_field(&handle_namespace_get(&registry, "original"));
        assert_eq!(data.get("name").and_then(|v| v.as_str()), Some("original"));
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

        handle_namespace_create(&registry, r#"{"name":"ns1"}"#);
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
    fn forward_namespace_appears_in_namespace_list() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(ForwardEntry {
            name: "example-mcp".to_owned(),
            address: "http://localhost:9090/mcp".to_owned(),
            namespace: Some("test-ns".to_owned()),
            server_info: None,
            labels: HashMap::new(),
            available: false,
            status_message: None,
            credential_bindings: HashMap::new(),
        });

        let names: Vec<String> = data_field(&handle_namespace_list(&registry))
            .as_array()
            .map(|entries| {
                entries
                    .iter()
                    .filter_map(|e| e.get("name").and_then(|v| v.as_str()).map(str::to_owned))
                    .collect()
            })
            .unwrap_or_default();

        assert!(
            names.iter().any(|n| n == "test-ns"),
            "namespace referenced by a forward should be listed by GET /api/v1/namespaces, got {names:?}"
        );

        // The namespace should also be individually retrievable.
        assert_eq!(handle_namespace_get(&registry, "test-ns").status(), 200);
    }

    #[test]
    fn namespace_delete_existing() {
        let registry = InMemoryRegistry::new();
        handle_namespace_create(&registry, r#"{"name":"del-ns"}"#);
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
        assert_eq!(handle_namespace_update(&registry, "x", "???").status(), 400);
    }

    // ---- Binding handlers ----

    fn sample_binding(id: &str) -> wanaku_types::credentials::CredentialBinding {
        use wanaku_types::credentials::{
            BindingRestrictions, CacheRules, CredentialBinding, CredentialPurpose,
            InjectionMechanism, NormalizedOrigin, SecretRef,
        };
        CredentialBinding {
            id: id.to_owned(),
            forward_id: "fwd-a".to_owned(),
            origin: NormalizedOrigin::from_address("https://api.example.com")
                .expect("valid origin"),
            mechanism: InjectionMechanism::Bearer,
            secret_refs: vec![SecretRef::parse("env:API_TOKEN").expect("valid secret ref")],
            allowed_purposes: vec![CredentialPurpose::Invocation],
            restrictions: BindingRestrictions::default(),
            cache: CacheRules::default(),
            revision: 1,
        }
    }

    #[test]
    fn binding_list_empty_then_populated() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            data_field(&handle_binding_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(0)
        );

        registry.register_binding(sample_binding("b1"));
        assert_eq!(
            data_field(&handle_binding_list(&registry))
                .as_array()
                .map(|a| a.len()),
            Some(1)
        );
    }

    #[test]
    fn binding_get_returns_metadata_without_secrets() {
        let registry = InMemoryRegistry::new();
        registry.register_binding(sample_binding("b1"));

        let resp = handle_binding_get(&registry, "b1");
        assert_eq!(resp.status(), 200);

        let data = data_field(&resp);
        assert_eq!(data.get("id").and_then(|v| v.as_str()), Some("b1"));
        // Only the opaque reference is exposed, never a resolved secret value.
        let refs = data
            .get("secretRefs")
            .and_then(|v| v.as_array())
            .expect("secretRefs present");
        assert_eq!(refs.first().and_then(|v| v.as_str()), Some("env:API_TOKEN"));
        let serialized = serde_json::to_string(&data).unwrap_or_default();
        assert!(!serialized.contains("REDACTED"));
    }

    #[test]
    fn binding_get_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(handle_binding_get(&registry, "nope").status(), 404);
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
            available: true,
            status_message: None,
            credential_bindings: HashMap::new(),
        });

        let list_resp = handle_forward_list(&registry);
        assert_eq!(list_resp.status(), 200);
        assert_eq!(data_field(&list_resp).as_array().map(|a| a.len()), Some(1));

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
            available: true,
            status_message: None,
            credential_bindings: HashMap::new(),
        });
        assert_eq!(
            handle_forward_delete(&registry, &test_broker(), "del-fwd").status(),
            200
        );
        assert_eq!(handle_forward_get(&registry, "del-fwd").status(), 404);
    }

    #[test]
    fn forward_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            handle_forward_delete(&registry, &test_broker(), "nope").status(),
            404
        );
    }

    /// Build a broker holding one cached credential for `forward_id`.
    fn broker_with_cached_forward(forward_id: &str) -> CredentialBroker {
        use std::sync::Arc;
        use wanaku_types::credentials::binding::UseScope;
        use wanaku_types::credentials::fake_resolver::FakeResolver;
        use wanaku_types::credentials::{
            BindingRestrictions, CacheRules, CredentialBinding, CredentialPurpose,
            InjectionMechanism, NormalizedOrigin, ResolverRegistry, SecretRef,
        };

        let resolvers = ResolverRegistry::new()
            .with_resolver(Arc::new(FakeResolver::new().with_value("token", "s3cr3t")));
        let broker = CredentialBroker::new(resolvers);
        let binding = CredentialBinding {
            id: "b1".to_owned(),
            forward_id: forward_id.to_owned(),
            origin: NormalizedOrigin::from_address("https://api.example.com")
                .expect("valid origin"),
            mechanism: InjectionMechanism::Bearer,
            secret_refs: vec![SecretRef::parse("fake:token").expect("valid ref")],
            allowed_purposes: vec![CredentialPurpose::Invocation],
            restrictions: BindingRestrictions::default(),
            // A TTL is required for the credential to be cached at all.
            cache: CacheRules {
                max_ttl_seconds: Some(60),
                ..CacheRules::default()
            },
            revision: 1,
        };

        let runtime = tokio::runtime::Builder::new_current_thread()
            .build()
            .expect("runtime");
        runtime.block_on(async {
            broker
                .broker(
                    &binding,
                    forward_id,
                    "https://api.example.com/mcp",
                    CredentialPurpose::Invocation,
                    &UseScope::default(),
                )
                .await
                .expect("brokerage succeeds");
        });
        assert_eq!(broker.cache().len(), 1, "cache should be seeded");
        broker
    }

    fn cached_forward(name: &str) -> ForwardEntry {
        ForwardEntry {
            name: name.to_owned(),
            address: "https://api.example.com/mcp".to_owned(),
            namespace: None,
            server_info: None,
            labels: HashMap::new(),
            available: true,
            status_message: None,
            credential_bindings: HashMap::new(),
        }
    }

    #[test]
    fn forward_delete_invalidates_cached_credentials() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(cached_forward("cred-fwd"));
        let broker = broker_with_cached_forward("cred-fwd");

        assert_eq!(
            handle_forward_delete(&registry, &broker, "cred-fwd").status(),
            200
        );

        // No cached secret may outlive the forward it was brokered for.
        assert!(broker.cache().is_empty());
    }

    #[test]
    fn revalidate_forward_credentials_drops_cached_entries() {
        let registry = InMemoryRegistry::new();
        let broker = broker_with_cached_forward("cred-fwd");

        super::revalidate_forward_credentials(&registry, &broker, &cached_forward("cred-fwd"));

        // An address change must invalidate credentials cached for the forward.
        assert!(broker.cache().is_empty());
    }

    // ---- Statistics handler ----

    #[test]
    fn statistics_empty_registry() {
        let registry = InMemoryRegistry::new();
        let resp = handle_statistics(&registry);
        assert_eq!(resp.status(), 200);

        let data = data_field(&resp);
        assert_eq!(data.get("toolsCount").and_then(|v| v.as_i64()), Some(0));
        assert_eq!(data.get("resourcesCount").and_then(|v| v.as_i64()), Some(0));
        assert_eq!(data.get("promptsCount").and_then(|v| v.as_i64()), Some(0));
        assert_eq!(data.get("forwardsCount").and_then(|v| v.as_i64()), Some(0));
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
            available: true,
            status_message: None,
            credential_bindings: HashMap::new(),
        });

        let data = data_field(&handle_statistics(&registry));
        assert_eq!(data.get("toolsCount").and_then(|v| v.as_i64()), Some(2));
        assert_eq!(data.get("resourcesCount").and_then(|v| v.as_i64()), Some(1));
        assert_eq!(data.get("promptsCount").and_then(|v| v.as_i64()), Some(1));
        assert_eq!(data.get("forwardsCount").and_then(|v| v.as_i64()), Some(1));
    }

    // ---- Serialization format (camelCase) ----

    #[test]
    fn tool_serializes_camel_case_keys() {
        let registry = InMemoryRegistry::new();
        let mut tool = test_tool("cc");
        tool.input_schema =
            serde_json::json!({"type":"object","properties":{"msg":{"type":"string"}}});
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
        tool.forward_id = Some("remote-forward".to_owned());
        registry.register_tool(tool);

        let data = data_field(&handle_tool_get(&registry, "cc-opt"));
        assert_eq!(
            data.get("forwardId").and_then(|v| v.as_str()),
            Some("remote-forward")
        );
        assert!(data.get("forward_id").is_none());
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

    #[test]
    fn info_returns_name_and_version() {
        let resp = handle_info();
        assert_eq!(resp.status(), 200);
        let data = data_field(&resp);
        assert!(data.get("name").and_then(|v| v.as_str()).is_some());
        assert!(data.get("version").and_then(|v| v.as_str()).is_some());
    }
}
