use http::{Response, StatusCode};
use tracing::info;
use wanaku_apis::http_response::{json_err, json_ok};

use crate::config::EvaluatorsConfig;
use crate::revision::{RevisionError, RevisionOrigin};
use crate::state::EvaluatorState;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum EvaluatorRoute {
    ListEvaluators,
    UpdateEvaluators,
    ListBindings,
    BindNamespace(String),
    UnbindNamespace(String),
    /// GET /api/v1/evaluators/revisions
    ListRevisions,
    /// GET /api/v1/evaluators/revisions/active
    ActiveRevision,
    /// GET /api/v1/evaluators/revisions/{id}
    GetRevision(u64),
    /// POST /api/v1/evaluators/revisions/{id}/activate
    ActivateRevision(u64),
    NotFound,
}

pub(crate) fn resolve_evaluator_route(method: &str, path: &str) -> EvaluatorRoute {
    let Some(suffix) = path.strip_prefix("/api/v1/evaluators") else {
        return EvaluatorRoute::NotFound;
    };

    if suffix.is_empty() || suffix == "/" {
        return match method {
            "GET" => EvaluatorRoute::ListEvaluators,
            "PUT" => EvaluatorRoute::UpdateEvaluators,
            _ => EvaluatorRoute::NotFound,
        };
    }

    // Check for /revisions routes before /namespaces.
    if let Some(rev_suffix) = suffix.strip_prefix("/revisions") {
        return resolve_revision_route(method, rev_suffix);
    }

    let Some(ns_suffix) = suffix.strip_prefix("/namespaces") else {
        return EvaluatorRoute::NotFound;
    };

    if ns_suffix.is_empty() || ns_suffix == "/" {
        return match method {
            "GET" => EvaluatorRoute::ListBindings,
            _ => EvaluatorRoute::NotFound,
        };
    }

    let name = ns_suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty() && !s.contains('/'));

    match (method, name) {
        ("PUT", Some(n)) => EvaluatorRoute::BindNamespace(n.to_owned()),
        ("DELETE", Some(n)) => EvaluatorRoute::UnbindNamespace(n.to_owned()),
        _ => EvaluatorRoute::NotFound,
    }
}

fn resolve_revision_route(method: &str, suffix: &str) -> EvaluatorRoute {
    if suffix.is_empty() || suffix == "/" {
        return match method {
            "GET" => EvaluatorRoute::ListRevisions,
            _ => EvaluatorRoute::NotFound,
        };
    }

    let segment = suffix.strip_prefix('/').unwrap_or(suffix);

    if segment == "active" && method == "GET" {
        return EvaluatorRoute::ActiveRevision;
    }

    // Check for /revisions/{id} and /revisions/{id}/activate.
    let (id_str, action) = match segment.split_once('/') {
        Some((id, rest)) => (id, Some(rest)),
        None => (segment, None),
    };

    let Ok(id) = id_str.parse::<u64>() else {
        return EvaluatorRoute::NotFound;
    };

    match (method, action) {
        ("GET", None) => EvaluatorRoute::GetRevision(id),
        ("POST", Some("activate")) => EvaluatorRoute::ActivateRevision(id),
        _ => EvaluatorRoute::NotFound,
    }
}

pub(crate) fn handle_list_evaluators(state: &EvaluatorState) -> Response<Vec<u8>> {
    json_ok(&serde_json::json!(state.list_evaluators()))
}

/// Request body for the update evaluators endpoint. Optionally includes
/// an expected revision for optimistic concurrency control.
#[derive(serde::Deserialize)]
struct UpdateEvaluatorsRequest {
    #[serde(default)]
    evaluators: Vec<crate::config::EvaluatorDef>,
    /// When set, the update is rejected with 409 Conflict if the current
    /// active revision does not match this value.
    #[serde(default)]
    expected_revision: Option<u64>,
}

#[expect(
    clippy::too_many_lines,
    reason = "request parsing with legacy fallback and revision error handling"
)]
pub(crate) fn handle_update_evaluators(state: &EvaluatorState, body: &str) -> Response<Vec<u8>> {
    // First try the new request format with optional expected_revision.
    let (evaluators, expected_revision) =
        match serde_json::from_str::<UpdateEvaluatorsRequest>(body) {
            Ok(req) => (req.evaluators, req.expected_revision),
            Err(_) => {
                // Fall back to the legacy format (plain EvaluatorsConfig).
                match serde_json::from_str::<EvaluatorsConfig>(body) {
                    Ok(config) => (config.evaluators, None),
                    Err(e) => {
                        return json_err(
                            StatusCode::BAD_REQUEST,
                            &format!("invalid evaluators config: {e}"),
                        );
                    }
                }
            }
        };

    let count = evaluators.len();
    info!(
        count = count,
        "evaluators update requested via management API"
    );

    match state.try_activate(evaluators, RevisionOrigin::Api, None, expected_revision) {
        Ok(revision) => {
            info!(
                revision_id = revision.metadata.id,
                count = count,
                "evaluators updated via management API"
            );
            json_ok(&serde_json::json!({
                "revision": revision.metadata,
                "evaluators": revision.evaluators,
            }))
        }
        Err(RevisionError::Conflict { expected, actual }) => json_err(
            StatusCode::CONFLICT,
            &format!("stale update: expected active revision {expected}, but current is {actual}"),
        ),
        Err(RevisionError::ValidationFailed(reason)) => json_err(
            StatusCode::UNPROCESSABLE_ENTITY,
            &format!("configuration rejected: {reason}"),
        ),
        Err(e) => json_err(
            StatusCode::INTERNAL_SERVER_ERROR,
            &format!("failed to activate configuration: {e}"),
        ),
    }
}

pub(crate) fn handle_list_revisions(state: &EvaluatorState) -> Response<Vec<u8>> {
    let revisions = state.revision_store().list_revisions();
    json_ok(&serde_json::json!(revisions))
}

pub(crate) fn handle_active_revision(state: &EvaluatorState) -> Response<Vec<u8>> {
    match state.revision_store().active_revision() {
        Some(rev) => json_ok(&serde_json::json!({
            "revision": rev.metadata,
            "evaluators": rev.evaluators,
        })),
        None => json_err(StatusCode::NOT_FOUND, "no active revision"),
    }
}

pub(crate) fn handle_get_revision(state: &EvaluatorState, id: u64) -> Response<Vec<u8>> {
    match state.revision_store().get_revision(id) {
        Some(rev) => json_ok(&serde_json::json!({
            "revision": rev.metadata,
            "evaluators": rev.evaluators,
        })),
        None => json_err(StatusCode::NOT_FOUND, &format!("revision {id} not found")),
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "rollback request parsing with concurrency and error handling"
)]
pub(crate) fn handle_activate_revision(
    state: &EvaluatorState,
    source_id: u64,
    body: &str,
) -> Response<Vec<u8>> {
    #[derive(serde::Deserialize, Default)]
    struct ActivateRequest {
        #[serde(default)]
        expected_revision: Option<u64>,
    }

    let req: ActivateRequest = if body.is_empty() {
        ActivateRequest::default()
    } else {
        match serde_json::from_str(body) {
            Ok(r) => r,
            Err(e) => {
                return json_err(
                    StatusCode::BAD_REQUEST,
                    &format!("invalid activate request: {e}"),
                );
            }
        }
    };

    match state.rollback(source_id, req.expected_revision) {
        Ok(revision) => {
            info!(
                source_id = source_id,
                new_revision_id = revision.metadata.id,
                "evaluator configuration restored from revision"
            );
            json_ok(&serde_json::json!({
                "revision": revision.metadata,
                "evaluators": revision.evaluators,
            }))
        }
        Err(RevisionError::NotFound(id)) => {
            json_err(StatusCode::NOT_FOUND, &format!("revision {id} not found"))
        }
        Err(RevisionError::Conflict { expected, actual }) => json_err(
            StatusCode::CONFLICT,
            &format!("stale restore: expected active revision {expected}, but current is {actual}"),
        ),
        Err(RevisionError::ValidationFailed(reason)) => json_err(
            StatusCode::UNPROCESSABLE_ENTITY,
            &format!("restored configuration failed validation: {reason}"),
        ),
    }
}

pub(crate) fn handle_list_bindings(state: &EvaluatorState) -> Response<Vec<u8>> {
    json_ok(&serde_json::json!(state.list_bindings()))
}

pub(crate) fn handle_bind_namespace(
    state: &EvaluatorState,
    namespace: &str,
    body: &str,
) -> Response<Vec<u8>> {
    #[derive(serde::Deserialize)]
    struct BindRequest {
        conversation_id: String,
    }

    let req: BindRequest = match serde_json::from_str(body) {
        Ok(r) => r,
        Err(e) => {
            return json_err(
                StatusCode::BAD_REQUEST,
                &format!("invalid bind request: {e}"),
            );
        }
    };

    info!(
        namespace = %namespace,
        conversation_id = %req.conversation_id,
        "namespace bound to conversation"
    );
    state.bind_namespace(namespace, &req.conversation_id);

    json_ok(&serde_json::json!({
        "namespace": namespace,
        "conversation_id": req.conversation_id,
    }))
}

pub(crate) fn handle_unbind_namespace(
    state: &EvaluatorState,
    namespace: &str,
) -> Response<Vec<u8>> {
    state.unbind_namespace(namespace);
    info!(namespace = %namespace, "namespace unbound");
    json_ok(&serde_json::json!({"unbound": namespace}))
}
