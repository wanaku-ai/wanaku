use async_trait::async_trait;
use http::Response;
use pingora_core::apps::http_app::ServeHttp;
use pingora_core::protocols::http::ServerSession;
use tracing::{info, warn};

use wanaku_praxis_apis::registry::{
    InMemoryRegistry, PromptEntry, PromptRegistry, ResourceEntry, ResourceRegistry, ToolEntry,
    ToolRegistry,
};

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

#[derive(Debug, PartialEq, Eq)]
enum ResourceRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}

fn resolve_resource_route(method: &str, path: &str) -> ResourceRoute {
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
enum PromptRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}

fn resolve_prompt_route(method: &str, path: &str) -> PromptRoute {
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

#[async_trait]
impl ServeHttp for WanakuManagementService {
    async fn response(&self, http_session: &mut ServerSession) -> Response<Vec<u8>> {
        let path = http_session.req_header().uri.path().to_owned();
        let method = http_session.req_header().method.as_str().to_owned();

        tracing::debug!(%method, %path, "management API request");

        let tool_route = resolve_tool_route(&method, &path);
        if tool_route != ToolRoute::NotFound {
            return match tool_route {
                ToolRoute::List => handle_tool_list(&self.registry),
                ToolRoute::GetByName(name) => handle_tool_get(&self.registry, &name),
                ToolRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_tool_create(&self.registry, &body),
                    Err(resp) => resp,
                },
                ToolRoute::Delete(name) => handle_tool_delete(&self.registry, &name),
                ToolRoute::NotFound => json_err(404, "not found"),
            };
        }

        let resource_route = resolve_resource_route(&method, &path);
        if resource_route != ResourceRoute::NotFound {
            return match resource_route {
                ResourceRoute::List => handle_resource_list(&self.registry),
                ResourceRoute::GetByName(name) => handle_resource_get(&self.registry, &name),
                ResourceRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_resource_create(&self.registry, &body),
                    Err(resp) => resp,
                },
                ResourceRoute::Delete(name) => handle_resource_delete(&self.registry, &name),
                ResourceRoute::NotFound => json_err(404, "not found"),
            };
        }

        match resolve_prompt_route(&method, &path) {
            PromptRoute::List => handle_prompt_list(&self.registry),
            PromptRoute::GetByName(name) => handle_prompt_get(&self.registry, &name),
            PromptRoute::Create => match read_body(http_session).await {
                Ok(body) => handle_prompt_create(&self.registry, &body),
                Err(resp) => resp,
            },
            PromptRoute::Delete(name) => handle_prompt_delete(&self.registry, &name),
            PromptRoute::NotFound => json_err(404, "not found"),
        }
    }
}

fn handle_tool_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let tools = registry.list_tools();
    json_ok(&serde_json::json!(tools))
}

fn handle_tool_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_tool(name) {
        Some(tool) => json_ok(&serde_json::json!(tool)),
        None => json_err(404, &format!("tool not found: {name}")),
    }
}

fn handle_tool_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
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

fn handle_tool_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_tool(name) {
        info!(tool = %name, "removed tool via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("tool not found: {name}"))
    }
}

fn handle_resource_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let resources = registry.list_resources();
    json_ok(&serde_json::json!(resources))
}

fn handle_resource_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_resource(name) {
        Some(resource) => json_ok(&serde_json::json!(resource)),
        None => json_err(404, &format!("resource not found: {name}")),
    }
}

fn handle_resource_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, "resource create request body");
    let resource: ResourceEntry = match serde_json::from_str(body) {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "invalid resource JSON");
            return json_err(400, &format!("invalid resource JSON: {e}"));
        }
    };

    info!(resource = %resource.name, "registered resource via management API");
    let response = serde_json::json!(&resource);
    registry.register_resource(resource);
    json_ok(&response)
}

fn handle_resource_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_resource(name) {
        info!(resource = %name, "removed resource via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("resource not found: {name}"))
    }
}

fn handle_prompt_list(registry: &InMemoryRegistry) -> Response<Vec<u8>> {
    let prompts = registry.list_prompts();
    json_ok(&serde_json::json!(prompts))
}

fn handle_prompt_get(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    match registry.get_prompt(name) {
        Some(prompt) => json_ok(&serde_json::json!(prompt)),
        None => json_err(404, &format!("prompt not found: {name}")),
    }
}

fn handle_prompt_create(registry: &InMemoryRegistry, body: &str) -> Response<Vec<u8>> {
    tracing::debug!(body = %body, "prompt create request body");
    let prompt: PromptEntry = match serde_json::from_str(body) {
        Ok(p) => p,
        Err(e) => {
            warn!(error = %e, "invalid prompt JSON");
            return json_err(400, &format!("invalid prompt JSON: {e}"));
        }
    };

    info!(prompt = %prompt.name, "registered prompt via management API");
    let response = serde_json::json!(&prompt);
    registry.register_prompt(prompt);
    json_ok(&response)
}

fn handle_prompt_delete(registry: &InMemoryRegistry, name: &str) -> Response<Vec<u8>> {
    if registry.remove_prompt(name) {
        info!(prompt = %name, "removed prompt via management API");
        json_ok(&serde_json::json!({"removed": name}))
    } else {
        json_err(404, &format!("prompt not found: {name}"))
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
