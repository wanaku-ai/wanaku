use crate::http_response::{json_err, json_ok};
use http::{Response, StatusCode};
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::agents::AgentEntry;

fn view(entry: AgentEntry) -> wanaku_types::agents::AgentView {
    entry.view(&wanaku_types::config::ENV.a2a_public_url)
}

pub(super) fn list(registry: &InMemoryRegistry, query: Option<&str>) -> Response<Vec<u8>> {
    let namespace = query.and_then(|query| {
        query
            .split('&')
            .find_map(|pair| pair.strip_prefix("namespace="))
    });
    if let Some(namespace) = namespace
        && let Err(error) = wanaku_types::registry::validate_namespace_name(namespace)
    {
        return json_err(StatusCode::BAD_REQUEST, &error);
    }
    let mut agents: Vec<_> = registry
        .list_agents()
        .into_iter()
        .filter(|agent| namespace.is_none_or(|namespace| agent.namespace == namespace))
        .map(view)
        .collect();
    agents.sort_by(|a, b| (&a.namespace, &a.name).cmp(&(&b.namespace, &b.name)));
    json_ok(&serde_json::json!(agents))
}

pub(super) fn get(registry: &InMemoryRegistry, namespace: &str, name: &str) -> Response<Vec<u8>> {
    registry.get_agent(namespace, name).map_or_else(
        || json_err(StatusCode::NOT_FOUND, "agent not found"),
        |entry| json_ok(&serde_json::json!(view(entry))),
    )
}

pub(super) fn save(
    registry: &InMemoryRegistry,
    body: &str,
    identity: Option<(&str, &str)>,
) -> Response<Vec<u8>> {
    let entry: AgentEntry = match serde_json::from_str(body) {
        Ok(entry) => entry,
        Err(error) => {
            return json_err(
                StatusCode::BAD_REQUEST,
                &format!("invalid agent JSON: {error}"),
            );
        }
    };
    if identity.is_some_and(|(namespace, name)| entry.namespace != namespace || entry.name != name)
    {
        return json_err(
            StatusCode::BAD_REQUEST,
            "agent name and namespace must match the route",
        );
    }
    match registry.save_agent(entry.clone(), identity.is_some()) {
        Ok(true) => json_ok(&serde_json::json!(view(entry))),
        Ok(false) if identity.is_some() => json_err(StatusCode::NOT_FOUND, "agent not found"),
        Ok(false) => json_err(StatusCode::CONFLICT, "agent already exists"),
        Err(error) => json_err(StatusCode::BAD_REQUEST, &error),
    }
}

pub(super) fn delete(
    registry: &InMemoryRegistry,
    namespace: &str,
    name: &str,
) -> Response<Vec<u8>> {
    if registry.remove_agent(namespace, name) {
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(StatusCode::NOT_FOUND, "agent not found")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "CRUD status and immutable identity fixtures"
    )]
    fn crud_preserves_identity_and_rejects_invalid_replacements() {
        let registry = InMemoryRegistry::new();
        let initial = r#"{"name":"worker","address":"http://first.example/rpc"}"#;
        assert_eq!(save(&registry, initial, None).status(), 200);
        assert_eq!(save(&registry, initial, None).status(), 409);
        assert_eq!(get(&registry, "default", "worker").status(), 200);
        assert_eq!(get(&registry, "blue", "worker").status(), 404);
        let invalid = r#"{"name":"worker","address":"file:///tmp/file"}"#;
        assert_eq!(
            save(&registry, invalid, Some(("default", "worker"))).status(),
            400
        );
        assert_eq!(
            registry
                .get_agent("default", "worker")
                .expect("unchanged agent")
                .address,
            "http://first.example/rpc"
        );
        let moved = r#"{"name":"other","address":"https://second.example/rpc"}"#;
        assert_eq!(
            save(&registry, moved, Some(("default", "worker"))).status(),
            400
        );
        assert_eq!(
            save(&registry, initial, Some(("blue", "worker"))).status(),
            400
        );
        let updated =
            r#"{"name":"worker","description":"updated","address":"https://second.example/rpc"}"#;
        assert_eq!(
            save(&registry, updated, Some(("default", "worker"))).status(),
            200
        );
        let body: serde_json::Value =
            serde_json::from_slice(list(&registry, Some("namespace=blue")).body()).expect("list");
        assert_eq!(body["data"], serde_json::json!([]));
        assert_eq!(delete(&registry, "default", "worker").status(), 200);
        assert_eq!(delete(&registry, "default", "worker").status(), 404);
        assert_eq!(
            save(&registry, initial, Some(("default", "worker"))).status(),
            404
        );
    }
}
