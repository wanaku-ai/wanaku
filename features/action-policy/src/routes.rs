#[derive(Debug, PartialEq, Eq)]
pub(crate) enum ActionPolicyRoute {
    Effective,
    Update,
    ListRevisions,
    ActiveRevision,
    GetRevision(u64),
    ActivateRevision(u64),
    NotFound,
}

pub(crate) fn resolve_action_policy_route(method: &str, path: &str) -> ActionPolicyRoute {
    let Some(suffix) = path.strip_prefix("/api/v1/action-policies") else {
        return ActionPolicyRoute::NotFound;
    };
    if suffix.is_empty() || suffix == "/" {
        return match method {
            "GET" => ActionPolicyRoute::Effective,
            "PUT" => ActionPolicyRoute::Update,
            _ => ActionPolicyRoute::NotFound,
        };
    }
    let Some(suffix) = suffix.strip_prefix("/revisions") else {
        return ActionPolicyRoute::NotFound;
    };
    resolve_revision_route(method, suffix)
}

fn resolve_revision_route(method: &str, suffix: &str) -> ActionPolicyRoute {
    if suffix.is_empty() || suffix == "/" {
        return if method == "GET" {
            ActionPolicyRoute::ListRevisions
        } else {
            ActionPolicyRoute::NotFound
        };
    }
    let segment = suffix.strip_prefix('/').unwrap_or(suffix);
    if segment == "active" && method == "GET" {
        return ActionPolicyRoute::ActiveRevision;
    }
    let (id, action) = segment
        .split_once('/')
        .map_or((segment, None), |(id, action)| (id, Some(action)));
    let Ok(id) = id.parse() else {
        return ActionPolicyRoute::NotFound;
    };
    match (method, action) {
        ("GET", None) => ActionPolicyRoute::GetRevision(id),
        ("POST", Some("activate")) => ActionPolicyRoute::ActivateRevision(id),
        _ => ActionPolicyRoute::NotFound,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolves_resource_family() {
        assert_eq!(
            resolve_action_policy_route("GET", "/api/v1/action-policies"),
            ActionPolicyRoute::Effective
        );
        assert_eq!(
            resolve_action_policy_route("PUT", "/api/v1/action-policies"),
            ActionPolicyRoute::Update
        );
        assert_eq!(
            resolve_action_policy_route("GET", "/api/v1/action-policies/revisions/7"),
            ActionPolicyRoute::GetRevision(7)
        );
        assert_eq!(
            resolve_action_policy_route("POST", "/api/v1/action-policies/revisions/7/activate"),
            ActionPolicyRoute::ActivateRevision(7)
        );
        assert_eq!(
            resolve_action_policy_route("GET", "/api/v1/action-policies/revisions/active"),
            ActionPolicyRoute::ActiveRevision
        );
    }
}
