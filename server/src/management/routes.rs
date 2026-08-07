#[derive(Debug, PartialEq, Eq)]
pub(super) enum ToolRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}

pub(super) fn resolve_tool_route(method: &str, path: &str) -> ToolRoute {
    let suffix = match path.strip_prefix("/api/v1/tools") {
        Some(s) => s,
        None => return ToolRoute::NotFound,
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => ToolRoute::List,
        ("GET", Some(n)) => ToolRoute::GetByName(n.to_owned()),
        ("POST", None | Some("payloads")) => ToolRoute::Create,
        ("DELETE", Some(n)) => ToolRoute::Delete(n.to_owned()),
        _ => ToolRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum ResourceRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}

pub(super) fn resolve_resource_route(method: &str, path: &str) -> ResourceRoute {
    let suffix = match path.strip_prefix("/api/v1/resources") {
        Some(s) => s,
        None => return ResourceRoute::NotFound,
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => ResourceRoute::List,
        ("GET", Some(n)) => ResourceRoute::GetByName(n.to_owned()),
        ("POST", None | Some("payloads")) => ResourceRoute::Create,
        ("DELETE", Some(n)) => ResourceRoute::Delete(n.to_owned()),
        _ => ResourceRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum PromptRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}

pub(super) fn resolve_prompt_route(method: &str, path: &str) -> PromptRoute {
    let suffix = match path.strip_prefix("/api/v1/prompts") {
        Some(s) => s,
        None => return PromptRoute::NotFound,
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => PromptRoute::List,
        ("GET", Some(n)) => PromptRoute::GetByName(n.to_owned()),
        ("POST", None | Some("payloads")) => PromptRoute::Create,
        ("DELETE", Some(n)) => PromptRoute::Delete(n.to_owned()),
        _ => PromptRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum ManagementRoute {
    Statistics,
    NotFound,
}

pub(super) fn resolve_management_route(method: &str, path: &str) -> ManagementRoute {
    let suffix = match path.strip_prefix("/api/v1/management") {
        Some(s) => s,
        None => return ManagementRoute::NotFound,
    };

    match (method, suffix) {
        ("GET", "/statistics") => ManagementRoute::Statistics,
        _ => ManagementRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum CapabilityRoute {
    List,
    ToolsState,
    ResourcesState,
    FleetStatus,
    NotFound,
}

pub(super) fn resolve_capability_route(method: &str, path: &str) -> CapabilityRoute {
    let suffix = match path.strip_prefix("/api/v1/capabilities") {
        Some(s) => s,
        None => return CapabilityRoute::NotFound,
    };

    if method != "GET" {
        return CapabilityRoute::NotFound;
    }

    match suffix {
        "" | "/" => CapabilityRoute::List,
        "/tools/state" => CapabilityRoute::ToolsState,
        "/resources/state" => CapabilityRoute::ResourcesState,
        "/fleet/status" => CapabilityRoute::FleetStatus,
        _ => CapabilityRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum ServiceRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}

pub(super) fn resolve_service_route(method: &str, path: &str) -> ServiceRoute {
    let suffix = match path.strip_prefix("/api/v1/services") {
        Some(s) => s,
        None => return ServiceRoute::NotFound,
    };

    let name = suffix
        .strip_prefix('/')
        .filter(|s| !s.is_empty());

    match (method, name) {
        ("GET", None) => ServiceRoute::List,
        ("GET", Some(n)) => ServiceRoute::GetByName(n.to_owned()),
        ("POST", None | Some("payloads")) => ServiceRoute::Create,
        ("DELETE", Some(n)) => ServiceRoute::Delete(n.to_owned()),
        _ => ServiceRoute::NotFound,
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
    let suffix = match path.strip_prefix("/api/v1/forwards") {
        Some(s) => s,
        None => return ForwardRoute::NotFound,
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
                if !name.contains('/') {
                    ForwardRoute::Refresh(name.to_owned())
                } else {
                    ForwardRoute::NotFound
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
    let suffix = match path.strip_prefix("/api/v1/namespaces") {
        Some(s) => s,
        None => return NamespaceRoute::NotFound,
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
    fn route_create() {
        assert_eq!(resolve_tool_route("POST", "/api/v1/tools"), ToolRoute::Create);
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
    fn resource_route_create() {
        assert_eq!(resolve_resource_route("POST", "/api/v1/resources"), ResourceRoute::Create);
    }

    #[test]
    fn resource_route_create_payloads() {
        assert_eq!(resolve_resource_route("POST", "/api/v1/resources/payloads"), ResourceRoute::Create);
    }

    #[test]
    fn resource_route_delete() {
        assert_eq!(
            resolve_resource_route("DELETE", "/api/v1/resources/my-res"),
            ResourceRoute::Delete("my-res".to_owned())
        );
    }
}
