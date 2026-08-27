use http::{Response, StatusCode};
use wanaku_types::http_response::{json_err, json_ok};

use crate::revision::{ActionPolicyRevision, RevisionError, RevisionOrigin};
use crate::state::ActivatePolicyParams;
use crate::{ActionPolicy, ActionPolicyState};

pub(crate) fn handle_effective(state: &ActionPolicyState) -> Response<Vec<u8>> {
    revision_response(state.revision_store().active_revision())
}

#[derive(serde::Deserialize)]
struct UpdateRequest {
    policy: ActionPolicy,
    #[serde(default)]
    expected_revision: Option<u64>,
}

pub(crate) fn handle_update(state: &ActionPolicyState, body: &str) -> Response<Vec<u8>> {
    let request = match parse_update_request(body) {
        Ok(request) => request,
        Err(error) => {
            return json_err(
                StatusCode::BAD_REQUEST,
                &format!("invalid action policy request: {error}"),
            );
        }
    };
    match state.try_activate(
        request.policy,
        ActivatePolicyParams {
            origin: RevisionOrigin::Api,
            actor: None,
            expected_revision: request.expected_revision,
        },
    ) {
        Ok(revision) => revision_ok(&revision),
        Err(error) => revision_error(error),
    }
}

fn parse_update_request(body: &str) -> Result<UpdateRequest, serde_json::Error> {
    match serde_json::from_str(body) {
        Ok(request) => Ok(request),
        Err(wrapper_error) => match serde_json::from_str(body) {
            Ok(policy) => Ok(UpdateRequest {
                policy,
                expected_revision: None,
            }),
            Err(_) => Err(wrapper_error),
        },
    }
}

pub(crate) fn handle_list_revisions(state: &ActionPolicyState) -> Response<Vec<u8>> {
    json_ok(&serde_json::json!(state.revision_store().list_revisions()))
}

pub(crate) fn handle_active_revision(state: &ActionPolicyState) -> Response<Vec<u8>> {
    revision_response(state.revision_store().active_revision())
}

pub(crate) fn handle_get_revision(state: &ActionPolicyState, id: u64) -> Response<Vec<u8>> {
    revision_response(state.revision_store().get_revision(id))
}

pub(crate) fn handle_activate_revision(
    state: &ActionPolicyState,
    id: u64,
    body: &str,
) -> Response<Vec<u8>> {
    #[derive(serde::Deserialize, Default)]
    struct ActivateRequest {
        #[serde(default)]
        expected_revision: Option<u64>,
    }
    let request = if body.is_empty() {
        ActivateRequest::default()
    } else {
        match serde_json::from_str(body) {
            Ok(request) => request,
            Err(error) => {
                return json_err(
                    StatusCode::BAD_REQUEST,
                    &format!("invalid activate request: {error}"),
                );
            }
        }
    };
    match state.rollback(id, request.expected_revision) {
        Ok(revision) => revision_ok(&revision),
        Err(error) => revision_error(error),
    }
}

fn revision_response(revision: Option<ActionPolicyRevision>) -> Response<Vec<u8>> {
    match revision {
        Some(revision) => revision_ok(&revision),
        None => json_err(StatusCode::NOT_FOUND, "no action policy revision found"),
    }
}

fn revision_ok(revision: &ActionPolicyRevision) -> Response<Vec<u8>> {
    json_ok(&serde_json::json!({"revision": revision.metadata, "policy": revision.policy}))
}

fn revision_error(error: RevisionError) -> Response<Vec<u8>> {
    match error {
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

#[cfg(test)]
mod tests {
    use super::*;

    fn request(id: &str) -> String {
        serde_json::json!({"policy": {"rules": [{"id": id, "effect": "deny", "selectors": {"operation": "tools/call"}}]}}).to_string()
    }

    #[test]
    fn update_query_conflict_and_rollback() {
        let state = ActionPolicyState::new();
        assert_eq!(
            handle_update(&state, &request("first")).status(),
            StatusCode::OK
        );
        assert_eq!(handle_effective(&state).status(), StatusCode::OK);
        assert_eq!(handle_list_revisions(&state).status(), StatusCode::OK);
        assert_eq!(handle_get_revision(&state, 1).status(), StatusCode::OK);
        assert_eq!(
            handle_get_revision(&state, 99).status(),
            StatusCode::NOT_FOUND
        );
        let conflict = serde_json::json!({"policy": {"rules": [{"id": "second", "effect": "allow", "selectors": {"operation": "prompts/get"}}]}, "expected_revision": 99}).to_string();
        assert_eq!(
            handle_update(&state, &conflict).status(),
            StatusCode::CONFLICT
        );
        assert_eq!(
            handle_activate_revision(&state, 1, r#"{"expected_revision":1}"#).status(),
            StatusCode::OK
        );
        assert_eq!(state.revision_store().active_revision_id(), Some(2));
    }

    #[test]
    fn invalid_candidate_is_rejected_without_replacing_active() {
        let state = ActionPolicyState::new();
        assert_eq!(
            handle_update(&state, &request("valid")).status(),
            StatusCode::OK
        );
        let invalid = serde_json::json!({"policy": {"rules": [{"id": "invalid", "effect": "deny", "selectors": {}}]}}).to_string();
        assert_eq!(
            handle_update(&state, &invalid).status(),
            StatusCode::UNPROCESSABLE_ENTITY
        );
        assert_eq!(state.revision_store().active_revision_id(), Some(1));
        assert_eq!(state.revision_store().list_revisions().len(), 2);
    }
}
