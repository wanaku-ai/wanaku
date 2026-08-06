#![expect(dead_code, reason = "utoipa OpenAPI stubs — empty function bodies used only for path annotations")]

use utoipa::OpenApi;

use wanaku_praxis_apis::interactions::Interaction;
use wanaku_praxis_apis::registry::{
    ForwardEntry, NamespaceEntry, PromptArgument, PromptEntry, PromptMessage, ResourceEntry,
    ServiceEntry, ToolEntry,
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
fn healthz() {}

// -- Tools --------------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/tools", tag = "Tools",
    responses((status = 200, description = "List all tools", body = Vec<ToolEntry>))
)]
fn list_tools() {}

#[utoipa::path(get, path = "/api/v1/tools/{name}", tag = "Tools",
    params(("name" = String, Path, description = "Tool name")),
    responses(
        (status = 200, description = "Tool found", body = ToolEntry),
        (status = 404, description = "Tool not found"),
    )
)]
fn get_tool() {}

#[utoipa::path(post, path = "/api/v1/tools", tag = "Tools",
    request_body = ToolEntry,
    responses((status = 200, description = "Tool registered", body = ToolEntry))
)]
fn create_tool() {}

#[utoipa::path(delete, path = "/api/v1/tools/{name}", tag = "Tools",
    params(("name" = String, Path, description = "Tool name")),
    responses(
        (status = 200, description = "Tool removed"),
        (status = 404, description = "Tool not found"),
    )
)]
fn delete_tool() {}

// -- Resources ----------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/resources", tag = "Resources",
    responses((status = 200, description = "List all resources", body = Vec<ResourceEntry>))
)]
fn list_resources() {}

#[utoipa::path(get, path = "/api/v1/resources/{name}", tag = "Resources",
    params(("name" = String, Path, description = "Resource name")),
    responses(
        (status = 200, description = "Resource found", body = ResourceEntry),
        (status = 404, description = "Resource not found"),
    )
)]
fn get_resource() {}

#[utoipa::path(post, path = "/api/v1/resources", tag = "Resources",
    request_body = ResourceEntry,
    responses((status = 200, description = "Resource registered", body = ResourceEntry))
)]
fn create_resource() {}

#[utoipa::path(delete, path = "/api/v1/resources/{name}", tag = "Resources",
    params(("name" = String, Path, description = "Resource name")),
    responses(
        (status = 200, description = "Resource removed"),
        (status = 404, description = "Resource not found"),
    )
)]
fn delete_resource() {}

// -- Prompts ------------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/prompts", tag = "Prompts",
    responses((status = 200, description = "List all prompts", body = Vec<PromptEntry>))
)]
fn list_prompts() {}

#[utoipa::path(get, path = "/api/v1/prompts/{name}", tag = "Prompts",
    params(("name" = String, Path, description = "Prompt name")),
    responses(
        (status = 200, description = "Prompt found", body = PromptEntry),
        (status = 404, description = "Prompt not found"),
    )
)]
fn get_prompt() {}

#[utoipa::path(post, path = "/api/v1/prompts", tag = "Prompts",
    request_body = PromptEntry,
    responses((status = 200, description = "Prompt registered", body = PromptEntry))
)]
fn create_prompt() {}

#[utoipa::path(delete, path = "/api/v1/prompts/{name}", tag = "Prompts",
    params(("name" = String, Path, description = "Prompt name")),
    responses(
        (status = 200, description = "Prompt removed"),
        (status = 404, description = "Prompt not found"),
    )
)]
fn delete_prompt() {}

// -- Namespaces ---------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/namespaces", tag = "Namespaces",
    responses((status = 200, description = "List all namespaces", body = Vec<NamespaceEntry>))
)]
fn list_namespaces() {}

#[utoipa::path(get, path = "/api/v1/namespaces/{name}", tag = "Namespaces",
    params(("name" = String, Path, description = "Namespace name")),
    responses(
        (status = 200, description = "Namespace found", body = NamespaceEntry),
        (status = 404, description = "Namespace not found"),
    )
)]
fn get_namespace() {}

#[utoipa::path(post, path = "/api/v1/namespaces", tag = "Namespaces",
    request_body = NamespaceEntry,
    responses((status = 200, description = "Namespace registered", body = NamespaceEntry))
)]
fn create_namespace() {}

#[utoipa::path(delete, path = "/api/v1/namespaces/{name}", tag = "Namespaces",
    params(("name" = String, Path, description = "Namespace name")),
    responses(
        (status = 200, description = "Namespace removed"),
        (status = 404, description = "Namespace not found"),
    )
)]
fn delete_namespace() {}

// -- Services -----------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/services", tag = "Services",
    responses((status = 200, description = "List all services", body = Vec<ServiceEntry>))
)]
fn list_services() {}

#[utoipa::path(get, path = "/api/v1/services/{name}", tag = "Services",
    params(("name" = String, Path, description = "Service name")),
    responses(
        (status = 200, description = "Service(s) found", body = Vec<ServiceEntry>),
        (status = 404, description = "Service not found"),
    )
)]
fn get_service() {}

#[utoipa::path(post, path = "/api/v1/services", tag = "Services",
    request_body = ServiceEntry,
    responses((status = 200, description = "Service registered", body = ServiceEntry))
)]
fn create_service() {}

#[utoipa::path(delete, path = "/api/v1/services/{name}", tag = "Services",
    params(("name" = String, Path, description = "Service name")),
    responses(
        (status = 200, description = "Service removed"),
        (status = 404, description = "Service not found"),
    )
)]
fn delete_service() {}

// -- Forwards -----------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/forwards", tag = "Forwards",
    responses((status = 200, description = "List all forwards", body = Vec<ForwardEntry>))
)]
fn list_forwards() {}

#[utoipa::path(get, path = "/api/v1/forwards/{name}", tag = "Forwards",
    params(("name" = String, Path, description = "Forward name")),
    responses(
        (status = 200, description = "Forward found", body = ForwardEntry),
        (status = 404, description = "Forward not found"),
    )
)]
fn get_forward() {}

#[utoipa::path(post, path = "/api/v1/forwards", tag = "Forwards",
    request_body = ForwardEntry,
    responses((status = 200, description = "Forward created and tools discovered", body = serde_json::Value))
)]
fn create_forward() {}

#[utoipa::path(delete, path = "/api/v1/forwards/{name}", tag = "Forwards",
    params(("name" = String, Path, description = "Forward name")),
    responses(
        (status = 200, description = "Forward removed"),
        (status = 404, description = "Forward not found"),
    )
)]
fn delete_forward() {}

#[utoipa::path(post, path = "/api/v1/forwards/{name}/refreshes", tag = "Forwards",
    params(("name" = String, Path, description = "Forward name")),
    responses(
        (status = 200, description = "Forward refreshed", body = serde_json::Value),
        (status = 404, description = "Forward not found"),
    )
)]
fn refresh_forward() {}

// -- Interactions -------------------------------------------------------------

#[utoipa::path(get, path = "/api/v1/interactions", tag = "Interactions",
    responses((status = 200, description = "List recorded interactions", body = Vec<Interaction>))
)]
fn list_interactions() {}

#[utoipa::path(delete, path = "/api/v1/interactions", tag = "Interactions",
    responses((status = 200, description = "Interactions cleared"))
)]
fn clear_interactions() {}

// -- OpenAPI Aggregation ------------------------------------------------------

#[derive(OpenApi)]
#[openapi(
    info(
        title = "Wanaku Praxis API",
        version = "0.1.0",
        description = "Wanaku Praxis MCP proxy management API"
    ),
    paths(
        healthz,
        list_tools, get_tool, create_tool, delete_tool,
        list_resources, get_resource, create_resource, delete_resource,
        list_prompts, get_prompt, create_prompt, delete_prompt,
        list_namespaces, get_namespace, create_namespace, delete_namespace,
        list_services, get_service, create_service, delete_service,
        list_forwards, get_forward, create_forward, delete_forward, refresh_forward,
        list_interactions, clear_interactions,
    ),
    components(schemas(
        ToolEntry, ResourceEntry, PromptEntry, PromptArgument, PromptMessage,
        ServiceEntry, ForwardEntry, NamespaceEntry, Interaction,
    ))
)]
pub struct ApiDoc;

pub fn openapi_json() -> Vec<u8> {
    let spec = ApiDoc::openapi();
    serde_json::to_vec_pretty(&spec).unwrap_or_default()
}
