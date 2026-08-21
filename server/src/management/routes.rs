#[derive(Debug, PartialEq, Eq)]
pub(super) enum ToolRoute {
    List,
    GetByName(String),
    Update(String),
    Delete(String),
    NotFound,
}

pub(super) fn resolve_tool_route(method: &str, path: &str) -> ToolRoute {
    let Some(suffix) = path.strip_prefix("/api/v1/tools") else {
        return ToolRoute::NotFound;
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => ToolRoute::List,
        ("GET", Some(n)) => ToolRoute::GetByName(n.to_owned()),
        ("PUT", Some(n)) => ToolRoute::Update(n.to_owned()),
        ("DELETE", Some(n)) => ToolRoute::Delete(n.to_owned()),
        _ => ToolRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum ResourceRoute {
    List,
    GetByName(String),
    Update(String),
    Delete(String),
    NotFound,
}

pub(super) fn resolve_resource_route(method: &str, path: &str) -> ResourceRoute {
    let Some(suffix) = path.strip_prefix("/api/v1/resources") else {
        return ResourceRoute::NotFound;
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => ResourceRoute::List,
        ("GET", Some(n)) => ResourceRoute::GetByName(n.to_owned()),
        ("PUT", Some(n)) => ResourceRoute::Update(n.to_owned()),
        ("DELETE", Some(n)) => ResourceRoute::Delete(n.to_owned()),
        _ => ResourceRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum PromptRoute {
    List,
    GetByName(String),
    Delete(String),
    NotFound,
}

pub(super) fn resolve_prompt_route(method: &str, path: &str) -> PromptRoute {
    let Some(suffix) = path.strip_prefix("/api/v1/prompts") else {
        return PromptRoute::NotFound;
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => PromptRoute::List,
        ("GET", Some(n)) => PromptRoute::GetByName(n.to_owned()),
        ("DELETE", Some(n)) => PromptRoute::Delete(n.to_owned()),
        _ => PromptRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum ManagementRoute {
    Info,
    Statistics,
    NotFound,
}

pub(super) fn resolve_management_route(method: &str, path: &str) -> ManagementRoute {
    let Some(suffix) = path.strip_prefix("/api/v1/management") else {
        return ManagementRoute::NotFound;
    };

    match (method, suffix) {
        ("GET", "/info") => ManagementRoute::Info,
        ("GET", "/statistics") => ManagementRoute::Statistics,
        _ => ManagementRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum ForwardRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    Refresh(String),
    NotFound,
}

pub(super) fn resolve_forward_route(method: &str, path: &str) -> ForwardRoute {
    let Some(suffix) = path.strip_prefix("/api/v1/forwards") else {
        return ForwardRoute::NotFound;
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => ForwardRoute::List,
        ("GET", Some(n)) => ForwardRoute::GetByName(n.to_owned()),
        ("POST", None) => ForwardRoute::Create,
        ("DELETE", Some(n)) if !n.contains('/') => ForwardRoute::Delete(n.to_owned()),
        ("POST", Some(n)) => {
            if let Some(name) = n.strip_suffix("/refreshes") {
                if name.contains('/') {
                    ForwardRoute::NotFound
                } else {
                    ForwardRoute::Refresh(name.to_owned())
                }
            } else {
                ForwardRoute::NotFound
            }
        }
        _ => ForwardRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum NamespaceRoute {
    List,
    GetByName(String),
    Create,
    Update(String),
    Delete(String),
    NotFound,
}

pub(super) fn resolve_namespace_route(method: &str, path: &str) -> NamespaceRoute {
    let Some(suffix) = path.strip_prefix("/api/v1/namespaces") else {
        return NamespaceRoute::NotFound;
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => NamespaceRoute::List,
        ("GET", Some(n)) => NamespaceRoute::GetByName(n.to_owned()),
        ("POST", None) => NamespaceRoute::Create,
        ("PUT", Some(n)) => NamespaceRoute::Update(n.to_owned()),
        ("DELETE", Some(n)) => NamespaceRoute::Delete(n.to_owned()),
        _ => NamespaceRoute::NotFound,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn route_list() {
        assert_eq!(resolve_tool_route("GET", "/api/v1/tools"), ToolRoute::List);
    }

    #[test]
    fn route_get_by_name() {
        assert_eq!(
            resolve_tool_route("GET", "/api/v1/tools/my-tool"),
            ToolRoute::GetByName("my-tool".to_owned())
        );
    }

    #[test]
    fn route_update() {
        assert_eq!(
            resolve_tool_route("PUT", "/api/v1/tools/my-tool"),
            ToolRoute::Update("my-tool".to_owned())
        );
    }

    #[test]
    fn route_delete() {
        assert_eq!(
            resolve_tool_route("DELETE", "/api/v1/tools/my-tool"),
            ToolRoute::Delete("my-tool".to_owned())
        );
    }

    #[test]
    fn route_unknown_path() {
        assert_eq!(resolve_tool_route("GET", "/api/v1/other"), ToolRoute::NotFound);
    }

    #[test]
    fn route_delete_without_name() {
        assert_eq!(resolve_tool_route("DELETE", "/api/v1/tools"), ToolRoute::NotFound);
    }

    #[test]
    fn resource_route_list() {
        assert_eq!(resolve_resource_route("GET", "/api/v1/resources"), ResourceRoute::List);
    }

    #[test]
    fn resource_route_get_by_name() {
        assert_eq!(
            resolve_resource_route("GET", "/api/v1/resources/my-res"),
            ResourceRoute::GetByName("my-res".to_owned())
        );
    }

    #[test]
    fn resource_route_update() {
        assert_eq!(
            resolve_resource_route("PUT", "/api/v1/resources/my-res"),
            ResourceRoute::Update("my-res".to_owned())
        );
    }

    #[test]
    fn resource_route_delete() {
        assert_eq!(
            resolve_resource_route("DELETE", "/api/v1/resources/my-res"),
            ResourceRoute::Delete("my-res".to_owned())
        );
    }

    #[test]
    fn forward_route_refresh() {
        assert_eq!(
            resolve_forward_route("POST", "/api/v1/forwards/my-fwd/refreshes"),
            ForwardRoute::Refresh("my-fwd".to_owned())
        );
    }

    #[test]
    fn forward_route_refresh_rejects_slashes_in_name() {
        assert_eq!(
            resolve_forward_route("POST", "/api/v1/forwards/a/b/refreshes"),
            ForwardRoute::NotFound
        );
    }

    #[test]
    fn forward_route_delete() {
        assert_eq!(
            resolve_forward_route("DELETE", "/api/v1/forwards/my-fwd"),
            ForwardRoute::Delete("my-fwd".to_owned())
        );
    }

    #[test]
    fn forward_route_delete_rejects_slashes() {
        assert_eq!(
            resolve_forward_route("DELETE", "/api/v1/forwards/a/b"),
            ForwardRoute::NotFound
        );
    }

    #[test]
    fn management_route_info() {
        assert_eq!(
            resolve_management_route("GET", "/api/v1/management/info"),
            ManagementRoute::Info
        );
    }

    #[test]
    fn management_route_info_rejects_post() {
        assert_eq!(
            resolve_management_route("POST", "/api/v1/management/info"),
            ManagementRoute::NotFound
        );
    }
}
