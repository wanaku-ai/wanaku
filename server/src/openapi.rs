#![expect(dead_code, reason = "utoipa OpenAPI stubs — empty function bodies used only for path annotations")]

use utoipa::OpenApi;

use wanaku_apis::interactions::Interaction;
use wanaku_apis::metrics::{
    DecisionSnapshot, DurationSnapshot, EvaluatorSnapshot, FilterSnapshot,
    GaugeSnapshot, LlmSnapshot, MetricsSnapshot, PipelineSnapshot, SchemaSnapshot, WasmSnapshot,
};
use wanaku_apis::registry::{
    ForwardEntry, McpServerInfo, NamespaceEntry, PromptArgument, PromptEntry, PromptMessage,
    ResourceEntry, ToolEntry,
};

#[derive(serde::Serialize, utoipa::ToSchema)]
struct WanakuResponse<T: utoipa::ToSchema> {
    data: Option<T>,
    error: Option<String>,
}

// -- Health -------------------------------------------------------------------

#[utoipa::path(get, path = "/healthz", tag = "Health",
    responses((status = 200, description = "Server is healthy", body = serde_json::Value))
)]
const fn healthz() {}

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
    responses((status = 200, description = "Namespace registered", body = NamespaceEntry))
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

// -- Interactions -------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/interactions", tag = "Interactions",
    responses((status = 200, description = "List recorded interactions", body = Vec<Interaction>))
)]
const fn list_interactions() {}

#[utoipa::path(delete, path = "/api/v1/interactions", tag = "Interactions",
    responses((status = 200, description = "Interactions cleared"))
)]
const fn clear_interactions() {}

// -- Metrics ------------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/metrics", tag = "Metrics",
    responses((status = 200, description = "Metrics snapshot", body = MetricsSnapshot))
)]
const fn get_metrics() {}

// -- Statistics ---------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/management/statistics", tag = "Management",
    responses((status = 200, description = "Registry statistics", body = serde_json::Value))
)]
const fn get_statistics() {}

// -- OpenAPI Aggregation ------------------------------------------------------

#[derive(OpenApi)]
#[openapi(
    info(
        title = "Wanaku API",
        version = "0.3.0",
        description = "Wanaku MCP proxy management API"
    ),
    paths(
        healthz,
        list_tools, get_tool, delete_tool,
        list_resources, get_resource, delete_resource,
        list_prompts, get_prompt, delete_prompt,
        list_namespaces, get_namespace, create_namespace, delete_namespace,
        list_forwards, get_forward, create_forward, delete_forward, refresh_forward,
        list_interactions, clear_interactions,
        get_metrics,
        get_statistics,
    ),
    components(schemas(
        ToolEntry, ResourceEntry, PromptEntry, PromptArgument, PromptMessage,
        ForwardEntry, McpServerInfo, NamespaceEntry, Interaction,
        MetricsSnapshot, FilterSnapshot, DurationSnapshot, EvaluatorSnapshot,
        DecisionSnapshot, LlmSnapshot, SchemaSnapshot, WasmSnapshot,
        PipelineSnapshot, GaugeSnapshot,
    ))
)]
pub struct ApiDoc;

pub fn openapi_json() -> Vec<u8> {
    let spec = ApiDoc::openapi();
    serde_json::to_vec_pretty(&spec).unwrap_or_default()
}
