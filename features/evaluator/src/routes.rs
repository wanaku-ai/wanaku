use http::Response;
use tracing::info;
use wanaku_praxis_apis::http_response::{json_err, json_ok};

use crate::config::EvaluatorsConfig;
use crate::state::EvaluatorState;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum EvaluatorRoute {
    ListEvaluators,
    UpdateEvaluators,
    ListBindings,
    BindNamespace(String),
    UnbindNamespace(String),
    NotFound,
}

pub(crate) fn resolve_evaluator_route(method: &str, path: &str) -> EvaluatorRoute {
    let suffix = match path.strip_prefix("/api/v1/evaluators") {
        Some(s) => s,
        None => return EvaluatorRoute::NotFound,
    };

    if suffix.is_empty() || suffix == "/" {
        return match method {
            "GET" => EvaluatorRoute::ListEvaluators,
            "PUT" => EvaluatorRoute::UpdateEvaluators,
            _ => EvaluatorRoute::NotFound,
        };
    }

    let ns_suffix = match suffix.strip_prefix("/namespaces") {
        Some(s) => s,
        None => return EvaluatorRoute::NotFound,
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

pub(crate) fn handle_list_evaluators(state: &EvaluatorState) -> Response<Vec<u8>> {
    json_ok(&serde_json::json!(state.list_evaluators()))
}

pub(crate) fn handle_update_evaluators(
    state: &EvaluatorState,
    body: &str,
) -> Response<Vec<u8>> {
    let config: EvaluatorsConfig = match serde_json::from_str(body) {
        Ok(c) => c,
        Err(e) => return json_err(400, &format!("invalid evaluators config: {e}")),
    };

    let count = config.evaluators.len();
    info!(count = count, "evaluators updated via management API");
    state.load_evaluators(config.evaluators.clone());

    json_ok(&serde_json::json!(config.evaluators))
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
        Err(e) => return json_err(400, &format!("invalid bind request: {e}")),
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
