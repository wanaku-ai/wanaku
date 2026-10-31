use std::collections::{HashMap, HashSet};

use http::{Response, StatusCode};
use tracing::{info, warn};

use crate::http_response::{json_err, json_ok};
use wanaku_infra::credentials::{
    CredentialAuditSink, CredentialBroker, CredentialRedactor, DiscoveryExchange,
    resolve_discovery_headers,
};
use wanaku_infra::mcp_client::{ForwardDiscovery, Listing, McpClientError};
use wanaku_infra::registry::{ConditionalRemoval, InMemoryRegistry};
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

pub(super) fn handle_tool_get(
    registry: &InMemoryRegistry,
    namespace: &str,
    name: &str,
) -> Response<Vec<u8>> {
    match registry.get_tool(namespace, name) {
        Some(tool) => json_ok(&serde_json::json!(tool)),
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("tool not found: {name} in namespace {namespace}"),
        ),
    }
}

/// Enables or disables a tool. A disabled tool stays in the management API but
/// MCP clients cannot list or use it.
pub(super) fn handle_tool_set_enabled(
    registry: &InMemoryRegistry,
    namespace: &str,
    name: &str,
    enabled: bool,
) -> Response<Vec<u8>> {
    match registry
        .set_tool_enabled(namespace, name, enabled)
        .then(|| registry.get_tool(namespace, name))
        .flatten()
    {
        Some(entry) => {
            info!(tool = %name, namespace = %namespace, enabled, "changed tool state via management API");
            json_ok(&serde_json::json!(entry))
        }
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("tool not found: {name} in namespace {namespace}"),
        ),
    }
}

pub(super) fn handle_tool_delete(
    registry: &InMemoryRegistry,
    namespace: &str,
    name: &str,
) -> Response<Vec<u8>> {
    if registry.remove_tool(namespace, name) {
        info!(tool = %name, namespace = %namespace, "removed tool via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(
            StatusCode::NOT_FOUND,
            &format!("tool not found: {name} in namespace {namespace}"),
        )
    }
}

pub(super) fn handle_resource_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let resources = registry.list_resources();
    json_ok(&serde_json::json!(resources))
}

pub(super) fn handle_resource_get(
    registry: &InMemoryRegistry,
    namespace: &str,
    name: &str,
) -> Response<Vec<u8>> {
    match registry.get_resource(namespace, name) {
        Some(resource) => json_ok(&serde_json::json!(resource)),
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("resource not found: {name} in namespace {namespace}"),
        ),
    }
}

/// Enables or disables a resource. A disabled resource stays in the management API but
/// MCP clients cannot list or use it.
pub(super) fn handle_resource_set_enabled(
    registry: &InMemoryRegistry,
    namespace: &str,
    name: &str,
    enabled: bool,
) -> Response<Vec<u8>> {
    match registry
        .set_resource_enabled(namespace, name, enabled)
        .then(|| registry.get_resource(namespace, name))
        .flatten()
    {
        Some(entry) => {
            info!(resource = %name, namespace = %namespace, enabled, "changed resource state via management API");
            json_ok(&serde_json::json!(entry))
        }
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("resource not found: {name} in namespace {namespace}"),
        ),
    }
}

pub(super) fn handle_resource_delete(
    registry: &InMemoryRegistry,
    namespace: &str,
    name: &str,
) -> Response<Vec<u8>> {
    if registry.remove_resource(namespace, name) {
        info!(resource = %name, namespace = %namespace, "removed resource via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(
            StatusCode::NOT_FOUND,
            &format!("resource not found: {name} in namespace {namespace}"),
        )
    }
}

pub(super) fn handle_prompt_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let prompts = registry.list_prompts();
    json_ok(&serde_json::json!(prompts))
}

pub(super) fn handle_prompt_get(
    registry: &InMemoryRegistry,
    namespace: &str,
    name: &str,
) -> Response<Vec<u8>> {
    match registry.get_prompt(namespace, name) {
        Some(prompt) => json_ok(&serde_json::json!(prompt)),
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("prompt not found: {name} in namespace {namespace}"),
        ),
    }
}

/// Enables or disables a prompt. A disabled prompt stays in the management API but
/// MCP clients cannot list or use it.
pub(super) fn handle_prompt_set_enabled(
    registry: &InMemoryRegistry,
    namespace: &str,
    name: &str,
    enabled: bool,
) -> Response<Vec<u8>> {
    match registry
        .set_prompt_enabled(namespace, name, enabled)
        .then(|| registry.get_prompt(namespace, name))
        .flatten()
    {
        Some(entry) => {
            info!(prompt = %name, namespace = %namespace, enabled, "changed prompt state via management API");
            json_ok(&serde_json::json!(entry))
        }
        None => json_err(
            StatusCode::NOT_FOUND,
            &format!("prompt not found: {name} in namespace {namespace}"),
        ),
    }
}

pub(super) fn handle_prompt_delete(
    registry: &InMemoryRegistry,
    namespace: &str,
    name: &str,
) -> Response<Vec<u8>> {
    if registry.remove_prompt(namespace, name) {
        info!(prompt = %name, namespace = %namespace, "removed prompt via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(
            StatusCode::NOT_FOUND,
            &format!("prompt not found: {name} in namespace {namespace}"),
        )
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

    if registry.get_namespace(&namespace.name).is_some() {
        return json_err(
            StatusCode::CONFLICT,
            &format!("namespace already exists: {}", namespace.name),
        );
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
    if registry
        .list_agents()
        .iter()
        .any(|agent| agent.namespace == name)
    {
        return json_err(
            StatusCode::CONFLICT,
            "namespace contains A2A agents; remove the agents first",
        );
    }
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
    clippy::too_many_lines,
    reason = "sequential validation before discovery"
)]
pub(super) async fn handle_forward_create(
    registry: &InMemoryRegistry,
    broker: &CredentialBroker,
    audit_sink: Option<&dyn CredentialAuditSink>,
    body: &str,
    replace: bool,
) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, "forward create request body");
    let forward: ForwardEntry = match serde_json::from_str(body) {
        Ok(f) => f,
        Err(e) => {
            warn!(error = %e, "invalid forward JSON");
            return json_err(
                StatusCode::BAD_REQUEST,
                &format!("invalid forward JSON: {e}"),
            );
        }
    };

    if let Some(namespace) = &forward.namespace
        && let Err(reason) = wanaku_types::registry::validate_namespace_name(namespace)
    {
        warn!(forward = %forward.name, namespace = %namespace, reason = %reason, "forward namespace validation failed");
        return json_err(StatusCode::BAD_REQUEST, &reason);
    }
    if let Err(e) = NormalizedOrigin::from_address(&forward.address) {
        warn!(forward = %forward.name, error = %e, "forward address validation failed");
        return json_err(StatusCode::BAD_REQUEST, &e.to_string());
    }
    // Re-registering replaces the forward's state, so callers must ask for it.
    if let Some(existing) = registry.get_forward(&forward.name) {
        if !replace {
            return json_err(
                StatusCode::CONFLICT,
                &format!("forward already exists: {}", forward.name),
            );
        }
        // A replacement that changes the upstream address must drop credentials
        // cached against the previous origin before anything is brokered anew.
        if existing.address != forward.address {
            info!(forward = %forward.name, "forward address changed; revalidating credentials");
            revalidate_forward_credentials(registry, broker, &forward);
        }
    }

    let prepared = prepare_forward_update(registry, broker, audit_sink, &forward).await;
    let (forward, counts) = apply_forward_update(registry, prepared);
    info!(forward = %forward.name, address = %forward.address, "registered forward via management API");

    json_ok(&serde_json::json!({
        "forward": &forward,
        "tools_discovered": counts.tools,
        "resources_discovered": counts.resources,
        "prompts_discovered": counts.prompts,
    }))
}

/// Whether a forward create asks to replace an existing forward
/// (`replace=true` query parameter).
pub(super) fn replace_requested(query: Option<&str>) -> bool {
    form_urlencoded::parse(query.unwrap_or_default().as_bytes())
        .any(|(key, value)| key == "replace" && value == "true")
}

/// The namespace of a tool, resource, or prompt route (`namespace` query
/// parameter). Without the parameter, the namespace is `default`.
pub(super) fn namespace_param(query: Option<&str>) -> String {
    form_urlencoded::parse(query.unwrap_or_default().as_bytes())
        .find(|(key, _)| key == "namespace")
        .map_or_else(
            || wanaku_types::registry::DEFAULT_NAMESPACE.to_owned(),
            |(_, value)| value.into_owned(),
        )
}

/// Build the label precondition of a conditional delete from the
/// `label.<key>=<value>` query parameters.
///
/// The precondition holds when every given label is present with the given
/// value. Without `label.*` parameters, it always holds.
pub(super) fn label_precondition(query: Option<&str>) -> impl Fn(&HashMap<String, String>) -> bool {
    let required: Vec<(String, String)> =
        form_urlencoded::parse(query.unwrap_or_default().as_bytes())
            .filter_map(|(key, value)| {
                key.strip_prefix("label.")
                    .map(|key| (key.to_owned(), value.into_owned()))
            })
            .collect();
    move |labels| {
        required
            .iter()
            .all(|(key, value)| labels.get(key) == Some(value))
    }
}

pub(super) fn handle_forward_delete(
    registry: &InMemoryRegistry,
    broker: &CredentialBroker,
    name: &str,
    query: Option<&str>,
) -> Response<Vec<u8>> {
    let precondition = label_precondition(query);
    let fwd = match registry.remove_forward_if(name, |fwd| precondition(&fwd.labels)) {
        ConditionalRemoval::Removed(fwd) => fwd,
        ConditionalRemoval::NotFound => {
            return json_err(StatusCode::NOT_FOUND, &format!("forward not found: {name}"));
        }
        ConditionalRemoval::PreconditionFailed => {
            return json_err(
                StatusCode::CONFLICT,
                &format!("forward {name} label precondition failed"),
            );
        }
    };

    // Drop credentials cached for the forward so no secret outlives it.
    broker.cache().invalidate_forward(fwd.forward_id());
    registry.remove_tools_owned_by(fwd.forward_id());
    registry.remove_resources_owned_by(fwd.forward_id());
    registry.remove_prompts_owned_by(fwd.forward_id());

    info!(forward = %name, "removed forward via management API");
    json_ok(&serde_json::json!({"removed": name}))
}

pub(super) async fn handle_forward_refresh(
    registry: &InMemoryRegistry,
    broker: &CredentialBroker,
    audit_sink: Option<&dyn CredentialAuditSink>,
    name: &str,
) -> Response<Vec<u8>> {
    let Some(forward) = registry.get_forward(name) else {
        return json_err(StatusCode::NOT_FOUND, &format!("forward not found: {name}"));
    };

    // Discovery runs before any registry change. A failed or incomplete
    // discovery keeps the previous capability set and marks the forward
    // unavailable.
    let prepared = prepare_forward_update(registry, broker, audit_sink, &forward).await;
    let (_, counts) = apply_forward_update(registry, prepared);

    info!(forward = %name, tools_discovered = counts.tools, resources_discovered = counts.resources, prompts_discovered = counts.prompts, "refreshed forward");
    json_ok(
        &serde_json::json!({"refreshed": name, "tools_discovered": counts.tools, "resources_discovered": counts.resources, "prompts_discovered": counts.prompts}),
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

/// Build the redacted status message for a failed discovery.
///
/// When the upstream requires authentication and the forward has no discovery
/// binding, the message tells the operator where to configure one.
fn discovery_failure_message(
    forward: &ForwardEntry,
    redactor: &CredentialRedactor,
    error: &McpClientError,
) -> String {
    let message = redactor.redact(&error.to_string());
    match error {
        McpClientError::AuthRequired { .. }
            if forward.binding_for(CredentialPurpose::Discovery).is_none() =>
        {
            format!(
                "{message}. Add a credential binding for discovery to this forward in wanaku.yaml"
            )
        }
        _ => message,
    }
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
        tools: redact_listing(redactor, discovery.tools),
        resources: redact_listing(redactor, discovery.resources),
        resource_templates: redact_listing(redactor, discovery.resource_templates),
        prompts: redact_listing(redactor, discovery.prompts),
    }
}

fn redact_listing(redactor: &CredentialRedactor, listing: Listing) -> Listing {
    match listing {
        Listing::Complete(items) => Listing::Complete(redactor.redact_json_each(&items)),
        Listing::Unsupported => Listing::Unsupported,
        Listing::Incomplete(reason) => Listing::Incomplete(redactor.redact(&reason)),
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

/// Names of the forward-sourced entries that an operator disabled.
///
/// Discovery replaces the entries of a forward. Capture this set before
/// discovery so that rediscovered entries stay disabled.
#[derive(Default)]
struct DisabledEntries {
    tools: HashSet<String>,
    resources: HashSet<String>,
    prompts: HashSet<String>,
}

impl DisabledEntries {
    fn capture(registry: &InMemoryRegistry, forward_id: &str) -> Self {
        let owned = |id: Option<&str>| id == Some(forward_id);
        Self {
            tools: registry
                .list_tools()
                .into_iter()
                .filter(|t| !t.enabled && owned(t.forward_id.as_deref()))
                .map(|t| t.name)
                .collect(),
            resources: registry
                .list_resources()
                .into_iter()
                .filter(|r| !r.enabled && owned(r.forward_id.as_deref()))
                .map(|r| r.name)
                .collect(),
            prompts: registry
                .list_prompts()
                .into_iter()
                .filter(|p| !p.enabled && owned(p.forward_id.as_deref()))
                .map(|p| p.name)
                .collect(),
        }
    }
}

/// Discover a forward and publish the result.
///
/// Startup and the background reconnect loop use this function. It shares
/// [`prepare_forward_update`] and [`apply_forward_update`] with the forward
/// create and refresh endpoints.
pub async fn discover_and_update_forward(
    registry: &InMemoryRegistry,
    broker: &CredentialBroker,
    audit_sink: Option<&dyn CredentialAuditSink>,
    forward: &ForwardEntry,
) {
    let prepared = prepare_forward_update(registry, broker, audit_sink, forward).await;
    let (_, counts) = apply_forward_update(registry, prepared);
    info!(
        forward = %forward.name,
        tools_discovered = counts.tools,
        resources_discovered = counts.resources,
        prompts_discovered = counts.prompts,
        "forward discovery finished"
    );
}

/// A forward status and capability set that is ready to publish.
///
/// Preparation reads the registry but does not change it. Only
/// [`apply_forward_update`] publishes a prepared update.
struct PreparedForwardUpdate {
    /// The forward with its intended availability, status message, and server
    /// identity.
    forward: ForwardEntry,
    /// The complete replacement capability set. `None` when discovery failed or
    /// was incomplete: the previous capability set then stays in place.
    capabilities: Option<ForwardCapabilities>,
}

/// Validated registry entries for every capability of one forward.
struct ForwardCapabilities {
    tools: Vec<ToolEntry>,
    resources: Vec<ResourceEntry>,
    prompts: Vec<PromptEntry>,
}

/// Number of entries that a forward update published.
#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
struct PublishedCounts {
    tools: usize,
    resources: usize,
    prompts: usize,
}

impl PreparedForwardUpdate {
    /// An update that marks the forward unavailable and keeps its previous
    /// capability set.
    fn failed(forward: &ForwardEntry, message: String) -> Self {
        let mut forward = forward.clone();
        forward.available = false;
        forward.status_message = Some(message);
        Self {
            forward,
            capabilities: None,
        }
    }
}

/// Run discovery for a forward and prepare the update, without changing the
/// registry.
async fn prepare_forward_update(
    registry: &InMemoryRegistry,
    broker: &CredentialBroker,
    audit_sink: Option<&dyn CredentialAuditSink>,
    forward: &ForwardEntry,
) -> PreparedForwardUpdate {
    let DiscoveryExchange { headers, redactor } =
        match discovery_headers_or_status(registry, broker, audit_sink, forward).await {
            Ok(exchange) => exchange,
            Err(message) => return PreparedForwardUpdate::failed(forward, message),
        };

    match wanaku_infra::mcp_client::discover_forward(&forward.address, headers).await {
        Ok(discovery) => prepare_from_discovery(forward, redact_discovery(&redactor, discovery)),
        // Redact any injected discovery secret the upstream echoed into its
        // error before it reaches the operator or the logs.
        Err(e) => PreparedForwardUpdate::failed(
            forward,
            discovery_failure_message(forward, &redactor, &e),
        ),
    }
}

/// Convert a discovery result into a prepared update.
///
/// An unsupported listing gives no entries of that type. An incomplete listing
/// fails the whole update, so a partial list never replaces a complete one.
fn prepare_from_discovery(
    forward: &ForwardEntry,
    discovery: ForwardDiscovery,
) -> PreparedForwardUpdate {
    let (tools, resources, templates, prompts) = match (
        listing_items(discovery.tools),
        listing_items(discovery.resources),
        listing_items(discovery.resource_templates),
        listing_items(discovery.prompts),
    ) {
        (Ok(tools), Ok(resources), Ok(templates), Ok(prompts)) => {
            (tools, resources, templates, prompts)
        }
        (Err(reason), ..) | (_, Err(reason), ..) | (_, _, Err(reason), _) | (.., Err(reason)) => {
            return PreparedForwardUpdate::failed(
                forward,
                format!("forward discovery incomplete: {reason}"),
            );
        }
    };

    let mut forward = forward.clone();
    forward.server_info = discovery.server_info;
    forward.available = true;
    forward.status_message = None;
    let capabilities = ForwardCapabilities {
        tools: tool_entries(&forward, &tools),
        resources: resource_entries(&forward, &resources, &templates),
        prompts: prompt_entries(&forward, &prompts),
    };
    PreparedForwardUpdate {
        forward,
        capabilities: Some(capabilities),
    }
}

fn listing_items(listing: Listing) -> Result<Vec<serde_json::Value>, String> {
    match listing {
        Listing::Complete(items) => Ok(items),
        Listing::Unsupported => Ok(Vec::new()),
        Listing::Incomplete(reason) => Err(reason),
    }
}

/// Publish a prepared forward update.
///
/// This is the only function that changes the registry after discovery. It
/// always stores the forward status. With a capability set, it replaces the
/// entries that the forward owns (matched by `forwardId`, not by address) and
/// keeps operator-disabled entries disabled. Without a capability set, it keeps
/// the previous entries; the forward is then unavailable, and the invocation
/// filters refuse calls to it.
///
/// Entries are keyed by namespace and name. When another owner already holds a
/// name in the forward namespace, the entry is skipped, not replaced. The
/// forward stays available and its status message names the skipped entries.
///
/// Returns the stored forward and the number of published entries.
#[expect(clippy::too_many_lines, reason = "sequential registry publication")]
fn apply_forward_update(
    registry: &InMemoryRegistry,
    update: PreparedForwardUpdate,
) -> (ForwardEntry, PublishedCounts) {
    let PreparedForwardUpdate {
        mut forward,
        capabilities,
    } = update;
    let forward_id = forward.forward_id().to_owned();
    let Some(mut capabilities) = capabilities else {
        warn!(
            forward = %forward.name,
            status = forward.status_message.as_deref().unwrap_or_default(),
            "forward discovery failed; kept the previous capability set and marked the forward unavailable"
        );
        registry.register_forward(forward.clone());
        return (forward, PublishedCounts::default());
    };

    let disabled = DisabledEntries::capture(registry, &forward_id);
    registry.remove_tools_owned_by(&forward_id);
    registry.remove_resources_owned_by(&forward_id);
    registry.remove_prompts_owned_by(&forward_id);

    for tool in &mut capabilities.tools {
        tool.enabled = !disabled.tools.contains(&tool.name);
    }
    for resource in &mut capabilities.resources {
        resource.enabled = !disabled.resources.contains(&resource.name);
    }
    for prompt in &mut capabilities.prompts {
        prompt.enabled = !disabled.prompts.contains(&prompt.name);
    }
    let discovered = PublishedCounts {
        tools: capabilities.tools.len(),
        resources: capabilities.resources.len(),
        prompts: capabilities.prompts.len(),
    };
    let skipped = SkippedEntries {
        tools: entry_names(
            registry.register_tools_unless_taken(capabilities.tools),
            |t| t.name,
        ),
        resources: entry_names(
            registry.register_resources_unless_taken(capabilities.resources),
            |r| r.name,
        ),
        prompts: entry_names(
            registry.register_prompts_unless_taken(capabilities.prompts),
            |p| p.name,
        ),
    };
    let counts = PublishedCounts {
        tools: discovered.tools.saturating_sub(skipped.tools.len()),
        resources: discovered.resources.saturating_sub(skipped.resources.len()),
        prompts: discovered.prompts.saturating_sub(skipped.prompts.len()),
    };
    if let Some(message) = skipped.status_message(&forward_namespace(&forward)) {
        warn!(forward = %forward.name, status = %message, "forward discovery skipped entries that other owners hold");
        forward.status_message = Some(message);
    }
    registry.register_forward(forward.clone());
    (forward, counts)
}

/// Names of the discovered entries that were not published because another
/// owner already holds the name in the same namespace.
struct SkippedEntries {
    tools: Vec<String>,
    resources: Vec<String>,
    prompts: Vec<String>,
}

impl SkippedEntries {
    /// The forward status message that names the skipped entries, or `None`
    /// when no entry was skipped.
    fn status_message(&self, namespace: &str) -> Option<String> {
        let names: Vec<String> = [
            ("tool", &self.tools),
            ("resource", &self.resources),
            ("prompt", &self.prompts),
        ]
        .into_iter()
        .flat_map(|(kind, names)| names.iter().map(move |name| format!("{kind} {name}")))
        .collect();
        (!names.is_empty()).then(|| {
            format!(
                "skipped {} discovered entries because other owners already use the names in namespace {namespace}: {}",
                names.len(),
                names.join(", ")
            )
        })
    }
}

fn entry_names<T>(entries: Vec<T>, name: fn(T) -> String) -> Vec<String> {
    entries.into_iter().map(name).collect()
}

fn forward_namespace(forward: &ForwardEntry) -> String {
    forward
        .namespace
        .as_deref()
        .unwrap_or(wanaku_types::registry::DEFAULT_NAMESPACE)
        .to_owned()
}

/// Read a required, non-empty string field of a discovered item.
fn required_str<'a>(item: &'a serde_json::Value, key: &str) -> Option<&'a str> {
    item.get(key)
        .and_then(|v| v.as_str())
        .map(str::trim)
        .filter(|v| !v.is_empty())
}

/// Read an optional string field of a discovered item.
fn optional_str<'a>(item: &'a serde_json::Value, key: &str) -> &'a str {
    item.get(key).and_then(|v| v.as_str()).unwrap_or_default()
}

/// Convert discovered tools into registry entries. Items without a name are
/// skipped.
fn tool_entries(forward: &ForwardEntry, tools: &[serde_json::Value]) -> Vec<ToolEntry> {
    let namespace = forward_namespace(forward);
    tools
        .iter()
        .filter_map(|tool_json| {
            let Some(name) = required_str(tool_json, "name") else {
                warn!(forward = %forward.name, "skipping forwarded tool with missing or empty name");
                return None;
            };
            info!(tool = %name, forward = %forward.name, "discovered forwarded tool");
            Some(ToolEntry {
                name: name.to_owned(),
                description: optional_str(tool_json, "description").to_owned(),
                uri: forward.address.clone(),
                type_: MCP_FORWARD_TYPE.to_owned(),
                input_schema: tool_json
                    .get("inputSchema")
                    .cloned()
                    .unwrap_or(serde_json::json!({"type": "object"})),
                labels: forward.labels.clone(),
                id: None,
                namespace: Some(namespace.clone()),
                forward_id: Some(forward.forward_id().to_owned()),
                enabled: true,
            })
        })
        .collect()
}

/// Convert discovered resources and resource templates into registry entries.
/// Items without a name or a URI (or URI template) are skipped.
#[expect(clippy::too_many_lines, reason = "resource and template conversion")]
fn resource_entries(
    forward: &ForwardEntry,
    resources: &[serde_json::Value],
    templates: &[serde_json::Value],
) -> Vec<ResourceEntry> {
    let namespace = forward_namespace(forward);
    let entry = |item: &serde_json::Value, name: &str, location: &str, labels| ResourceEntry {
        name: name.to_owned(),
        description: optional_str(item, "description").to_owned(),
        location: location.to_owned(),
        type_: MCP_FORWARD_TYPE.to_owned(),
        mime_type: optional_str(item, "mimeType").to_owned(),
        labels,
        id: None,
        namespace: Some(namespace.clone()),
        forward_id: Some(forward.forward_id().to_owned()),
        enabled: true,
    };

    let plain = resources.iter().filter_map(|res_json| {
        match (required_str(res_json, "name"), required_str(res_json, "uri")) {
            (Some(name), Some(uri)) => {
                info!(resource = %name, forward = %forward.name, "discovered forwarded resource");
                Some(entry(res_json, name, uri, HashMap::new()))
            }
            _ => {
                warn!(forward = %forward.name, "skipping forwarded resource with missing or empty name or uri");
                None
            }
        }
    });
    let templated = templates.iter().filter_map(|tmpl_json| {
        match (required_str(tmpl_json, "name"), required_str(tmpl_json, "uriTemplate")) {
            (Some(name), Some(uri_template)) => {
                info!(template = %name, uri_template = %uri_template, forward = %forward.name, "discovered forwarded resource template");
                let labels = HashMap::from([(
                    wanaku_types::registry::IS_TEMPLATE_LABEL.to_owned(),
                    "true".to_owned(),
                )]);
                Some(entry(tmpl_json, name, uri_template, labels))
            }
            _ => {
                warn!(forward = %forward.name, "skipping forwarded template with missing or empty name or uriTemplate");
                None
            }
        }
    });
    plain.chain(templated).collect()
}

/// Convert discovered prompts into registry entries. Items without a name are
/// skipped.
#[expect(clippy::too_many_lines, reason = "prompt and argument conversion")]
fn prompt_entries(forward: &ForwardEntry, prompts: &[serde_json::Value]) -> Vec<PromptEntry> {
    let namespace = forward_namespace(forward);
    prompts
        .iter()
        .filter_map(|prompt_json| {
            let Some(name) = required_str(prompt_json, "name") else {
                warn!(forward = %forward.name, "skipping forwarded prompt with missing or empty name");
                return None;
            };
            let arguments = prompt_json
                .get("arguments")
                .and_then(|a| a.as_array())
                .map(|arr| {
                    arr.iter()
                        .filter_map(|arg| {
                            Some(wanaku_types::registry::PromptArgument {
                                name: arg.get("name")?.as_str()?.to_owned(),
                                description: optional_str(arg, "description").to_owned(),
                                required: arg
                                    .get("required")
                                    .and_then(serde_json::Value::as_bool)
                                    .unwrap_or(false),
                            })
                        })
                        .collect()
                })
                .unwrap_or_default();
            info!(prompt = %name, forward = %forward.name, "discovered forwarded prompt");
            Some(PromptEntry {
                name: name.to_owned(),
                description: optional_str(prompt_json, "description").to_owned(),
                arguments,
                messages: Vec::new(),
                id: None,
                namespace: Some(namespace.clone()),
                forward_id: Some(forward.forward_id().to_owned()),
                enabled: true,
            })
        })
        .collect()
}

#[cfg(test)]
mod forward_helpers_tests {
    use super::*;
    use wanaku_infra::registry::InMemoryRegistry;
    use wanaku_types::registry::{PromptRegistry, ToolRegistry};

    fn forward_at(name: &str, address: &str) -> ForwardEntry {
        serde_json::from_value(serde_json::json!({"name": name, "address": address}))
            .expect("forward")
    }

    fn complete(items: &[serde_json::Value]) -> Listing {
        Listing::Complete(items.to_vec())
    }

    fn discovery(tools: Listing, resources: Listing, prompts: Listing) -> ForwardDiscovery {
        ForwardDiscovery {
            server_info: None,
            tools,
            resources,
            resource_templates: Listing::Unsupported,
            prompts,
        }
    }

    /// Publish a successful discovery of `tools` and `prompts` for `forward`.
    fn publish(
        registry: &InMemoryRegistry,
        forward: &ForwardEntry,
        tools: &[serde_json::Value],
        prompts: &[serde_json::Value],
    ) -> PublishedCounts {
        let found = discovery(complete(tools), Listing::Unsupported, complete(prompts));
        apply_forward_update(registry, prepare_from_discovery(forward, found)).1
    }

    fn tool_names(registry: &InMemoryRegistry) -> Vec<String> {
        let mut names: Vec<_> = registry.list_tools().into_iter().map(|t| t.name).collect();
        names.sort();
        names
    }

    #[test]
    #[expect(clippy::too_many_lines, reason = "covers every capability type")]
    fn conversion_builds_entries_and_skips_invalid_items() {
        let forward = forward_at("remote", "http://remote:8080/mcp");
        let tools = tool_entries(
            &forward,
            &[
                serde_json::json!({"name": " echo ", "description": "d"}),
                serde_json::json!({"name": ""}),
                serde_json::json!({"description": "no name"}),
            ],
        );
        assert_eq!(tools.len(), 1);
        assert_eq!(tools[0].name, "echo");
        assert_eq!(tools[0].uri, "http://remote:8080/mcp");
        assert_eq!(tools[0].forward_id.as_deref(), Some("remote"));
        assert_eq!(tools[0].namespace.as_deref(), Some("default"));
        assert_eq!(tools[0].input_schema, serde_json::json!({"type": "object"}));

        let resources = resource_entries(
            &forward,
            &[
                serde_json::json!({"name": "doc", "uri": "file:///doc", "mimeType": "text/plain"}),
                serde_json::json!({"name": "no-uri"}),
            ],
            &[serde_json::json!({"name": "tmpl", "uriTemplate": "file:///{id}"})],
        );
        assert_eq!(resources.len(), 2);
        assert_eq!(resources[0].location, "file:///doc");
        assert!(resources[0].labels.is_empty());
        assert_eq!(resources[1].location, "file:///{id}");
        assert_eq!(
            resources[1]
                .labels
                .get(wanaku_types::registry::IS_TEMPLATE_LABEL)
                .map(String::as_str),
            Some("true")
        );

        let prompts = prompt_entries(
            &forward,
            &[serde_json::json!({
                "name": "greet",
                "arguments": [{"name": "who", "required": true}, {"description": "no name"}]
            })],
        );
        assert_eq!(prompts.len(), 1);
        assert_eq!(prompts[0].arguments.len(), 1);
        assert!(prompts[0].arguments[0].required);
    }

    #[test]
    fn preparation_does_not_mutate_the_registry() {
        let registry = InMemoryRegistry::new();
        let forward = forward_at("remote", "http://remote:8080/mcp");
        let found = discovery(
            complete(&[serde_json::json!({"name": "echo"})]),
            Listing::Unsupported,
            Listing::Unsupported,
        );
        let prepared = prepare_from_discovery(&forward, found);
        assert!(prepared.forward.available);
        assert!(registry.list_forwards().is_empty());
        assert!(registry.list_tools().is_empty());
    }

    #[test]
    fn unsupported_optional_listing_is_not_a_failure() {
        let registry = InMemoryRegistry::new();
        let forward = forward_at("remote", "http://remote:8080/mcp");
        let counts = publish(
            &registry,
            &forward,
            &[serde_json::json!({"name": "echo"})],
            &[],
        );
        assert_eq!(
            counts,
            PublishedCounts {
                tools: 1,
                resources: 0,
                prompts: 0
            }
        );
        let stored = registry.get_forward("remote").expect("forward");
        assert!(stored.available);
        assert!(stored.status_message.is_none());
    }

    #[test]
    fn incomplete_listing_keeps_previous_capabilities_and_marks_unavailable() {
        let registry = InMemoryRegistry::new();
        let forward = forward_at("remote", "http://remote:8080/mcp");
        let tools = [
            serde_json::json!({"name": "a"}),
            serde_json::json!({"name": "b"}),
        ];
        publish(
            &registry,
            &forward,
            &tools,
            &[serde_json::json!({"name": "p"})],
        );

        // The second page of prompts failed: the replacement must not publish
        // the partial tool or prompt list.
        let found = discovery(
            complete(&[serde_json::json!({"name": "a"})]),
            Listing::Unsupported,
            Listing::Incomplete("prompts/list failed: page 2".to_owned()),
        );
        let (_, counts) = apply_forward_update(&registry, prepare_from_discovery(&forward, found));

        assert_eq!(counts, PublishedCounts::default());
        assert_eq!(tool_names(&registry), ["a", "b"]);
        assert!(registry.get_prompt("default", "p").is_some());
        let stored = registry.get_forward("remote").expect("forward");
        assert!(!stored.available);
        let status = stored.status_message.expect("status message");
        assert!(status.contains("incomplete"), "{status}");
        assert!(status.contains("prompts/list"), "{status}");
    }

    #[test]
    fn successful_rediscovery_replaces_stale_entries_and_restores_availability() {
        let registry = InMemoryRegistry::new();
        let mut forward = forward_at("remote", "http://remote:8080/mcp");
        publish(
            &registry,
            &forward,
            &[serde_json::json!({"name": "old"})],
            &[],
        );
        forward.available = false;
        forward.status_message = Some("down".to_owned());
        registry.register_forward(forward.clone());

        publish(
            &registry,
            &forward,
            &[serde_json::json!({"name": "new"})],
            &[],
        );

        assert_eq!(tool_names(&registry), ["new"]);
        let stored = registry.get_forward("remote").expect("forward");
        assert!(stored.available);
        assert!(stored.status_message.is_none());
    }

    #[test]
    fn replacement_keeps_other_forwards_with_the_same_address() {
        let registry = InMemoryRegistry::new();
        let address = "http://shared:8080/mcp";
        let first = forward_at("first", address);
        let second = forward_at("second", address);
        publish(
            &registry,
            &first,
            &[serde_json::json!({"name": "first-tool"})],
            &[],
        );
        publish(
            &registry,
            &second,
            &[serde_json::json!({"name": "second-tool"})],
            &[],
        );

        publish(&registry, &first, &[], &[]);

        assert_eq!(tool_names(&registry), ["second-tool"]);
        let kept = registry.get_tool("default", "second-tool").expect("tool");
        assert_eq!(kept.forward_id.as_deref(), Some("second"));
    }

    fn forward_in(name: &str, namespace: &str) -> ForwardEntry {
        serde_json::from_value(serde_json::json!({
            "name": name,
            "address": "http://shared:8080/mcp",
            "namespace": namespace,
        }))
        .expect("forward")
    }

    fn owner(registry: &InMemoryRegistry, namespace: &str, name: &str) -> Option<String> {
        registry
            .get_tool(namespace, name)
            .and_then(|tool| tool.forward_id)
    }

    #[test]
    fn same_upstream_is_exposed_in_two_namespaces() {
        let registry = InMemoryRegistry::new();
        let broker = CredentialBroker::new(wanaku_types::credentials::ResolverRegistry::new());
        let tools = [serde_json::json!({"name": "echo"})];
        let prompts = [serde_json::json!({"name": "greet"})];
        publish(&registry, &forward_in("a", "default"), &tools, &prompts);
        publish(&registry, &forward_in("b", "other"), &tools, &prompts);

        assert_eq!(owner(&registry, "default", "echo").as_deref(), Some("a"));
        assert_eq!(owner(&registry, "other", "echo").as_deref(), Some("b"));
        assert_eq!(registry.list_tools_in_namespace("default").len(), 1);
        assert_eq!(registry.list_tools_in_namespace("other").len(), 1);
        assert!(
            registry
                .get_forward("b")
                .and_then(|f| f.status_message)
                .is_none()
        );

        let response = handle_forward_delete(&registry, &broker, "b", None);
        assert_eq!(response.status(), StatusCode::OK);

        assert_eq!(owner(&registry, "default", "echo").as_deref(), Some("a"));
        assert!(registry.get_tool("other", "echo").is_none());
        assert!(registry.get_prompt("default", "greet").is_some());
        assert!(registry.get_prompt("other", "greet").is_none());
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "covers conflict, refresh, and removal"
    )]
    fn same_name_in_one_namespace_is_not_overwritten_and_is_reported() {
        let registry = InMemoryRegistry::new();
        let broker = CredentialBroker::new(wanaku_types::credentials::ResolverRegistry::new());
        let first = forward_at("first", "http://one:8080/mcp");
        let second = forward_at("second", "http://two:8080/mcp");
        publish(
            &registry,
            &first,
            &[serde_json::json!({"name": "echo"})],
            &[],
        );

        let counts = publish(
            &registry,
            &second,
            &[
                serde_json::json!({"name": "echo"}),
                serde_json::json!({"name": "only-second"}),
            ],
            &[],
        );

        assert_eq!(counts.tools, 1);
        assert_eq!(
            owner(&registry, "default", "echo").as_deref(),
            Some("first")
        );
        assert_eq!(
            owner(&registry, "default", "only-second").as_deref(),
            Some("second")
        );
        let stored = registry.get_forward("second").expect("forward");
        assert!(stored.available);
        let status = stored.status_message.expect("conflict status");
        assert!(status.contains("tool echo"), "{status}");
        assert!(status.contains("namespace default"), "{status}");

        // A refresh of the first forward keeps its entry; removing the second
        // forward does not delete the entry of the first forward.
        publish(
            &registry,
            &first,
            &[serde_json::json!({"name": "echo"})],
            &[],
        );
        assert_eq!(
            owner(&registry, "default", "echo").as_deref(),
            Some("first")
        );
        let response = handle_forward_delete(&registry, &broker, "second", None);
        assert_eq!(response.status(), StatusCode::OK);
        assert_eq!(
            owner(&registry, "default", "echo").as_deref(),
            Some("first")
        );
        assert!(registry.get_tool("default", "only-second").is_none());
    }

    #[test]
    fn freed_name_is_published_on_the_next_refresh() {
        let registry = InMemoryRegistry::new();
        let broker = CredentialBroker::new(wanaku_types::credentials::ResolverRegistry::new());
        let first = forward_at("first", "http://one:8080/mcp");
        let second = forward_at("second", "http://two:8080/mcp");
        let tools = [serde_json::json!({"name": "echo"})];
        publish(&registry, &first, &tools, &[]);
        publish(&registry, &second, &tools, &[]);

        let response = handle_forward_delete(&registry, &broker, "first", None);
        assert_eq!(response.status(), StatusCode::OK);
        assert!(registry.get_tool("default", "echo").is_none());

        publish(&registry, &second, &tools, &[]);
        assert_eq!(
            owner(&registry, "default", "echo").as_deref(),
            Some("second")
        );
        let stored = registry.get_forward("second").expect("forward");
        assert!(stored.status_message.is_none());
    }

    #[test]
    fn management_lookups_use_the_namespace_parameter() {
        let registry = InMemoryRegistry::new();
        let tools = [serde_json::json!({"name": "echo"})];
        publish(&registry, &forward_in("a", "default"), &tools, &[]);
        publish(&registry, &forward_in("b", "other"), &tools, &[]);

        assert_eq!(namespace_param(None), "default");
        assert_eq!(namespace_param(Some("replace=true")), "default");
        assert_eq!(namespace_param(Some("namespace=other")), "other");

        let body = |response: Response<Vec<u8>>| -> serde_json::Value {
            serde_json::from_slice(response.body()).expect("json")
        };
        let found = body(handle_tool_get(&registry, "other", "echo"));
        assert_eq!(found["data"]["forwardId"], "b");
        let found = body(handle_tool_get(&registry, "default", "echo"));
        assert_eq!(found["data"]["forwardId"], "a");

        let response = handle_tool_set_enabled(&registry, "other", "echo", false);
        assert_eq!(response.status(), StatusCode::OK);
        assert!(registry.get_tool_in_namespace("other", "echo").is_none());
        assert!(registry.get_tool_in_namespace("default", "echo").is_some());

        let response = handle_tool_delete(&registry, "other", "echo");
        assert_eq!(response.status(), StatusCode::OK);
        assert_eq!(
            handle_tool_get(&registry, "other", "echo").status(),
            StatusCode::NOT_FOUND
        );
        assert_eq!(owner(&registry, "default", "echo").as_deref(), Some("a"));
    }

    #[tokio::test]
    async fn failed_refresh_keeps_capabilities_and_marks_forward_unavailable() {
        let registry = InMemoryRegistry::new();
        // An unroutable address makes discovery fail fast.
        let forward = forward_at("remote", "http://127.0.0.1:1/mcp");
        publish(
            &registry,
            &forward,
            &[serde_json::json!({"name": "echo"})],
            &[],
        );
        let broker = CredentialBroker::new(wanaku_types::credentials::ResolverRegistry::new());

        let response = handle_forward_refresh(&registry, &broker, None, "remote").await;

        assert_eq!(response.status(), StatusCode::OK);
        let body: serde_json::Value = serde_json::from_slice(response.body()).expect("json");
        assert_eq!(body["data"]["refreshed"], "remote");
        assert_eq!(body["data"]["tools_discovered"], 0);
        assert_eq!(tool_names(&registry), ["echo"]);
        let stored = registry.get_forward("remote").expect("forward");
        assert!(!stored.available);
        assert!(stored.status_message.is_some());
    }

    #[test]
    fn rediscovery_keeps_disabled_entries_disabled() {
        let registry = InMemoryRegistry::new();
        let forward = forward_at("remote-forward", "http://remote:8080/mcp");
        let tools = [
            serde_json::json!({"name": "off"}),
            serde_json::json!({"name": "on"}),
        ];
        let prompts = [serde_json::json!({"name": "prompt-off"})];
        publish(&registry, &forward, &tools, &prompts);
        let response = handle_tool_set_enabled(&registry, "default", "off", false);
        assert_eq!(response.status(), StatusCode::OK);
        assert!(registry.set_prompt_enabled("default", "prompt-off", false));

        publish(&registry, &forward, &tools, &prompts);

        assert_eq!(
            registry.get_tool("default", "off").map(|t| t.enabled),
            Some(false)
        );
        assert_eq!(
            registry.get_tool("default", "on").map(|t| t.enabled),
            Some(true)
        );
        let prompt = registry.get_prompt("default", "prompt-off");
        assert_eq!(prompt.map(|p| p.enabled), Some(false));
    }

    #[test]
    fn set_enabled_on_missing_entry_is_not_found() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            handle_tool_set_enabled(&registry, "default", "missing", true).status(),
            StatusCode::NOT_FOUND
        );
        assert_eq!(
            handle_resource_set_enabled(&registry, "default", "missing", true).status(),
            StatusCode::NOT_FOUND
        );
        assert_eq!(
            handle_prompt_set_enabled(&registry, "default", "missing", true).status(),
            StatusCode::NOT_FOUND
        );
    }

    fn github_forward(bindings: &serde_json::Value) -> ForwardEntry {
        serde_json::from_value(serde_json::json!({
            "name": "github",
            "address": "https://api.githubcopilot.com/mcp/",
            "credentialBindings": bindings,
        }))
        .expect("forward")
    }

    fn auth_required() -> McpClientError {
        McpClientError::AuthRequired {
            url: "https://api.githubcopilot.com/mcp/".to_owned(),
        }
    }

    #[test]
    fn auth_failure_without_discovery_binding_points_to_wanaku_yaml() {
        let message = discovery_failure_message(
            &github_forward(&serde_json::json!({})),
            &CredentialRedactor::default(),
            &auth_required(),
        );
        assert_eq!(
            message,
            "MCP server at https://api.githubcopilot.com/mcp/ requires authentication (HTTP 401). \
             Add a credential binding for discovery to this forward in wanaku.yaml"
        );
    }

    #[test]
    fn auth_failure_with_discovery_binding_has_no_hint() {
        let message = discovery_failure_message(
            &github_forward(&serde_json::json!({"discovery": "gh-pat"})),
            &CredentialRedactor::default(),
            &auth_required(),
        );
        assert_eq!(
            message,
            "MCP server at https://api.githubcopilot.com/mcp/ requires authentication (HTTP 401)"
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
        handle_binding_get, handle_binding_list, handle_forward_create, handle_forward_delete,
        handle_forward_get, handle_forward_list, handle_info, handle_namespace_create,
        handle_namespace_delete, handle_namespace_get, handle_namespace_list,
        handle_namespace_update, handle_prompt_delete, handle_prompt_get, handle_prompt_list,
        handle_resource_delete, handle_resource_get, handle_resource_list, handle_statistics,
        handle_tool_delete, handle_tool_get, handle_tool_list,
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
            enabled: true,
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
            enabled: true,
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
            enabled: true,
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
        let resp = handle_tool_get(&registry, "default", "no-such-tool");
        assert_eq!(resp.status(), 404);
    }

    #[test]
    fn tool_delete_existing() {
        let registry = InMemoryRegistry::new();
        registry.register_tool(test_tool("to-delete"));

        let resp = handle_tool_delete(&registry, "default", "to-delete");
        assert_eq!(resp.status(), 200);

        assert_eq!(
            handle_tool_get(&registry, "default", "to-delete").status(),
            404
        );
    }

    #[test]
    fn tool_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            handle_tool_delete(&registry, "default", "ghost").status(),
            404
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
        assert_eq!(
            handle_resource_get(&registry, "default", "nope").status(),
            404
        );
    }

    #[test]
    fn resource_delete_existing() {
        let registry = InMemoryRegistry::new();
        registry.register_resource(test_resource("del-res"));

        assert_eq!(
            handle_resource_delete(&registry, "default", "del-res").status(),
            200
        );
        assert_eq!(
            handle_resource_get(&registry, "default", "del-res").status(),
            404
        );
    }

    #[test]
    fn resource_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            handle_resource_delete(&registry, "default", "nope").status(),
            404
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
        assert_eq!(
            handle_prompt_get(&registry, "default", "nope").status(),
            404
        );
    }

    #[test]
    fn prompt_delete_existing() {
        let registry = InMemoryRegistry::new();
        registry.register_prompt(test_prompt("del-p"));
        assert_eq!(
            handle_prompt_delete(&registry, "default", "del-p").status(),
            200
        );
        assert_eq!(
            handle_prompt_get(&registry, "default", "del-p").status(),
            404
        );
    }

    #[test]
    fn prompt_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            handle_prompt_delete(&registry, "default", "nope").status(),
            404
        );
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
    fn namespace_create_duplicate_returns_409_and_keeps_entry() {
        let registry = InMemoryRegistry::new();
        let body = r#"{"name":"dup-ns","labels":{"team":"a"}}"#;
        assert_eq!(handle_namespace_create(&registry, body).status(), 200);

        let resp = handle_namespace_create(&registry, r#"{"name":"dup-ns"}"#);
        assert_eq!(resp.status(), 409);
        let data = data_field(&handle_namespace_get(&registry, "dup-ns"));
        assert_eq!(data["labels"]["team"], "a");
    }

    async fn create_forward(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
        handle_forward_create(registry, &test_broker(), None, body, false).await
    }

    #[tokio::test]
    async fn forward_create_rejects_invalid_namespace() {
        let registry = InMemoryRegistry::new();
        let body = r#"{"name":"fwd","address":"http://localhost:1/mcp","namespace":"Bad_NS"}"#;
        assert_eq!(create_forward(&registry, body).await.status(), 400);
        assert!(registry.get_forward("fwd").is_none());
        assert_eq!(handle_namespace_get(&registry, "Bad_NS").status(), 404);
    }

    #[tokio::test]
    async fn forward_create_rejects_non_http_address() {
        let registry = InMemoryRegistry::new();
        for address in ["ftp://host/mcp", "host/mcp", "not a url"] {
            let body = serde_json::json!({"name": "fwd", "address": address}).to_string();
            assert_eq!(
                create_forward(&registry, &body).await.status(),
                400,
                "{address}"
            );
        }
        assert!(registry.get_forward("fwd").is_none());
    }

    #[tokio::test]
    async fn forward_create_duplicate_returns_409_and_keeps_state() {
        let registry = InMemoryRegistry::new();
        register_owned_forward(&registry, "pod-a");
        let body = r#"{"name":"owned-fwd","address":"http://other.example/mcp"}"#;

        let resp = create_forward(&registry, body).await;

        assert_eq!(resp.status(), 409);
        assert!(registry.get_tool("default", "owned-tool").is_some());
        let kept = registry.get_forward("owned-fwd").map(|f| f.address);
        assert_eq!(kept, Some(cached_forward("owned-fwd").address));
    }

    #[test]
    fn forward_create_with_replace_drops_credentials_cached_for_old_address() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(cached_forward("cred-fwd"));
        // Builds its own runtime, so this test cannot run inside `tokio::test`.
        let broker = broker_with_cached_forward("cred-fwd");
        let body = r#"{"name":"cred-fwd","address":"http://127.0.0.1:1/mcp"}"#;

        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .expect("runtime");
        let resp = runtime.block_on(handle_forward_create(&registry, &broker, None, body, true));

        assert_eq!(resp.status(), 200);
        assert!(broker.cache().is_empty());
        let address = registry.get_forward("cred-fwd").map(|f| f.address);
        assert_eq!(address.as_deref(), Some("http://127.0.0.1:1/mcp"));
    }

    #[test]
    fn replace_requested_only_for_true() {
        assert!(super::replace_requested(Some("replace=true")));
        assert!(!super::replace_requested(Some("replace=false")));
        assert!(!super::replace_requested(None));
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
            handle_forward_delete(&registry, &test_broker(), "del-fwd", None).status(),
            200
        );
        assert_eq!(handle_forward_get(&registry, "del-fwd").status(), 404);
    }

    #[test]
    fn forward_delete_nonexistent_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            handle_forward_delete(&registry, &test_broker(), "nope", None).status(),
            404
        );
    }

    /// Register a forward owned by `owner` with one discovered tool.
    fn register_owned_forward(registry: &InMemoryRegistry, owner: &str) {
        let mut forward = cached_forward("owned-fwd");
        forward
            .labels
            .insert("cic.instance".to_owned(), owner.to_owned());
        registry.register_forward(forward);
        let mut tool = test_tool("owned-tool");
        tool.forward_id = Some("owned-fwd".to_owned());
        tool.type_ = wanaku_types::registry::MCP_FORWARD_TYPE.to_owned();
        registry.register_tool(tool);
    }

    fn conditional_delete(registry: &InMemoryRegistry, query: &str) -> Response<Vec<u8>> {
        handle_forward_delete(registry, &test_broker(), "owned-fwd", Some(query))
    }

    #[test]
    fn forward_conditional_delete_with_matching_labels_removes_forward_and_tools() {
        let registry = InMemoryRegistry::new();
        register_owned_forward(&registry, "pod-a");
        let resp = conditional_delete(&registry, "label.cic.instance=pod-a&other=ignored");
        assert_eq!(resp.status(), 200);
        assert_eq!(data_field(&resp)["removed"], "owned-fwd");
        assert!(registry.get_forward("owned-fwd").is_none());
        assert!(registry.get_tool("default", "owned-tool").is_none());
    }

    #[test]
    fn forward_conditional_delete_with_different_label_returns_409_and_keeps_forward() {
        let registry = InMemoryRegistry::new();
        register_owned_forward(&registry, "pod-b");
        let resp = conditional_delete(&registry, "label.cic.instance=pod-a");
        assert_eq!(resp.status(), 409);
        assert_eq!(
            parse_body(&resp)["error"],
            "forward owned-fwd label precondition failed"
        );
        assert!(parse_body(&resp)["data"].is_null());
        assert!(registry.get_forward("owned-fwd").is_some());
        assert!(registry.get_tool("default", "owned-tool").is_some());
    }

    #[test]
    fn forward_conditional_delete_with_absent_label_returns_409() {
        let registry = InMemoryRegistry::new();
        register_owned_forward(&registry, "pod-a");
        let resp = conditional_delete(&registry, "label.cic.instance=pod-a&label.zone=eu");
        assert_eq!(resp.status(), 409);
        assert!(registry.get_forward("owned-fwd").is_some());
        assert!(registry.get_tool("default", "owned-tool").is_some());
    }

    #[test]
    fn forward_conditional_delete_of_missing_forward_returns_404() {
        let registry = InMemoryRegistry::new();
        assert_eq!(
            conditional_delete(&registry, "label.cic.instance=pod-a").status(),
            404
        );
    }

    #[test]
    fn forward_delete_without_label_parameters_is_unconditional() {
        let registry = InMemoryRegistry::new();
        register_owned_forward(&registry, "pod-b");
        assert_eq!(conditional_delete(&registry, "unrelated=1").status(), 200);
        assert!(registry.get_forward("owned-fwd").is_none());
        assert!(registry.get_tool("default", "owned-tool").is_none());
    }

    #[test]
    fn label_precondition_decodes_percent_encoded_values() {
        let precondition = super::label_precondition(Some("label.owner=pod%20a%2F1"));
        let labels = HashMap::from([("owner".to_owned(), "pod a/1".to_owned())]);
        assert!(precondition(&labels));
        assert!(super::label_precondition(None)(&HashMap::new()));
        assert!(!super::label_precondition(Some("label.owner=x"))(
            &HashMap::new()
        ));
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
            handle_forward_delete(&registry, &broker, "cred-fwd", None).status(),
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

        let data = data_field(&handle_tool_get(&registry, "default", "cc"));
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

        let data = data_field(&handle_tool_get(&registry, "default", "cc-opt"));
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

        let data = data_field(&handle_resource_get(&registry, "default", "cc-res"));
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
        let resp = handle_tool_get(&registry, "default", "nonexistent");
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
