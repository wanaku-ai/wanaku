#![expect(
    dead_code,
    reason = "utoipa OpenAPI stubs — empty function bodies used only for path annotations"
)]

use utoipa::OpenApi;

use wanaku_feature_action_policy::api::{
    ActionPolicyRevisionResponse, ActivateRevisionRequest as ActivatePolicyRevisionRequest,
    UpdateActionPolicyRequest,
};
use wanaku_feature_action_policy::{
    ActionPolicy, Effect, MatchExpression, MatchKind, Predicate, Rule, Selectors, TargetType,
};
use wanaku_feature_evaluator::api::{
    ActivateRevisionRequest as ActivateEvaluatorRevisionRequest, BindNamespaceRequest,
    EvaluatorRevisionResponse, NamespaceBinding, UnbindNamespaceResponse, UpdateEvaluatorsRequest,
};
use wanaku_feature_evaluator::config::{
    ErrorPolicy, EvaluatorDef, LlmDef, LlmOperation, ProcessorRef, TriggerDef,
};
use wanaku_types::revision::{ActivationStatus, RevisionMetadata, RevisionOrigin};

use wanaku_infra::metrics::{
    DecisionSnapshot, DurationSnapshot, EvaluatorSnapshot, FilterSnapshot, GaugeSnapshot,
    LlmSnapshot, MetricsSnapshot, PipelineSnapshot, SchemaSnapshot, WasmSnapshot,
};
use wanaku_types::interactions::Interaction;
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

// -- OpenAPI Aggregation ------------------------------------------------------

struct OptionalActivationBodies;

impl utoipa::Modify for OptionalActivationBodies {
    fn modify(&self, openapi: &mut utoipa::openapi::OpenApi) {
        for path in [
            "/api/v1/evaluators/revisions/{id}/activate",
            "/api/v1/action-policies/revisions/{id}/activate",
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

#[derive(OpenApi)]
#[openapi(
    info(
        title = "Wanaku API",
        version = "0.3.0",
        description = "Wanaku MCP proxy management API"
    ),
    paths(
        healthz,
        list_tools,
        get_tool,
        delete_tool,
        list_resources,
        get_resource,
        delete_resource,
        list_prompts,
        get_prompt,
        delete_prompt,
        list_namespaces,
        get_namespace,
        create_namespace,
        delete_namespace,
        list_forwards,
        get_forward,
        create_forward,
        delete_forward,
        refresh_forward,
        list_interactions,
        clear_interactions,
        get_metrics,
        get_info,
        get_statistics,
        list_evaluators,
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
    ),
    modifiers(&OptionalActivationBodies),
    components(schemas(
        ToolEntry,
        ResourceEntry,
        PromptEntry,
        PromptArgument,
        PromptMessage,
        ForwardEntry,
        McpServerInfo,
        NamespaceEntry,
        Interaction,
        MetricsSnapshot,
        FilterSnapshot,
        DurationSnapshot,
        EvaluatorSnapshot,
        DecisionSnapshot,
        LlmSnapshot,
        SchemaSnapshot,
        WasmSnapshot,
        PipelineSnapshot,
        GaugeSnapshot,
        EvaluatorDef,
        TriggerDef,
        LlmDef,
        LlmOperation,
        ProcessorRef,
        ErrorPolicy,
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
        ManagementErrorResponse,
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
    fn includes_evaluator_and_action_policy_contracts() {
        let document = ApiDoc::openapi();
        let value = serde_json::to_value(document).unwrap_or_default();
        let paths = value.get("paths").and_then(serde_json::Value::as_object);
        let schemas = value
            .pointer("/components/schemas")
            .and_then(serde_json::Value::as_object);

        for path in [
            "/api/v1/evaluators",
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
        ] {
            assert!(
                paths.is_some_and(|paths| paths.contains_key(path)),
                "missing {path}"
            );
        }

        for schema in [
            "EvaluatorDef",
            "UpdateEvaluatorsRequest",
            "EvaluatorRevisionResponse",
            "ActionPolicy",
            "Predicate",
            "UpdateActionPolicyRequest",
            "ActionPolicyRevisionResponse",
            "RevisionMetadata",
            "ManagementErrorResponse",
        ] {
            assert!(
                schemas.is_some_and(|schemas| schemas.contains_key(schema)),
                "missing {schema}"
            );
        }
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
