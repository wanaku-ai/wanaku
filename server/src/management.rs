use async_trait::async_trait;
use http::Response;
use pingora_core::apps::http_app::ServeHttp;
use pingora_core::protocols::http::ServerSession;
use tracing::{info, warn};

use wanaku_praxis_apis::registry::{InMemoryRegistry, ToolEntry, ToolRegistry};

const MAX_BODY_BYTES: usize = 1_048_576;

pub struct WanakuManagementService {
    registry: InMemoryRegistry,
}

impl WanakuManagementService {
    pub fn new(registry: InMemoryRegistry) -> Self {
        Self { registry }
    }
}

#[derive(Debug, PartialEq, Eq)]
enum ToolRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}

fn resolve_tool_route(method: &str, path: &str) -> ToolRoute {
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

#[async_trait]
impl ServeHttp for WanakuManagementService {
    async fn response(&self, http_session: &mut ServerSession) -> Response<Vec<u8>> {
        let path = http_session.req_header().uri.path().to_owned();
        let method = http_session.req_header().method.as_str().to_owned();

        tracing::debug!(%method, %path, "management API request");

        match resolve_tool_route(&method, &path) {
            ToolRoute::List => handle_list(&self.registry),
            ToolRoute::GetByName(name) => handle_get(&self.registry, &name),
            ToolRoute::Create => match read_body(http_session).await {
                Ok(body) => handle_create(&self.registry, &body),
                Err(resp) => resp,
            },
            ToolRoute::Delete(name) => handle_delete(&self.registry, &name),
            ToolRoute::NotFound => json_err(404, "not found"),
        }
    }
}

fn handle_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let tools = registry.list_tools();
    json_ok(&serde_json::json!(tools))
}

fn handle_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_tool(name) {
        Some(tool) => json_ok(&serde_json::json!(tool)),
        None => json_err(404, &format!("tool not found: {name}")),
    }
}

fn handle_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, "tool create request body");
    let tool: ToolEntry = match serde_json::from_str(body) {
        Ok(t) => t,
        Err(e) => {
            warn!(error = %e, "invalid tool JSON");
            return json_err(400, &format!("invalid tool JSON: {e}"));
        }
    };

    info!(tool = %tool.name, "registered tool via management API");
    let response = serde_json::json!(&tool);
    registry.register_tool(tool);
    json_ok(&response)
}

fn handle_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_tool(name) {
        info!(tool = %name, "removed tool via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("tool not found: {name}"))
    }
}

#[expect(clippy::expect_used, reason = "valid static response")]
fn json_ok(data: &serde_json::Value) -> Response<Vec<u8>> {
    let wrapper = serde_json::json!({
        "data": data,
        "error": null,
    });
    let body = serde_json::to_vec(&wrapper).unwrap_or_default();
    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid json response")
}

#[expect(clippy::expect_used, reason = "valid static response")]
fn json_err(status: u16, message: &str) -> Response<Vec<u8>> {
    let wrapper = serde_json::json!({
        "data": null,
        "error": message,
    });
    let body = serde_json::to_vec(&wrapper).unwrap_or_default();
    Response::builder()
        .status(status)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid json error response")
}

async fn read_body(session: &mut ServerSession) -> Result<String, Response<Vec<u8>>> {
    let mut buf = Vec::new();
    loop {
        match session.read_request_body().await {
            Ok(Some(chunk)) => {
                if buf.len() + chunk.len() > MAX_BODY_BYTES {
                    warn!(limit = MAX_BODY_BYTES, "management request body exceeded size limit");
                    return Err(json_err(413, "request body too large"));
                }
                buf.extend_from_slice(&chunk);
            }
            Ok(None) => break,
            Err(e) => {
                warn!(error = %e, "management request body read failed");
                return Err(json_err(502, "request body read failed"));
            }
        }
    }
    String::from_utf8(buf).map_err(|_| json_err(400, "request body is not valid UTF-8"))
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
}
