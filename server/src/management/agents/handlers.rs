use crate::http_response::{json_err, json_ok};
use crate::management::handlers::label_precondition;
use http::{Response, StatusCode};
use wanaku_infra::registry::{ConditionalRemoval, InMemoryRegistry};
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
    query: Option<&str>,
) -> Response<Vec<u8>> {
    let precondition = label_precondition(query);
    match registry.remove_agent_if(namespace, name, |agent| precondition(&agent.labels)) {
        ConditionalRemoval::Removed(_) => json_ok(&serde_json::json!({"removed": name})),
        ConditionalRemoval::NotFound => json_err(StatusCode::NOT_FOUND, "agent not found"),
        ConditionalRemoval::PreconditionFailed => json_err(
            StatusCode::CONFLICT,
            &format!("agent {namespace}/{name} label precondition failed"),
        ),
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
        assert_eq!(delete(&registry, "default", "worker", None).status(), 200);
        assert_eq!(delete(&registry, "default", "worker", None).status(), 404);
        assert_eq!(
            save(&registry, initial, Some(("default", "worker"))).status(),
            404
        );
    }

    fn body(response: &Response<Vec<u8>>) -> serde_json::Value {
        serde_json::from_slice(response.body()).expect("JSON body")
    }

    #[test]
    fn labels_round_trip_through_create_get_and_list() {
        let registry = InMemoryRegistry::new();
        let created = r#"{"name":"worker","address":"http://a.example/rpc","labels":{"cic.instance":"pod-a"}}"#;
        let response = save(&registry, created, None);
        assert_eq!(response.status(), 200);
        assert_eq!(body(&response)["data"]["labels"]["cic.instance"], "pod-a");
        let fetched = body(&get(&registry, "default", "worker"));
        assert_eq!(fetched["data"]["labels"]["cic.instance"], "pod-a");
        let listed = body(&list(&registry, None));
        assert_eq!(listed["data"][0]["labels"]["cic.instance"], "pod-a");
    }

    #[test]
    fn update_replaces_labels() {
        let registry = InMemoryRegistry::new();
        let created = r#"{"name":"worker","address":"http://a.example/rpc","labels":{"cic.instance":"pod-a"}}"#;
        assert_eq!(save(&registry, created, None).status(), 200);
        let updated = r#"{"name":"worker","address":"http://a.example/rpc","labels":{"cic.instance":"pod-b"}}"#;
        assert_eq!(
            save(&registry, updated, Some(("default", "worker"))).status(),
            200
        );
        let fetched = body(&get(&registry, "default", "worker"));
        assert_eq!(fetched["data"]["labels"]["cic.instance"], "pod-b");

        let unlabeled = r#"{"name":"worker","address":"http://a.example/rpc"}"#;
        assert_eq!(
            save(&registry, unlabeled, Some(("default", "worker"))).status(),
            200
        );
        let fetched = body(&get(&registry, "default", "worker"));
        assert!(fetched["data"].get("labels").is_none());
    }

    fn delete_worker(registry: &InMemoryRegistry, query: &str) -> Response<Vec<u8>> {
        delete(registry, "default", "worker", Some(query))
    }

    #[test]
    fn conditional_delete_follows_label_precondition() {
        let registry = InMemoryRegistry::new();
        let labeled = r#"{"name":"worker","address":"http://a.example/rpc","labels":{"cic.instance":"pod-b"}}"#;
        assert_eq!(save(&registry, labeled, None).status(), 200);

        let stale = delete_worker(&registry, "label.cic.instance=pod-a");
        assert_eq!(stale.status(), 409);
        assert_eq!(
            body(&stale)["error"],
            "agent default/worker label precondition failed"
        );
        let absent = delete_worker(&registry, "label.cic.instance=pod-b&label.zone=eu");
        assert_eq!(absent.status(), 409);
        assert!(registry.get_agent("default", "worker").is_some());

        let owner = delete_worker(&registry, "label.cic.instance=pod-b");
        assert_eq!(owner.status(), 200);
        assert!(registry.get_agent("default", "worker").is_none());
        let missing = delete_worker(&registry, "label.cic.instance=pod-b");
        assert_eq!(missing.status(), 404);
    }

    #[test]
    fn delete_without_label_parameters_is_unconditional() {
        let registry = InMemoryRegistry::new();
        let labeled = r#"{"name":"worker","address":"http://a.example/rpc","labels":{"cic.instance":"pod-b"}}"#;
        assert_eq!(save(&registry, labeled, None).status(), 200);
        assert_eq!(delete_worker(&registry, "namespace=x").status(), 200);
        assert!(registry.get_agent("default", "worker").is_none());
    }
}
