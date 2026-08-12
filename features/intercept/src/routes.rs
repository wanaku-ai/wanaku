use http::Response;
use tracing::info;
use wanaku_praxis_apis::http_response::json_ok;
use wanaku_praxis_apis::interactions::{InMemoryInteractionStore, InteractionStore};

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum InteractionRoute {
    List,
    Clear,
    NotFound,
}

pub(crate) fn resolve_interaction_route(method: &str, path: &str) -> InteractionRoute {
    let suffix = match path.strip_prefix("/api/v1/interactions") {
        Some(s) => s,
        None => return InteractionRoute::NotFound,
    };

    if !suffix.is_empty() && suffix != "/" {
        return InteractionRoute::NotFound;
    }

    match method {
        "GET" => InteractionRoute::List,
        "DELETE" => InteractionRoute::Clear,
        _ => InteractionRoute::NotFound,
    }
}

pub(crate) fn handle_interaction_list(store: &InMemoryInteractionStore) -> Response<Vec<u8>> {
    let items = store.list();
    json_ok(&serde_json::json!(items))
}

pub(crate) fn handle_interaction_clear(store: &InMemoryInteractionStore) -> Response<Vec<u8>> {
    store.clear();
    info!("cleared interaction store");
    json_ok(&serde_json::json!({"cleared": true}))
}
