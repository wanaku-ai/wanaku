use bytes::Bytes;
use praxis_filter::{FilterAction, HttpFilterContext};
use wanaku_infra::registry::RuntimeRegistry;
use wanaku_types::registry::NamespaceRegistry;

pub(super) fn catalog(ctx: &HttpFilterContext<'_>, public_origin: &str) -> Option<FilterAction> {
    let name = ctx
        .request
        .uri
        .path()
        .strip_prefix("/virtual-ns/")?
        .strip_suffix("/a2a")?;
    if name.contains('/') || wanaku_types::registry::validate_namespace_name(name).is_err() {
        return Some(super::route_error(404, "virtual namespace not found"));
    }
    if ctx.request.method != http::Method::GET {
        return Some(super::route_error(
            405,
            "use GET to list virtual namespace agents",
        ));
    }
    let Some(registry) = ctx.extensions.get::<RuntimeRegistry>() else {
        return Some(super::route_error(503, "registry unavailable"));
    };
    if registry
        .get_namespace(name)
        .is_none_or(|entry| !entry.is_virtual())
    {
        return Some(super::route_error(404, "virtual namespace not found"));
    }
    let mut agents: Vec<_> = registry
        .list_virtual_agents(name)
        .into_iter()
        .map(|entry| {
            let url = format!(
                "{}/virtual-ns/{name}/a2a/{}",
                public_origin.trim_end_matches('/'),
                entry.name
            );
            serde_json::json!({
                "name": entry.name,
                "namespace": entry.namespace,
                "description": entry.description,
                "url": url,
                "cardUrl": format!("{url}/.well-known/agent-card.json"),
            })
        })
        .collect();
    agents.sort_by(|left, right| left["name"].as_str().cmp(&right["name"].as_str()));
    Some(FilterAction::Reject(
        wanaku_filters::response::json_response(Bytes::from(
            serde_json::json!({"agents": agents}).to_string(),
        )),
    ))
}

pub(super) fn route(path: &str) -> Option<(&str, &str, &str, bool)> {
    let suffix = path.strip_prefix("/virtual-ns/")?;
    let (group, suffix) = suffix.split_once("/a2a/")?;
    let (namespace, suffix) = suffix.split_once('/')?;
    let (name, card) = match suffix.strip_suffix("/.well-known/agent-card.json") {
        Some(name) => (name, true),
        None => (suffix, false),
    };
    if [group, namespace, name]
        .iter()
        .any(|name| wanaku_types::registry::validate_namespace_name(name).is_err())
    {
        return None;
    }
    Some((group, namespace, name, card))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn virtual_routes_require_a_concrete_namespace_and_agent() {
        assert_eq!(
            route("/virtual-ns/integration/a2a/kafka/worker"),
            Some(("integration", "kafka", "worker", false))
        );
        assert_eq!(
            route("/virtual-ns/integration/a2a/kafka/worker/.well-known/agent-card.json"),
            Some(("integration", "kafka", "worker", true))
        );
        for path in [
            "/virtual-ns/integration/a2a/worker",
            "/virtual-ns/integration/a2a/kafka/worker/extra",
            "/virtual-ns/integration/a2a/../worker",
        ] {
            assert_eq!(route(path), None);
        }
    }
}
