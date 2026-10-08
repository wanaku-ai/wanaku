#![expect(
    dead_code,
    reason = "utoipa OpenAPI stubs — empty function bodies used only for path annotations"
)]

use utoipa::OpenApi;
use wanaku_feature_simulation::api::{
    CandidateSelection, Diagnostic, PolicyIdentity, ReplayDetail, ReplayJob, ReplayRequest,
    SimulationError, SimulationReport, SimulationRequest, StageDecision, SyntheticAction,
    ValidationReport, ValidationRequest,
};

use wanaku_feature_action_policy::api::{
    ActionPolicyRevisionResponse, ActivateRevisionRequest as ActivatePolicyRevisionRequest,
    UpdateActionPolicyRequest,
};
use wanaku_feature_action_policy::{
    ActionPolicy, Effect, MatchExpression, MatchKind, Predicate, Rule, Selectors, TargetType,
};
use wanaku_feature_evaluator::api::{
    ActivateRevisionRequest as ActivateEvaluatorRevisionRequest, BindNamespaceRequest,
    EvaluatorReadiness, EvaluatorRevisionResponse, EvaluatorStatus, NamespaceBinding,
    UnbindNamespaceResponse, UpdateEvaluatorsRequest,
};
use wanaku_feature_evaluator::config::{
    EvaluatorDef, LlmDef, LlmOperation, NoulCriteria, NoulDef, ProcessorRef, SystemOneDef,
    SystemOneState, TriggerDef,
};
use wanaku_feature_plugins::api::{
    ConfigurePluginRequest, InstallPluginRequest, InstallPluginResponse, PluginCatalogEntry,
    PluginServiceTarget,
};
use wanaku_feature_plugins::manifest::{PluginManifest, PluginRequires, ServiceRequirement};
use wanaku_types::revision::{ActivationStatus, RevisionMetadata, RevisionOrigin};

use wanaku_infra::metrics::{
    AuditSnapshot, DecisionSnapshot, DurationSnapshot, EngineSnapshot, EvaluatorSnapshot,
    FilterSnapshot, GaugeSnapshot, GovernanceMetric, GovernanceOutcome, LlmSnapshot,
    MetricsSnapshot, PipelineSnapshot, SchemaSnapshot, WasmSnapshot,
};
use wanaku_types::audit::{AuditCategory, AuditDecision, AuditEvent, AuditHealth, AuditPage};
use wanaku_types::credentials::{
    BindingRestrictions, CacheRules, CredentialBinding, CredentialPurpose, InjectionMechanism,
    NormalizedOrigin, SecretRef,
};
use wanaku_types::registry::{
    ForwardEntry, McpServerInfo, NamespaceEntry, PromptArgument, PromptEntry, PromptMessage,
    ResourceEntry, ToolEntry,
};

#[derive(serde::Serialize, utoipa::ToSchema)]
struct WanakuResponse<T: utoipa::ToSchema> {
    data: Option<T>,
    error: Option<String>,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
struct ManagementErrorResponse {
    data: Option<serde_json::Value>,
    error: String,
}

// -- Health -------------------------------------------------------------------

#[utoipa::path(get, path = "/healthz", tag = "Health",
    responses((status = 200, description = "Server is healthy", body = serde_json::Value))
)]
const fn healthz() {}

use wanaku_types::agents::{AgentEntry, AgentView};

#[utoipa::path(get, path = "/api/v1/agents", tag = "Agents",
    params(("namespace" = Option<String>, Query)),
    responses((status = 200, description = "Agent operation completed", body = WanakuResponse<Vec<AgentView>>),
        (status = 400, description = "Invalid agent", body = ManagementErrorResponse),
        (status = 404, description = "Agent not found", body = ManagementErrorResponse),
        (status = 409, description = "Agent already exists", body = ManagementErrorResponse))
)]
const fn list_agents() {}

#[utoipa::path(get, path = "/api/v1/agents/{namespace}/{name}", tag = "Agents",
    params(("namespace" = String, Path), ("name" = String, Path)),
    responses((status = 200, description = "Agent operation completed", body = WanakuResponse<AgentView>),
        (status = 400, description = "Invalid agent", body = ManagementErrorResponse),
        (status = 404, description = "Agent not found", body = ManagementErrorResponse),
        (status = 409, description = "Agent already exists", body = ManagementErrorResponse))
)]
const fn get_agent() {}

#[utoipa::path(post, path = "/api/v1/agents", tag = "Agents",
    request_body = AgentEntry,
    responses((status = 200, description = "Agent operation completed", body = WanakuResponse<AgentView>),
        (status = 400, description = "Invalid agent", body = ManagementErrorResponse),
        (status = 404, description = "Agent not found", body = ManagementErrorResponse),
        (status = 409, description = "Agent already exists", body = ManagementErrorResponse))
)]
const fn create_agent() {}

#[utoipa::path(put, path = "/api/v1/agents/{namespace}/{name}", tag = "Agents",
    params(("namespace" = String, Path), ("name" = String, Path)),
    request_body = AgentEntry,
    responses((status = 200, description = "Agent operation completed", body = WanakuResponse<AgentView>),
        (status = 400, description = "Invalid agent", body = ManagementErrorResponse),
        (status = 404, description = "Agent not found", body = ManagementErrorResponse),
        (status = 409, description = "Agent already exists", body = ManagementErrorResponse))
)]
const fn update_agent() {}

#[utoipa::path(delete, path = "/api/v1/agents/{namespace}/{name}", tag = "Agents",
    params(("namespace" = String, Path), ("name" = String, Path)),
    responses((status = 200, description = "Agent operation completed", body = WanakuResponse<serde_json::Value>),
        (status = 400, description = "Invalid agent", body = ManagementErrorResponse),
        (status = 404, description = "Agent not found", body = ManagementErrorResponse),
        (status = 409, description = "Agent already exists", body = ManagementErrorResponse))
)]
const fn delete_agent() {}

// -- Tools --------------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/tools", tag = "Tools",
    responses((status = 200, description = "List all tools", body = Vec<ToolEntry>))
)]
const fn list_tools() {}

#[utoipa::path(get, path = "/api/v1/tools/{name}", tag = "Tools",
    params(("name" = String, Path, description = "Tool name")),
    responses(
        (status = 200, description = "Tool found", body = ToolEntry),
        (status = 404, description = "Tool not found"),
    )
)]
const fn get_tool() {}

#[utoipa::path(delete, path = "/api/v1/tools/{name}", tag = "Tools",
    params(("name" = String, Path, description = "Tool name")),
    responses(
        (status = 200, description = "Tool removed"),
        (status = 404, description = "Tool not found"),
    )
)]
const fn delete_tool() {}

#[utoipa::path(put, path = "/api/v1/tools/{name}/enable", tag = "Tools",
    params(("name" = String, Path, description = "Tool name")),
    responses(
        (status = 200, description = "Tool enabled. MCP clients can list and use it.", body = ToolEntry),
        (status = 404, description = "Tool not found"),
    )
)]
const fn enable_tool() {}

#[utoipa::path(put, path = "/api/v1/tools/{name}/disable", tag = "Tools",
    params(("name" = String, Path, description = "Tool name")),
    responses(
        (status = 200, description = "Tool disabled. MCP clients cannot list or use it.", body = ToolEntry),
        (status = 404, description = "Tool not found"),
    )
)]
const fn disable_tool() {}

// -- Resources ----------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/resources", tag = "Resources",
    responses((status = 200, description = "List all resources", body = Vec<ResourceEntry>))
)]
const fn list_resources() {}

#[utoipa::path(get, path = "/api/v1/resources/{name}", tag = "Resources",
    params(("name" = String, Path, description = "Resource name")),
    responses(
        (status = 200, description = "Resource found", body = ResourceEntry),
        (status = 404, description = "Resource not found"),
    )
)]
const fn get_resource() {}

#[utoipa::path(delete, path = "/api/v1/resources/{name}", tag = "Resources",
    params(("name" = String, Path, description = "Resource name")),
    responses(
        (status = 200, description = "Resource removed"),
        (status = 404, description = "Resource not found"),
    )
)]
const fn delete_resource() {}

#[utoipa::path(put, path = "/api/v1/resources/{name}/enable", tag = "Resources",
    params(("name" = String, Path, description = "Resource name")),
    responses(
        (status = 200, description = "Resource enabled. MCP clients can list and use it.", body = ResourceEntry),
        (status = 404, description = "Resource not found"),
    )
)]
const fn enable_resource() {}

#[utoipa::path(put, path = "/api/v1/resources/{name}/disable", tag = "Resources",
    params(("name" = String, Path, description = "Resource name")),
    responses(
        (status = 200, description = "Resource disabled. MCP clients cannot list or use it.", body = ResourceEntry),
        (status = 404, description = "Resource not found"),
    )
)]
const fn disable_resource() {}

// -- Prompts ------------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/prompts", tag = "Prompts",
    responses((status = 200, description = "List all prompts", body = Vec<PromptEntry>))
)]
const fn list_prompts() {}

#[utoipa::path(get, path = "/api/v1/prompts/{name}", tag = "Prompts",
    params(("name" = String, Path, description = "Prompt name")),
    responses(
        (status = 200, description = "Prompt found", body = PromptEntry),
        (status = 404, description = "Prompt not found"),
    )
)]
const fn get_prompt() {}

#[utoipa::path(delete, path = "/api/v1/prompts/{name}", tag = "Prompts",
    params(("name" = String, Path, description = "Prompt name")),
    responses(
        (status = 200, description = "Prompt removed"),
        (status = 404, description = "Prompt not found"),
    )
)]
const fn delete_prompt() {}

#[utoipa::path(put, path = "/api/v1/prompts/{name}/enable", tag = "Prompts",
    params(("name" = String, Path, description = "Prompt name")),
    responses(
        (status = 200, description = "Prompt enabled. MCP clients can list and use it.", body = PromptEntry),
        (status = 404, description = "Prompt not found"),
    )
)]
const fn enable_prompt() {}

#[utoipa::path(put, path = "/api/v1/prompts/{name}/disable", tag = "Prompts",
    params(("name" = String, Path, description = "Prompt name")),
    responses(
        (status = 200, description = "Prompt disabled. MCP clients cannot list or use it.", body = PromptEntry),
        (status = 404, description = "Prompt not found"),
    )
)]
const fn disable_prompt() {}

// -- Namespaces ---------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/namespaces", tag = "Namespaces",
    responses((status = 200, description = "List all namespaces", body = Vec<NamespaceEntry>))
)]
const fn list_namespaces() {}

#[utoipa::path(get, path = "/api/v1/namespaces/{name}", tag = "Namespaces",
    params(("name" = String, Path, description = "Namespace name")),
    responses(
        (status = 200, description = "Namespace found", body = NamespaceEntry),
        (status = 404, description = "Namespace not found"),
    )
)]
const fn get_namespace() {}

#[utoipa::path(post, path = "/api/v1/namespaces", tag = "Namespaces",
    request_body = NamespaceEntry,
    responses(
        (status = 200, description = "Namespace registered", body = NamespaceEntry),
        (status = 400, description = "Invalid namespace name"),
    )
)]
const fn create_namespace() {}

#[utoipa::path(delete, path = "/api/v1/namespaces/{name}", tag = "Namespaces",
    params(("name" = String, Path, description = "Namespace name")),
    responses(
        (status = 200, description = "Namespace removed"),
        (status = 404, description = "Namespace not found"),
    )
)]
const fn delete_namespace() {}

// -- Bindings -----------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/bindings", tag = "Bindings",
    responses((status = 200, description = "List all credential bindings (non-secret metadata only)", body = Vec<CredentialBinding>))
)]
const fn list_bindings() {}

#[utoipa::path(get, path = "/api/v1/bindings/{id}", tag = "Bindings",
    params(("id" = String, Path, description = "Credential binding id")),
    responses(
        (status = 200, description = "Binding found (non-secret metadata only)", body = CredentialBinding),
        (status = 404, description = "Binding not found"),
    )
)]
const fn get_binding() {}

// -- Forwards -----------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/forwards", tag = "Forwards",
    responses((status = 200, description = "List all forwards", body = Vec<ForwardEntry>))
)]
const fn list_forwards() {}

#[utoipa::path(get, path = "/api/v1/forwards/{name}", tag = "Forwards",
    params(("name" = String, Path, description = "Forward name")),
    responses(
        (status = 200, description = "Forward found", body = ForwardEntry),
        (status = 404, description = "Forward not found"),
    )
)]
const fn get_forward() {}

#[utoipa::path(post, path = "/api/v1/forwards", tag = "Forwards",
    request_body = ForwardEntry,
    responses((status = 200, description = "Forward created and tools discovered", body = serde_json::Value))
)]
const fn create_forward() {}

#[utoipa::path(delete, path = "/api/v1/forwards/{name}", tag = "Forwards",
    params(("name" = String, Path, description = "Forward name")),
    responses(
        (status = 200, description = "Forward removed"),
        (status = 404, description = "Forward not found"),
    )
)]
const fn delete_forward() {}

#[utoipa::path(post, path = "/api/v1/forwards/{name}/refreshes", tag = "Forwards",
    params(("name" = String, Path, description = "Forward name")),
    responses(
        (status = 200, description = "Forward refreshed", body = serde_json::Value),
        (status = 404, description = "Forward not found"),
    )
)]
const fn refresh_forward() {}

// -- Metrics ------------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/metrics", tag = "Metrics",
    responses((status = 200, description = "Metrics snapshot", body = MetricsSnapshot))
)]
const fn get_metrics() {}

// -- Audit --------------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/audit/events", tag = "Audit",
    params(
        ("from" = Option<String>, Query, description = "Inclusive ISO 8601 start time"),
        ("to" = Option<String>, Query, description = "Inclusive ISO 8601 end time"),
        ("namespace" = Option<String>, Query),
        ("actor" = Option<String>, Query),
        ("operation" = Option<String>, Query),
        ("target" = Option<String>, Query),
        ("decision" = Option<AuditDecision>, Query),
        ("reason_code" = Option<String>, Query),
        ("correlation_id" = Option<String>, Query),
        ("offset" = Option<usize>, Query),
        ("limit" = Option<usize>, Query),
    ),
    responses((status = 200, description = "Audit event page", body = WanakuResponse<AuditPage>))
)]
const fn list_audit_events() {}

#[utoipa::path(get, path = "/api/v1/audit/events/{id}", tag = "Audit",
    params(("id" = String, Path, description = "Audit event ID")),
    responses(
        (status = 200, description = "Audit event", body = WanakuResponse<AuditEvent>),
        (status = 404, body = ManagementErrorResponse),
    )
)]
const fn get_audit_event() {}

#[utoipa::path(get, path = "/api/v1/audit/schema", tag = "Audit",
    responses((status = 200, description = "Audit schema version", body = serde_json::Value))
)]
const fn get_audit_schema() {}

#[utoipa::path(get, path = "/api/v1/audit/health", tag = "Audit",
    responses((status = 200, description = "Audit store health", body = WanakuResponse<AuditHealth>))
)]
const fn get_audit_health() {}

// -- Info ---------------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/management/info", tag = "Management",
    responses((status = 200, description = "Server name and version", body = serde_json::Value))
)]
const fn get_info() {}

// -- Statistics ---------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/management/statistics", tag = "Management",
    responses((status = 200, description = "Registry statistics", body = serde_json::Value))
)]
const fn get_statistics() {}

// -- Evaluators ---------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/evaluators", tag = "Evaluators",
    responses((status = 200, body = WanakuResponse<Vec<EvaluatorDef>>))
)]
const fn list_evaluators() {}

#[utoipa::path(get, path = "/api/v1/evaluators/status", tag = "Evaluators",
    params(("namespace" = Option<String>, Query, description = "Namespace name; defaults to default")),
    responses((status = 200, body = WanakuResponse<EvaluatorStatus>))
)]
const fn get_evaluator_status() {}

#[utoipa::path(put, path = "/api/v1/evaluators", tag = "Evaluators",
    request_body = UpdateEvaluatorsRequest,
    responses(
        (status = 200, body = WanakuResponse<EvaluatorRevisionResponse>),
        (status = 400, description = "Invalid evaluator configuration", body = ManagementErrorResponse),
        (status = 409, description = "Revision conflict", body = ManagementErrorResponse),
        (status = 422, description = "Evaluator configuration rejected", body = ManagementErrorResponse),
    )
)]
const fn update_evaluators() {}

#[utoipa::path(get, path = "/api/v1/evaluators/llm-connections", tag = "Evaluators",
    responses((status = 200, body = WanakuResponse<Vec<String>>))
)]
const fn list_evaluator_llm_connections() {}

#[utoipa::path(get, path = "/api/v1/evaluators/namespaces", tag = "Evaluators",
    responses((status = 200, body = WanakuResponse<std::collections::HashMap<String, String>>))
)]
const fn list_evaluator_bindings() {}

#[utoipa::path(put, path = "/api/v1/evaluators/namespaces/{namespace}", tag = "Evaluators",
    params(("namespace" = String, Path)), request_body = BindNamespaceRequest,
    responses((status = 200, body = WanakuResponse<NamespaceBinding>), (status = 400, body = ManagementErrorResponse))
)]
const fn bind_evaluator_namespace() {}

#[utoipa::path(delete, path = "/api/v1/evaluators/namespaces/{namespace}", tag = "Evaluators",
    params(("namespace" = String, Path)),
    responses((status = 200, body = WanakuResponse<UnbindNamespaceResponse>))
)]
const fn unbind_evaluator_namespace() {}

#[utoipa::path(get, path = "/api/v1/evaluators/revisions", tag = "Evaluators",
    responses((status = 200, body = WanakuResponse<Vec<RevisionMetadata>>))
)]
const fn list_evaluator_revisions() {}

#[utoipa::path(get, path = "/api/v1/evaluators/revisions/active", tag = "Evaluators",
    responses((status = 200, body = WanakuResponse<EvaluatorRevisionResponse>), (status = 404, body = ManagementErrorResponse))
)]
const fn get_active_evaluator_revision() {}

#[utoipa::path(get, path = "/api/v1/evaluators/revisions/{id}", tag = "Evaluators",
    params(("id" = u64, Path)),
    responses((status = 200, body = WanakuResponse<EvaluatorRevisionResponse>), (status = 404, body = ManagementErrorResponse))
)]
const fn get_evaluator_revision() {}

#[utoipa::path(post, path = "/api/v1/evaluators/revisions/{id}/activate", tag = "Evaluators",
    params(("id" = u64, Path)), request_body = ActivateEvaluatorRevisionRequest,
    responses(
        (status = 200, body = WanakuResponse<EvaluatorRevisionResponse>),
        (status = 400, body = ManagementErrorResponse),
        (status = 404, body = ManagementErrorResponse),
        (status = 409, body = ManagementErrorResponse),
        (status = 422, body = ManagementErrorResponse),
    )
)]
const fn activate_evaluator_revision() {}

// -- Action policies ----------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/action-policies", tag = "Action Policies",
    responses((status = 200, body = WanakuResponse<ActionPolicyRevisionResponse>), (status = 404, body = ManagementErrorResponse))
)]
const fn get_effective_action_policy() {}

#[utoipa::path(put, path = "/api/v1/action-policies", tag = "Action Policies",
    request_body = UpdateActionPolicyRequest,
    responses(
        (status = 200, body = WanakuResponse<ActionPolicyRevisionResponse>),
        (status = 400, body = ManagementErrorResponse),
        (status = 409, body = ManagementErrorResponse),
        (status = 422, body = ManagementErrorResponse),
    )
)]
const fn update_action_policy() {}

#[utoipa::path(get, path = "/api/v1/action-policies/revisions", tag = "Action Policies",
    responses((status = 200, body = WanakuResponse<Vec<RevisionMetadata>>))
)]
const fn list_action_policy_revisions() {}

#[utoipa::path(get, path = "/api/v1/action-policies/revisions/active", tag = "Action Policies",
    responses((status = 200, body = WanakuResponse<ActionPolicyRevisionResponse>), (status = 404, body = ManagementErrorResponse))
)]
const fn get_active_action_policy_revision() {}

#[utoipa::path(get, path = "/api/v1/action-policies/revisions/{id}", tag = "Action Policies",
    params(("id" = u64, Path)),
    responses((status = 200, body = WanakuResponse<ActionPolicyRevisionResponse>), (status = 404, body = ManagementErrorResponse))
)]
const fn get_action_policy_revision() {}

#[utoipa::path(post, path = "/api/v1/action-policies/revisions/{id}/activate", tag = "Action Policies",
    params(("id" = u64, Path)), request_body = ActivatePolicyRevisionRequest,
    responses(
        (status = 200, body = WanakuResponse<ActionPolicyRevisionResponse>),
        (status = 400, body = ManagementErrorResponse),
        (status = 404, body = ManagementErrorResponse),
        (status = 409, body = ManagementErrorResponse),
        (status = 422, body = ManagementErrorResponse),
    )
)]
const fn activate_action_policy_revision() {}

// -- Plugins ------------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/plugins", tag = "Plugins",
    responses((status = 200, description = "List discovered plugins", body = WanakuResponse<Vec<PluginManifest>>))
)]
const fn list_plugins() {}

#[utoipa::path(get, path = "/api/v1/plugins/catalog", tag = "Plugins",
    responses(
        (status = 200, description = "Available plugins", body = WanakuResponse<Vec<PluginCatalogEntry>>),
        (status = 502, description = "Catalog unavailable or invalid", body = ManagementErrorResponse),
    )
)]
const fn list_plugin_catalog() {}

#[utoipa::path(post, path = "/api/v1/plugins/install", tag = "Plugins",
    request_body = InstallPluginRequest,
    responses(
        (status = 200, description = "Plugin successfully installed", body = WanakuResponse<InstallPluginResponse>),
        (status = 400, description = "Invalid request or insecure archive", body = ManagementErrorResponse),
        (status = 502, description = "Download failed", body = ManagementErrorResponse),
        (status = 422, description = "Extraction or manifest error", body = ManagementErrorResponse),
    )
)]
const fn install_plugin() {}

#[utoipa::path(put, path = "/api/v1/plugins/{id}/config", tag = "Plugins",
    params(("id" = String, Path, description = "Plugin ID")),
    request_body = ConfigurePluginRequest,
    responses(
        (status = 200, description = "Configuration saved", body = WanakuResponse<serde_json::Value>),
        (status = 400, description = "Invalid configuration", body = ManagementErrorResponse),
        (status = 500, description = "Persistence failed", body = ManagementErrorResponse),
    )
)]
const fn configure_plugin() {}

// -- OpenAPI Aggregation ------------------------------------------------------

struct OptionalActivationBodies;

impl utoipa::Modify for OptionalActivationBodies {
    fn modify(&self, openapi: &mut utoipa::openapi::OpenApi) {
        for path in [
            "/api/v1/evaluators/revisions/{id}/activate",
            "/api/v1/action-policies/revisions/{id}/activate",
            "/api/v1/audit/events",
            "/api/v1/audit/events/{id}",
            "/api/v1/audit/schema",
            "/api/v1/audit/health",
        ] {
            if let Some(body) = openapi
                .paths
                .paths
                .get_mut(path)
                .and_then(|item| item.post.as_mut())
                .and_then(|operation| operation.request_body.as_mut())
            {
                body.required = None;
            }
        }
    }
}

#[utoipa::path(post, path = "/api/v1/policy-simulations/validate", tag = "Policy Simulations",
    request_body = ValidationRequest,
    responses((status = 200, description = "Transient validation report", body = WanakuResponse<ValidationReport>),
        (status = 400, description = "Invalid request", body = ManagementErrorResponse),
        (status = 413, description = "Input or result limit exceeded", body = ManagementErrorResponse),
        (status = 408, description = "Simulation duration exceeded", body = ManagementErrorResponse),
        (status = 429, description = "Simulation capacity exhausted", body = ManagementErrorResponse),
        (status = 503, description = "Simulation state unavailable", body = ManagementErrorResponse))
)]
const fn validate_policy_candidate() {}

#[utoipa::path(post, path = "/api/v1/policy-simulations/simulate", tag = "Policy Simulations",
    request_body = SimulationRequest,
    responses((status = 200, description = "Side-effect-free decision report", body = WanakuResponse<SimulationReport>),
        (status = 400, description = "Invalid action or unsupported external execution", body = ManagementErrorResponse),
        (status = 422, description = "Candidate validation failed", body = WanakuResponse<SimulationError>),
        (status = 413, description = "Input or result limit exceeded", body = ManagementErrorResponse),
        (status = 408, description = "Simulation duration exceeded", body = ManagementErrorResponse),
        (status = 503, description = "Simulation state unavailable", body = ManagementErrorResponse),
        (status = 429, description = "Simulation capacity exhausted", body = ManagementErrorResponse))
)]
const fn simulate_policy_action() {}

#[utoipa::path(post, path = "/api/v1/policy-simulations/replays", tag = "Policy Simulations",
    request_body = ReplayRequest,
    responses((status = 202, description = "Bounded replay job accepted", body = WanakuResponse<ReplayJob>),
        (status = 400, description = "Invalid replay request", body = ManagementErrorResponse),
        (status = 413, description = "Input or result limit exceeded", body = ManagementErrorResponse),
        (status = 408, description = "Simulation duration exceeded", body = ManagementErrorResponse),
        (status = 422, description = "Candidate validation failed", body = WanakuResponse<SimulationError>),
        (status = 503, description = "Simulation state unavailable", body = ManagementErrorResponse),
        (status = 429, description = "Replay capacity exhausted", body = ManagementErrorResponse))
)]
const fn start_policy_replay() {}

#[utoipa::path(get, path = "/api/v1/policy-simulations/replays/{id}", tag = "Policy Simulations",
    params(("id" = String, Path)),
    responses((status = 200, description = "Replay status and bounded results", body = WanakuResponse<ReplayJob>),
        (status = 404, description = "Replay not found or expired", body = ManagementErrorResponse),
        (status = 503, description = "Simulation state unavailable", body = ManagementErrorResponse))
)]
const fn get_policy_replay() {}

#[utoipa::path(post, path = "/api/v1/policy-simulations/replays/{id}/cancel", tag = "Policy Simulations",
    params(("id" = String, Path)),
    responses((status = 200, description = "Replay cancellation status", body = WanakuResponse<ReplayJob>),
        (status = 404, description = "Replay not found or expired", body = ManagementErrorResponse),
        (status = 503, description = "Simulation state unavailable", body = ManagementErrorResponse))
)]
const fn cancel_policy_replay() {}

#[utoipa::path(delete, path = "/api/v1/policy-simulations/replays/{id}", tag = "Policy Simulations",
    params(("id" = String, Path)),
    responses((status = 200, description = "Replay deleted", body = WanakuResponse<serde_json::Value>),
        (status = 404, description = "Replay not found or expired", body = ManagementErrorResponse),
        (status = 503, description = "Simulation state unavailable", body = ManagementErrorResponse))
)]
const fn delete_policy_replay() {}

#[derive(OpenApi)]
#[openapi(
    info(
        title = "Wanaku API",
        version = "0.3.0",
        description = "Wanaku MCP proxy management API"
    ),
    paths(
        list_agents, get_agent, create_agent, update_agent, delete_agent,
        healthz,
        list_tools,
        get_tool,
        delete_tool,
        enable_tool,
        disable_tool,
        list_resources,
        get_resource,
        delete_resource,
        enable_resource,
        disable_resource,
        list_prompts,
        get_prompt,
        delete_prompt,
        enable_prompt,
        disable_prompt,
        list_namespaces,
        get_namespace,
        create_namespace,
        delete_namespace,
        list_bindings,
        get_binding,
        list_forwards,
        get_forward,
        create_forward,
        delete_forward,
        refresh_forward,
        get_metrics,
        list_audit_events,
        get_audit_event,
        get_audit_schema,
        get_audit_health,
        get_info,
        get_statistics,
        list_evaluators,
        get_evaluator_status,
        update_evaluators,
        list_evaluator_llm_connections,
        list_evaluator_bindings,
        bind_evaluator_namespace,
        unbind_evaluator_namespace,
        list_evaluator_revisions,
        get_active_evaluator_revision,
        get_evaluator_revision,
        activate_evaluator_revision,
        get_effective_action_policy,
        update_action_policy,
        list_action_policy_revisions,
        get_active_action_policy_revision,
        get_action_policy_revision,
        activate_action_policy_revision,
        list_plugins,
        list_plugin_catalog,
        install_plugin,
        configure_plugin,
        validate_policy_candidate,
        simulate_policy_action,
        start_policy_replay,
        get_policy_replay,
        cancel_policy_replay,
        delete_policy_replay,
    ),
    modifiers(&OptionalActivationBodies),
    components(schemas(
        AgentEntry, AgentView,
        ToolEntry,
        ResourceEntry,
        PromptEntry,
        PromptArgument,
        PromptMessage,
        ForwardEntry,
        McpServerInfo,
        NamespaceEntry,
        CredentialBinding,
        CredentialPurpose,
        NormalizedOrigin,
        InjectionMechanism,
        BindingRestrictions,
        CacheRules,
        SecretRef,
        MetricsSnapshot,
        GovernanceMetric,
        GovernanceOutcome,
        FilterSnapshot,
        DurationSnapshot,
        EvaluatorSnapshot,
        DecisionSnapshot,
        LlmSnapshot,
        EngineSnapshot,
        SchemaSnapshot,
        WasmSnapshot,
        PipelineSnapshot,
        GaugeSnapshot,
        AuditCategory,
        AuditDecision,
        AuditEvent,
        AuditPage,
        AuditHealth,
        AuditSnapshot,
        EvaluatorDef,
        EvaluatorStatus,
        EvaluatorReadiness,
        TriggerDef,
        LlmDef,
        LlmOperation,
        SystemOneDef,
        SystemOneState,
        NoulDef,
        NoulCriteria,
        ProcessorRef,
        UpdateEvaluatorsRequest,
        ActivateEvaluatorRevisionRequest,
        EvaluatorRevisionResponse,
        BindNamespaceRequest,
        NamespaceBinding,
        UnbindNamespaceResponse,
        RevisionMetadata,
        RevisionOrigin,
        ActivationStatus,
        ActionPolicy,
        Rule,
        Effect,
        Selectors,
        TargetType,
        MatchExpression,
        MatchKind,
        Predicate,
        UpdateActionPolicyRequest,
        ActivatePolicyRevisionRequest,
        ActionPolicyRevisionResponse,
        PluginManifest,
        PluginCatalogEntry,
        PluginRequires,
        ServiceRequirement,
        InstallPluginRequest,
        InstallPluginResponse,
        PluginServiceTarget,
        ConfigurePluginRequest,
        ManagementErrorResponse,
        CandidateSelection, SyntheticAction, ValidationRequest, SimulationRequest, ReplayRequest,
        Diagnostic, PolicyIdentity, ValidationReport, StageDecision, SimulationReport,
        ReplayDetail, ReplayJob, SimulationError,
    ))
)]
pub struct ApiDoc;

pub fn openapi_json() -> Vec<u8> {
    let spec = ApiDoc::openapi();
    serde_json::to_vec_pretty(&spec).unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn simulation_contracts_are_transient_and_versioned() {
        let value = serde_json::to_value(ApiDoc::openapi()).unwrap_or_default();
        for (path, method, status) in [
            ("/api/v1/policy-simulations/validate", "post", "200"),
            ("/api/v1/policy-simulations/simulate", "post", "200"),
            ("/api/v1/policy-simulations/replays", "post", "202"),
            ("/api/v1/policy-simulations/replays/{id}", "get", "200"),
            ("/api/v1/policy-simulations/replays/{id}", "delete", "200"),
            (
                "/api/v1/policy-simulations/replays/{id}/cancel",
                "post",
                "200",
            ),
        ] {
            let escaped = path.replace('/', "~1");
            assert!(
                value
                    .pointer(&format!("/paths/{escaped}/{method}/responses/{status}"))
                    .is_some()
            );
        }
    }

    #[test]
    fn simulation_reports_have_version_and_patch_fields() {
        let value = serde_json::to_value(ApiDoc::openapi()).unwrap_or_default();
        for schema in ["ValidationReport", "SimulationReport", "ReplayJob"] {
            assert!(
                value
                    .pointer(&format!(
                        "/components/schemas/{schema}/properties/schema_version"
                    ))
                    .is_some()
            );
        }
        assert!(
            value
                .pointer("/components/schemas/CandidateSelection/properties/policy_patch")
                .is_some()
        );
    }

    #[test]
    fn agents_have_crud_contracts_and_proxy_views() {
        let value = serde_json::to_value(ApiDoc::openapi()).unwrap_or_default();
        for path in ["/api/v1/agents", "/api/v1/agents/{namespace}/{name}"] {
            assert!(value["paths"].get(path).is_some());
        }
        assert!(
            value
                .pointer("/components/schemas/AgentView/properties/proxyUrl")
                .is_some()
        );
        assert!(
            value
                .pointer("/components/schemas/AgentEntry/properties/proxyUrl")
                .is_none()
        );
        assert!(
            value
                .pointer("/components/schemas/AgentEntry/properties/cardAddress")
                .is_some()
        );
    }

    #[test]
    fn action_policy_accepts_agent_targets() {
        let value = serde_json::to_value(ApiDoc::openapi()).unwrap_or_default();
        assert_eq!(
            value.pointer("/components/schemas/TargetType/enum"),
            Some(&serde_json::json!(["tool", "resource", "prompt", "agent"]))
        );
    }

    #[test]
    fn includes_evaluator_and_action_policy_contracts() {
        let document = ApiDoc::openapi();
        let value = serde_json::to_value(document).unwrap_or_default();
        let paths = value.get("paths").and_then(serde_json::Value::as_object);
        let schemas = value
            .pointer("/components/schemas")
            .and_then(serde_json::Value::as_object);

        for path in [
            "/api/v1/evaluators",
            "/api/v1/evaluators/status",
            "/api/v1/evaluators/llm-connections",
            "/api/v1/evaluators/namespaces",
            "/api/v1/evaluators/namespaces/{namespace}",
            "/api/v1/evaluators/revisions",
            "/api/v1/evaluators/revisions/active",
            "/api/v1/evaluators/revisions/{id}",
            "/api/v1/evaluators/revisions/{id}/activate",
            "/api/v1/action-policies",
            "/api/v1/action-policies/revisions",
            "/api/v1/action-policies/revisions/active",
            "/api/v1/action-policies/revisions/{id}",
            "/api/v1/action-policies/revisions/{id}/activate",
            "/api/v1/plugins",
            "/api/v1/plugins/catalog",
            "/api/v1/plugins/install",
            "/api/v1/plugins/{id}/config",
        ] {
            assert!(
                paths.is_some_and(|paths| paths.contains_key(path)),
                "missing {path}"
            );
        }

        for schema in [
            "PluginManifest",
            "PluginCatalogEntry",
            "InstallPluginRequest",
            "ConfigurePluginRequest",
            "EvaluatorDef",
            "EvaluatorStatus",
            "EvaluatorReadiness",
            "GovernanceMetric",
            "UpdateEvaluatorsRequest",
            "EvaluatorRevisionResponse",
            "ActionPolicy",
            "Predicate",
            "UpdateActionPolicyRequest",
            "ActionPolicyRevisionResponse",
            "RevisionMetadata",
            "ManagementErrorResponse",
            "AuditEvent",
            "AuditPage",
            "AuditHealth",
        ] {
            assert!(
                schemas.is_some_and(|schemas| schemas.contains_key(schema)),
                "missing {schema}"
            );
        }
    }

    #[test]
    fn includes_enable_and_disable_contracts() {
        let value = serde_json::to_value(ApiDoc::openapi()).unwrap_or_default();
        for kind in ["tools", "resources", "prompts"] {
            for action in ["enable", "disable"] {
                let pointer = format!("/paths/~1api~1v1~1{kind}~1{{name}}~1{action}/put");
                assert!(value.pointer(&pointer).is_some(), "missing {pointer}");
            }
        }
        for schema in ["ToolEntry", "ResourceEntry", "PromptEntry"] {
            assert!(
                value
                    .pointer(&format!("/components/schemas/{schema}/properties/enabled"))
                    .is_some(),
                "{schema} must expose enabled"
            );
        }
    }

    #[test]
    fn evaluator_status_and_shared_failure_contract() {
        let value = serde_json::to_value(ApiDoc::openapi()).unwrap_or_default();
        assert!(
            value
                .pointer("/components/schemas/EvaluatorDef/properties/on_error")
                .is_none()
        );
        assert_eq!(
            value.pointer("/components/schemas/EvaluatorReadiness/enum"),
            Some(&serde_json::json!([
                "ready",
                "degraded",
                "invalid",
                "unconfigured",
                "disabled"
            ]))
        );
        assert!(
            value
                .pointer("/components/schemas/MetricsSnapshot/properties/evaluator_governance")
                .is_some()
        );
    }

    #[test]
    fn includes_credential_binding_contracts() {
        let value = serde_json::to_value(ApiDoc::openapi()).unwrap_or_default();
        let paths = value.get("paths").and_then(serde_json::Value::as_object);
        let schemas = value
            .pointer("/components/schemas")
            .and_then(serde_json::Value::as_object);

        for path in ["/api/v1/bindings", "/api/v1/bindings/{id}"] {
            assert!(
                paths.is_some_and(|paths| paths.contains_key(path)),
                "missing {path}"
            );
        }

        for schema in [
            "CredentialBinding",
            "CredentialPurpose",
            "NormalizedOrigin",
            "InjectionMechanism",
            "BindingRestrictions",
            "CacheRules",
            "SecretRef",
        ] {
            assert!(
                schemas.is_some_and(|schemas| schemas.contains_key(schema)),
                "missing {schema}"
            );
        }

        // A SecretRef serializes as the opaque string "<scheme>:<path>", so its
        // schema must be a string, not an object with scheme/path properties.
        assert_eq!(
            value.pointer("/components/schemas/SecretRef/type"),
            Some(&serde_json::Value::String("string".to_owned())),
            "SecretRef must be a string schema matching its wire format"
        );

        // The binding schema must never expose resolved secret material. It
        // carries only opaque secret references (`secretRefs`).
        assert!(
            !schemas.is_some_and(|schemas| schemas.contains_key("SecretMaterial")),
            "secret material must not appear in the OpenAPI schema"
        );
        let binding = value
            .pointer("/components/schemas/CredentialBinding/properties")
            .and_then(serde_json::Value::as_object);
        assert!(
            binding.is_some_and(|properties| properties.contains_key("secretRefs")),
            "binding must expose opaque secret references"
        );
    }

    #[test]
    fn feature_contracts_match_the_management_wire_format() {
        let value = serde_json::to_value(ApiDoc::openapi()).unwrap_or_default();

        for path in [
            "/api/v1/evaluators/revisions/{id}/activate",
            "/api/v1/action-policies/revisions/{id}/activate",
        ] {
            assert_eq!(
                value.pointer(&format!(
                    "/paths/{}/post/requestBody/required",
                    path.replace('~', "~0").replace('/', "~1")
                )),
                None,
                "{path} request body must be optional"
            );
            let schema = value.pointer(&format!(
                "/paths/{}/post/requestBody/content/application~1json/schema",
                path.replace('~', "~0").replace('/', "~1")
            ));
            assert!(schema.and_then(|schema| schema.get("$ref")).is_some());
            assert!(schema.and_then(|schema| schema.get("oneOf")).is_none());
        }

        let response_ref = value
            .pointer("/paths/~1api~1v1~1evaluators/get/responses/200/content/application~1json/schema/$ref")
            .and_then(serde_json::Value::as_str)
            .unwrap_or_default();
        assert!(response_ref.contains("WanakuResponse"));

        let llm = value
            .pointer("/components/schemas/LlmDef/properties")
            .and_then(serde_json::Value::as_object);
        assert!(llm.is_some_and(|properties| properties.contains_key("connection")));
        for secret in ["api_key", "model", "url"] {
            assert!(!llm.is_some_and(|properties| properties.contains_key(secret)));
        }
    }
}
