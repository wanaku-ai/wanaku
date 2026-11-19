use crate::config::HandlerDefinition;
use crate::routes::HandlerRoute;
use crate::state::HandlerState;
use http::{Response, StatusCode};
use serde_json::json;
use wanaku_types::http_response::{json_err, json_ok};

pub(crate) async fn dispatch(
    route: HandlerRoute<'_>,
    state: &HandlerState,
    body: Option<&str>,
) -> Option<Response<Vec<u8>>> {
    Some(match route {
        HandlerRoute::List => match state.list() {
            Ok(definitions) => json_ok(&json!(definitions)),
            Err(error) => json_err(StatusCode::SERVICE_UNAVAILABLE, &error),
        },
        HandlerRoute::Replace => replace(state, body.unwrap_or("")).await,
        HandlerRoute::Get(namespace, name) => match state.get(namespace, name) {
            Ok(Some(definition)) => json_ok(&json!(definition)),
            Ok(None) => json_err(StatusCode::NOT_FOUND, "handler not found"),
            Err(error) => json_err(StatusCode::SERVICE_UNAVAILABLE, &error),
        },
        HandlerRoute::Delete(namespace, name) => match state.remove(namespace, name) {
            Ok(true) => json_ok(&json!({"removed":true})),
            Ok(false) => json_err(StatusCode::NOT_FOUND, "handler not found"),
            Err(error) => json_err(StatusCode::CONFLICT, &error),
        },
        HandlerRoute::NotFound => return None,
    })
}

async fn replace(state: &HandlerState, body: &str) -> Response<Vec<u8>> {
    let definitions: Vec<HandlerDefinition> = match serde_json::from_str(body) {
        Ok(definitions) => definitions,
        Err(error) => {
            return json_err(
                StatusCode::BAD_REQUEST,
                &format!("invalid handler definitions: {error}"),
            );
        }
    };
    let state = state.clone();
    match tokio::task::spawn_blocking(move || state.replace(definitions)).await {
        Ok(Ok(())) => json_ok(&json!({"activated":true})),
        Ok(Err(error)) => json_err(StatusCode::BAD_REQUEST, &error),
        Err(_) => json_err(
            StatusCode::INTERNAL_SERVER_ERROR,
            "handler activation task failed",
        ),
    }
}
