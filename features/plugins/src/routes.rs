use http::Response;

use crate::handlers;
use crate::manifest::PluginManifest;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum PluginRoute {
    ListPlugins,
    ServeFile(String, String),
    ProxyService(String, String, String),
    NotFound,
}

fn is_valid_segment(s: &str) -> bool {
    !s.is_empty() && s != "." && s != ".." && !s.contains('/') && !s.contains('\\')
}

pub(crate) fn resolve_plugin_route(method: &str, path: &str) -> PluginRoute {
    if let Some(suffix) = path.strip_prefix("/api/v1/plugins") {
        return match (method, suffix) {
            ("GET", "" | "/") => PluginRoute::ListPlugins,
            _ => PluginRoute::NotFound,
        };
    }

    if method == "GET"
        && let Some(rest) = path.strip_prefix("/plugins/")
    {
        let (id, file_path) = match rest.split_once('/') {
            Some((id, p)) => (id, p),
            None => (rest, ""),
        };
        if !is_valid_segment(id) {
            return PluginRoute::NotFound;
        }
        return PluginRoute::ServeFile(id.to_owned(), file_path.to_owned());
    }

    if let Some(rest) = path.strip_prefix("/api/plugins/") {
        let (id, remainder) = match rest.split_once('/') {
            Some((id, r)) => (id, r),
            None => return PluginRoute::NotFound,
        };
        if !is_valid_segment(id) {
            return PluginRoute::NotFound;
        }
        let (service, proxy_path) = match remainder.split_once('/') {
            Some((svc, p)) => (svc, format!("/{p}")),
            None => (remainder, String::new()),
        };
        if !is_valid_segment(service) {
            return PluginRoute::NotFound;
        }
        return PluginRoute::ProxyService(id.to_owned(), service.to_owned(), proxy_path);
    }

    PluginRoute::NotFound
}

pub(crate) fn handle_list(manifests: &[PluginManifest]) -> Response<Vec<u8>> {
    handlers::handle_list_plugins(manifests)
}

pub(crate) fn handle_file(
    plugins_path: &std::path::Path,
    plugin_id: &str,
    file_path: &str,
) -> Response<Vec<u8>> {
    handlers::handle_serve_file(plugins_path, plugin_id, file_path)
}

pub(crate) async fn handle_proxy(
    client: &reqwest::Client,
    target_url: &str,
    path: &str,
    query: Option<&str>,
    method: &str,
    body: Option<&str>,
) -> Response<Vec<u8>> {
    handlers::handle_proxy_service(client, target_url, path, query, method, body).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn list_plugins() {
        assert_eq!(
            resolve_plugin_route("GET", "/api/v1/plugins"),
            PluginRoute::ListPlugins
        );
    }

    #[test]
    fn list_plugins_trailing_slash() {
        assert_eq!(
            resolve_plugin_route("GET", "/api/v1/plugins/"),
            PluginRoute::ListPlugins
        );
    }

    #[test]
    fn list_plugins_wrong_method() {
        assert_eq!(
            resolve_plugin_route("POST", "/api/v1/plugins"),
            PluginRoute::NotFound
        );
    }

    #[test]
    fn serve_file_with_path() {
        assert_eq!(
            resolve_plugin_route("GET", "/plugins/my-plugin/assets/style.css"),
            PluginRoute::ServeFile("my-plugin".to_owned(), "assets/style.css".to_owned())
        );
    }

    #[test]
    fn serve_file_root() {
        assert_eq!(
            resolve_plugin_route("GET", "/plugins/my-plugin"),
            PluginRoute::ServeFile("my-plugin".to_owned(), String::new())
        );
    }

    #[test]
    fn serve_file_empty_id() {
        assert_eq!(
            resolve_plugin_route("GET", "/plugins/"),
            PluginRoute::NotFound
        );
    }

    #[test]
    fn proxy_service_with_path() {
        assert_eq!(
            resolve_plugin_route("POST", "/api/plugins/my-plugin/chat/messages/list"),
            PluginRoute::ProxyService(
                "my-plugin".to_owned(),
                "chat".to_owned(),
                "/messages/list".to_owned()
            )
        );
    }

    #[test]
    fn proxy_service_root() {
        assert_eq!(
            resolve_plugin_route("GET", "/api/plugins/my-plugin/chat"),
            PluginRoute::ProxyService(
                "my-plugin".to_owned(),
                "chat".to_owned(),
                String::new()
            )
        );
    }

    #[test]
    fn proxy_service_missing_service() {
        assert_eq!(
            resolve_plugin_route("GET", "/api/plugins/my-plugin"),
            PluginRoute::NotFound
        );
    }

    #[test]
    fn unrelated_path() {
        assert_eq!(
            resolve_plugin_route("GET", "/api/v1/tools"),
            PluginRoute::NotFound
        );
    }

    #[test]
    fn serve_file_rejects_dotdot_traversal() {
        assert_eq!(
            resolve_plugin_route("GET", "/plugins/../etc/passwd"),
            PluginRoute::NotFound
        );
    }

    #[test]
    fn serve_file_rejects_single_dot() {
        assert_eq!(
            resolve_plugin_route("GET", "/plugins/./file.js"),
            PluginRoute::NotFound
        );
    }

    #[test]
    fn proxy_rejects_dotdot_plugin_id() {
        assert_eq!(
            resolve_plugin_route("POST", "/api/plugins/../svc/path"),
            PluginRoute::NotFound
        );
    }

    #[test]
    fn proxy_rejects_dotdot_service_id() {
        assert_eq!(
            resolve_plugin_route("POST", "/api/plugins/ok/../path"),
            PluginRoute::NotFound
        );
    }
}
