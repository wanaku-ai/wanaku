/// Matches `path` against a collection path on a segment boundary.
///
/// Returns `Some(None)` for the collection itself (with or without a trailing
/// slash) and `Some(Some(child))` for `collection/child`. Returns `None` when
/// `path` is outside the collection, including sibling paths that share the
/// prefix (for example `/api/v1/tools-export`).
fn collection_child<'a>(path: &'a str, collection: &str) -> Option<Option<&'a str>> {
    match path.strip_prefix(collection)? {
        "" | "/" => Some(None),
        rest => rest.strip_prefix('/').map(Some),
    }
}

/// Resolves `{name}/enable` and `{name}/disable` to the name and the new state.
fn resolve_set_enabled(path: &str) -> Option<(String, bool)> {
    let (name, enabled) = match path.strip_suffix("/enable") {
        Some(name) => (name, true),
        None => (path.strip_suffix("/disable")?, false),
    };
    (!name.is_empty()).then(|| (name.to_owned(), enabled))
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum ToolRoute {
    List,
    GetByName(String),
    Delete(String),
    SetEnabled(String, bool),
    NotFound,
}

pub(super) fn resolve_tool_route(method: &str, path: &str) -> ToolRoute {
    let Some(name) = collection_child(path, "/api/v1/tools") else {
        return ToolRoute::NotFound;
    };

    match (method, name) {
        ("GET", None) => ToolRoute::List,
        ("GET", Some(n)) => ToolRoute::GetByName(n.to_owned()),
        ("DELETE", Some(n)) => ToolRoute::Delete(n.to_owned()),
        ("PUT", Some(n)) => match resolve_set_enabled(n) {
            Some((name, enabled)) => ToolRoute::SetEnabled(name, enabled),
            None => ToolRoute::NotFound,
        },
        _ => ToolRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum ResourceRoute {
    List,
    GetByName(String),
    Delete(String),
    SetEnabled(String, bool),
    NotFound,
}

pub(super) fn resolve_resource_route(method: &str, path: &str) -> ResourceRoute {
    let Some(name) = collection_child(path, "/api/v1/resources") else {
        return ResourceRoute::NotFound;
    };

    match (method, name) {
        ("GET", None) => ResourceRoute::List,
        ("GET", Some(n)) => ResourceRoute::GetByName(n.to_owned()),
        ("DELETE", Some(n)) => ResourceRoute::Delete(n.to_owned()),
        ("PUT", Some(n)) => match resolve_set_enabled(n) {
            Some((name, enabled)) => ResourceRoute::SetEnabled(name, enabled),
            None => ResourceRoute::NotFound,
        },
        _ => ResourceRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum PromptRoute {
    List,
    GetByName(String),
    Delete(String),
    SetEnabled(String, bool),
    NotFound,
}

pub(super) fn resolve_prompt_route(method: &str, path: &str) -> PromptRoute {
    let Some(name) = collection_child(path, "/api/v1/prompts") else {
        return PromptRoute::NotFound;
    };

    match (method, name) {
        ("GET", None) => PromptRoute::List,
        ("GET", Some(n)) => PromptRoute::GetByName(n.to_owned()),
        ("DELETE", Some(n)) => PromptRoute::Delete(n.to_owned()),
        ("PUT", Some(n)) => match resolve_set_enabled(n) {
            Some((name, enabled)) => PromptRoute::SetEnabled(name, enabled),
            None => PromptRoute::NotFound,
        },
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
    let Some(name) = collection_child(path, "/api/v1/forwards") else {
        return ForwardRoute::NotFound;
    };

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
    let Some(name) = collection_child(path, "/api/v1/namespaces") else {
        return NamespaceRoute::NotFound;
    };

    match (method, name) {
        ("GET", None) => NamespaceRoute::List,
        ("GET", Some(n)) => NamespaceRoute::GetByName(n.to_owned()),
        ("POST", None) => NamespaceRoute::Create,
        ("PUT", Some(n)) => NamespaceRoute::Update(n.to_owned()),
        ("DELETE", Some(n)) => NamespaceRoute::Delete(n.to_owned()),
        _ => NamespaceRoute::NotFound,
    }
}

#[derive(Debug, PartialEq, Eq)]
pub(super) enum BindingRoute {
    List,
    GetById(String),
    NotFound,
}

pub(super) fn resolve_binding_route(method: &str, path: &str) -> BindingRoute {
    let Some(id) = collection_child(path, "/api/v1/bindings") else {
        return BindingRoute::NotFound;
    };

    match (method, id) {
        ("GET", None) => BindingRoute::List,
        ("GET", Some(i)) => BindingRoute::GetById(i.to_owned()),
        _ => BindingRoute::NotFound,
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
    fn route_update_not_supported() {
        assert_eq!(
            resolve_tool_route("PUT", "/api/v1/tools/my-tool"),
            ToolRoute::NotFound
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
        assert_eq!(
            resolve_tool_route("GET", "/api/v1/other"),
            ToolRoute::NotFound
        );
    }

    #[test]
    fn route_enable_and_disable() {
        assert_eq!(
            resolve_tool_route("PUT", "/api/v1/tools/my-tool/enable"),
            ToolRoute::SetEnabled("my-tool".to_owned(), true)
        );
        assert_eq!(
            resolve_tool_route("PUT", "/api/v1/tools/my-tool/disable"),
            ToolRoute::SetEnabled("my-tool".to_owned(), false)
        );
        assert_eq!(
            resolve_tool_route("PUT", "/api/v1/tools/enable"),
            ToolRoute::NotFound
        );
        assert_eq!(
            resolve_tool_route("POST", "/api/v1/tools/my-tool/enable"),
            ToolRoute::NotFound
        );
        assert_eq!(
            resolve_resource_route("PUT", "/api/v1/resources/my-res/disable"),
            ResourceRoute::SetEnabled("my-res".to_owned(), false)
        );
        assert_eq!(
            resolve_prompt_route("PUT", "/api/v1/prompts/my-prompt/enable"),
            PromptRoute::SetEnabled("my-prompt".to_owned(), true)
        );
    }

    #[test]
    fn route_delete_without_name() {
        assert_eq!(
            resolve_tool_route("DELETE", "/api/v1/tools"),
            ToolRoute::NotFound
        );
    }

    #[test]
    fn resource_route_list() {
        assert_eq!(
            resolve_resource_route("GET", "/api/v1/resources"),
            ResourceRoute::List
        );
    }

    #[test]
    fn resource_route_get_by_name() {
        assert_eq!(
            resolve_resource_route("GET", "/api/v1/resources/my-res"),
            ResourceRoute::GetByName("my-res".to_owned())
        );
    }

    #[test]
    fn resource_route_update_not_supported() {
        assert_eq!(
            resolve_resource_route("PUT", "/api/v1/resources/my-res"),
            ResourceRoute::NotFound
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
    fn binding_route_list() {
        assert_eq!(
            resolve_binding_route("GET", "/api/v1/bindings"),
            BindingRoute::List
        );
    }

    #[test]
    fn binding_route_get_by_id() {
        assert_eq!(
            resolve_binding_route("GET", "/api/v1/bindings/my-binding"),
            BindingRoute::GetById("my-binding".to_owned())
        );
    }

    #[test]
    fn binding_route_rejects_mutations() {
        assert_eq!(
            resolve_binding_route("POST", "/api/v1/bindings"),
            BindingRoute::NotFound
        );
        assert_eq!(
            resolve_binding_route("DELETE", "/api/v1/bindings/my-binding"),
            BindingRoute::NotFound
        );
    }

    #[test]
    fn collection_child_enforces_segment_boundary() {
        let cases = [
            ("/api/v1/tools", Some(None)),
            ("/api/v1/tools/", Some(None)),
            ("/api/v1/tools/a", Some(Some("a"))),
            ("/api/v1/tools/a/enable", Some(Some("a/enable"))),
            ("/api/v1/tools-export", None),
            ("/api/v1/toolsx/a", None),
            ("/api/v1/tool", None),
            ("/other", None),
        ];
        for (path, expected) in cases {
            assert_eq!(collection_child(path, "/api/v1/tools"), expected, "{path}");
        }
    }

    fn assert_routes<R: PartialEq + std::fmt::Debug>(
        resolve: fn(&str, &str) -> R,
        cases: impl IntoIterator<Item = (&'static str, &'static str, R)>,
    ) {
        for (method, path, expected) in cases {
            assert_eq!(resolve(method, path), expected, "{method} {path}");
        }
    }

    #[test]
    fn tool_route_table() {
        use ToolRoute as R;
        let cases = [
            ("GET", "/api/v1/tools", R::List),
            ("GET", "/api/v1/tools/", R::List),
            ("GET", "/api/v1/tools/t", R::GetByName("t".into())),
            ("DELETE", "/api/v1/tools/t", R::Delete("t".into())),
            ("DELETE", "/api/v1/tools/", R::NotFound),
            (
                "PUT",
                "/api/v1/tools/t/enable",
                R::SetEnabled("t".into(), true),
            ),
            (
                "PUT",
                "/api/v1/tools/t/disable",
                R::SetEnabled("t".into(), false),
            ),
            ("PUT", "/api/v1/tools//enable", R::NotFound),
            ("GET", "/api/v1/tools-export", R::NotFound),
            ("GET", "/api/v1/toolsx/t", R::NotFound),
            ("PUT", "/api/v1/tools-x/t/enable", R::NotFound),
            ("DELETE", "/api/v1/tools-x/t", R::NotFound),
        ];
        assert_routes(resolve_tool_route, cases);
    }

    #[test]
    fn resource_route_table() {
        use ResourceRoute as R;
        let cases = [
            ("GET", "/api/v1/resources", R::List),
            ("GET", "/api/v1/resources/", R::List),
            ("GET", "/api/v1/resources/r", R::GetByName("r".into())),
            ("DELETE", "/api/v1/resources/r", R::Delete("r".into())),
            ("DELETE", "/api/v1/resources/", R::NotFound),
            (
                "PUT",
                "/api/v1/resources/r/enable",
                R::SetEnabled("r".into(), true),
            ),
            (
                "PUT",
                "/api/v1/resources/r/disable",
                R::SetEnabled("r".into(), false),
            ),
            ("GET", "/api/v1/resources-export", R::NotFound),
            ("DELETE", "/api/v1/resourcesx/r", R::NotFound),
        ];
        assert_routes(resolve_resource_route, cases);
    }

    #[test]
    fn prompt_route_table() {
        use PromptRoute as R;
        let cases = [
            ("GET", "/api/v1/prompts", R::List),
            ("GET", "/api/v1/prompts/", R::List),
            ("GET", "/api/v1/prompts/p", R::GetByName("p".into())),
            ("DELETE", "/api/v1/prompts/p", R::Delete("p".into())),
            ("DELETE", "/api/v1/prompts/", R::NotFound),
            (
                "PUT",
                "/api/v1/prompts/p/enable",
                R::SetEnabled("p".into(), true),
            ),
            (
                "PUT",
                "/api/v1/prompts/p/disable",
                R::SetEnabled("p".into(), false),
            ),
            ("GET", "/api/v1/prompts-export", R::NotFound),
            ("PUT", "/api/v1/promptsx/p/enable", R::NotFound),
        ];
        assert_routes(resolve_prompt_route, cases);
    }

    #[test]
    fn forward_route_table() {
        use ForwardRoute as R;
        let cases = [
            ("GET", "/api/v1/forwards", R::List),
            ("GET", "/api/v1/forwards/", R::List),
            ("POST", "/api/v1/forwards", R::Create),
            ("POST", "/api/v1/forwards/", R::Create),
            ("GET", "/api/v1/forwards/f", R::GetByName("f".into())),
            ("DELETE", "/api/v1/forwards/f", R::Delete("f".into())),
            ("DELETE", "/api/v1/forwards/", R::NotFound),
            (
                "POST",
                "/api/v1/forwards/f/refreshes",
                R::Refresh("f".into()),
            ),
            ("POST", "/api/v1/forwards/f", R::NotFound),
            ("POST", "/api/v1/forwards-extra", R::NotFound),
            ("GET", "/api/v1/forwards-extra", R::NotFound),
            ("POST", "/api/v1/forwardsx/f/refreshes", R::NotFound),
            ("DELETE", "/api/v1/forwardsx/f", R::NotFound),
        ];
        assert_routes(resolve_forward_route, cases);
    }

    #[test]
    fn namespace_route_table() {
        use NamespaceRoute as R;
        let cases = [
            ("GET", "/api/v1/namespaces", R::List),
            ("GET", "/api/v1/namespaces/", R::List),
            ("POST", "/api/v1/namespaces", R::Create),
            ("POST", "/api/v1/namespaces/", R::Create),
            ("GET", "/api/v1/namespaces/n", R::GetByName("n".into())),
            ("PUT", "/api/v1/namespaces/n", R::Update("n".into())),
            ("DELETE", "/api/v1/namespaces/n", R::Delete("n".into())),
            ("PUT", "/api/v1/namespaces/", R::NotFound),
            ("DELETE", "/api/v1/namespaces/", R::NotFound),
            ("POST", "/api/v1/namespaces-extra", R::NotFound),
            ("GET", "/api/v1/namespaces-extra", R::NotFound),
            ("PUT", "/api/v1/namespacesx/n", R::NotFound),
            ("DELETE", "/api/v1/namespacesx/n", R::NotFound),
        ];
        assert_routes(resolve_namespace_route, cases);
    }

    #[test]
    fn binding_route_table() {
        use BindingRoute as R;
        let cases = [
            ("GET", "/api/v1/bindings", R::List),
            ("GET", "/api/v1/bindings/", R::List),
            ("GET", "/api/v1/bindings/b", R::GetById("b".into())),
            ("GET", "/api/v1/bindings-extra", R::NotFound),
            ("GET", "/api/v1/bindingsx/b", R::NotFound),
        ];
        assert_routes(resolve_binding_route, cases);
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
