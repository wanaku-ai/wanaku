use http::{Response, StatusCode};
use tracing::info;
use wanaku_apis::http_response::{json_err, json_ok};

use crate::config::EvaluatorsConfig;
use crate::revision::{RevisionError, RevisionOrigin};
use crate::state::EvaluatorState;

type ParseResult<T> = Result<T, Box<Response<Vec<u8>>>>;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum EvaluatorRoute {
    ListEvaluators,
    UpdateEvaluators,
    ListLlmConnections,
    ListBindings,
    BindNamespace(String),
    UnbindNamespace(String),
    ListRevisions,
    ActiveRevision,
    GetRevision(u64),
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

    if let Some(conn_suffix) = suffix.strip_prefix("/llm-connections") {
        return resolve_llm_connections_route(method, conn_suffix);
    }

    let Some(ns_suffix) = suffix.strip_prefix("/namespaces") else {
        return EvaluatorRoute::NotFound;
    };
    resolve_namespace_route(method, ns_suffix)
}

fn resolve_llm_connections_route(method: &str, suffix: &str) -> EvaluatorRoute {
    if !suffix.is_empty() && suffix != "/" {
        return EvaluatorRoute::NotFound;
    }
    match method {
        "GET" => EvaluatorRoute::ListLlmConnections,
        _ => EvaluatorRoute::NotFound,
    }
}

fn resolve_namespace_route(method: &str, ns_suffix: &str) -> EvaluatorRoute {
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

/// Secret-free connection summaries only — never returns `api_key`.
pub(crate) fn handle_list_llm_connections(state: &EvaluatorState) -> Response<Vec<u8>> {
    json_ok(&serde_json::json!(state.list_llm_connections()))
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

pub(crate) fn handle_update_evaluators(state: &EvaluatorState, body: &str) -> Response<Vec<u8>> {
    let (evaluators, expected_revision) = match parse_update_request(body) {
        Ok(parsed) => parsed,
        Err(resp) => return *resp,
    };

    let count = evaluators.len();
    info!(count = count, "evaluators update requested via management API");

    match state.try_activate(evaluators, RevisionOrigin::Api, None, expected_revision) {
        Ok(revision) => revision_ok_response(&revision),
        Err(e) => revision_error_response(e),
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

pub(crate) fn handle_activate_revision(
    state: &EvaluatorState,
    source_id: u64,
    body: &str,
) -> Response<Vec<u8>> {
    let expected_revision = match parse_activate_request(body) {
        Ok(rev) => rev,
        Err(resp) => return *resp,
    };

    match state.rollback(source_id, expected_revision) {
        Ok(revision) => revision_ok_response(&revision),
        Err(e) => revision_error_response(e),
    }
}

fn parse_update_request(
    body: &str,
) -> ParseResult<(Vec<crate::config::EvaluatorDef>, Option<u64>)> {
    if let Ok(req) = serde_json::from_str::<UpdateEvaluatorsRequest>(body) {
        return Ok((req.evaluators, req.expected_revision));
    }
    match serde_json::from_str::<EvaluatorsConfig>(body) {
        Ok(config) => Ok((config.evaluators, None)),
        Err(e) => Err(Box::new(json_err(
            StatusCode::BAD_REQUEST,
            &format!("invalid evaluators config: {e}"),
        ))),
    }
}

fn parse_activate_request(body: &str) -> ParseResult<Option<u64>> {
    #[derive(serde::Deserialize, Default)]
    struct ActivateRequest {
        #[serde(default)]
        expected_revision: Option<u64>,
    }

    if body.is_empty() {
        return Ok(None);
    }
    match serde_json::from_str::<ActivateRequest>(body) {
        Ok(r) => Ok(r.expected_revision),
        Err(e) => Err(Box::new(json_err(
            StatusCode::BAD_REQUEST,
            &format!("invalid activate request: {e}"),
        ))),
    }
}

fn revision_ok_response(
    revision: &crate::revision::Revision,
) -> Response<Vec<u8>> {
    json_ok(&serde_json::json!({
        "revision": revision.metadata,
        "evaluators": revision.evaluators,
    }))
}

fn revision_error_response(err: RevisionError) -> Response<Vec<u8>> {
    match err {
        RevisionError::NotFound(id) => {
            json_err(StatusCode::NOT_FOUND, &format!("revision {id} not found"))
        }
        RevisionError::Conflict { expected, actual } => json_err(
            StatusCode::CONFLICT,
            &format!("expected active revision {expected}, but current is {actual}"),
        ),
        RevisionError::ValidationFailed(reason) => json_err(
            StatusCode::UNPROCESSABLE_ENTITY,
            &format!("configuration rejected: {reason}"),
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
