mod handlers;
mod response;
mod routes;
#[cfg(feature = "ui")]
mod ui;

pub use handlers::discover_and_update_forward;
pub use handlers::discover_tools_from_forward;
pub use handlers::discover_resources_from_forward;
pub use handlers::discover_prompts_from_forward;

use async_trait::async_trait;
use http::{Response, StatusCode};
use pingora_core::apps::http_app::ServeHttp;
use pingora_core::protocols::http::ServerSession;

use wanaku_apis::feature::{Feature, HttpContext};
use wanaku_apis::registry::InMemoryRegistry;

use self::handlers::{
    handle_forward_create, handle_forward_delete, handle_forward_get, handle_forward_list,
    handle_forward_refresh,
    handle_namespace_create, handle_namespace_delete, handle_namespace_get, handle_namespace_list,
    handle_namespace_update,
    handle_prompt_delete, handle_prompt_get, handle_prompt_list,
    handle_resource_delete, handle_resource_get, handle_resource_list,
    handle_resource_update,
    handle_statistics,
    handle_tool_delete, handle_tool_get, handle_tool_list, handle_tool_update,
};
use self::response::{json_err, json_ok, raw_json_response, read_body};
#[cfg(feature = "ui")]
use self::response::redirect_response;
use self::routes::{
    ForwardRoute, ManagementRoute, NamespaceRoute,
    PromptRoute, ResourceRoute, ToolRoute,
    resolve_forward_route,
    resolve_management_route, resolve_namespace_route,
    resolve_prompt_route, resolve_resource_route,
    resolve_tool_route,
};

pub struct WanakuManagementService {
    registry: InMemoryRegistry,
    features: Vec<Box<dyn Feature>>,
    #[cfg(feature = "ui")]
    ui_path: Option<std::path::PathBuf>,
}

impl WanakuManagementService {
    pub fn new(
        registry: InMemoryRegistry,
        features: Vec<Box<dyn Feature>>,
    ) -> Self {
        #[cfg(feature = "ui")]
        let ui_path = wanaku_apis::config::ENV.ui_path.clone();
        #[cfg(feature = "ui")]
        if let Some(p) = &ui_path {
            tracing::info!(path = %p.display(), "Admin UI serving enabled");
        }

        Self {
            registry,
            features,
            #[cfg(feature = "ui")]
            ui_path,
        }
    }
}

#[async_trait]
impl ServeHttp for WanakuManagementService {
    async fn response(&self, http_session: &mut ServerSession) -> Response<Vec<u8>> {
        let req = http_session.req_header();
        let uri = &req.uri;
        let path = uri.path().to_owned();
        let query = uri.query().map(std::borrow::ToOwned::to_owned);
        let method = req.method.as_str().to_owned();
        let headers = req.headers.clone();

        #[cfg(feature = "ui")]
        {
            if path == "/" {
                return redirect_response("/admin/");
            }

            if path.starts_with("/admin") {
                return ui::serve_ui(&self.ui_path, &path);
            }
        }

        let body = match method.as_str() {
            "POST" | "PUT" | "PATCH" if path.starts_with("/api/") => {
                match read_body(http_session).await {
                    Ok(b) => Some(b),
                    Err(resp) => return resp,
                }
            }
            _ => None,
        };

        let ctx = HttpContext::new(&method, &path, query.as_deref(), body.as_deref(), &headers);
        dispatch(&ctx, &self.registry, &self.features).await
    }
}

#[expect(clippy::too_many_lines, clippy::cognitive_complexity, reason = "route dispatch requires sequential matching")]
pub(crate) async fn dispatch(
    ctx: &HttpContext<'_>,
    registry: &InMemoryRegistry,
    features: &[Box<dyn Feature>],
) -> Response<Vec<u8>> {
    if ctx.path == "/healthz" || ctx.path == "/health" {
        return json_ok(&serde_json::json!({"status": "ok"}));
    }

    if ctx.path == "/openapi.json" {
        let openapi_body = crate::openapi::openapi_json();
        return raw_json_response(openapi_body);
    }

    let mgmt_route = resolve_management_route(ctx.method, ctx.path);
    if mgmt_route != ManagementRoute::NotFound {
        return match mgmt_route {
            ManagementRoute::Statistics => handle_statistics(registry),
            ManagementRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
        };
    }

    tracing::debug!(method = %ctx.method, path = %ctx.path, "management API request");

    let tool_route = resolve_tool_route(ctx.method, ctx.path);
    if tool_route != ToolRoute::NotFound {
        return match tool_route {
            ToolRoute::List => handle_tool_list(registry),
            ToolRoute::GetByName(name) => handle_tool_get(registry, &name),
            ToolRoute::Update(name) => match ctx.body {
                Some(b) => handle_tool_update(registry, &name, b),
                None => json_err(StatusCode::BAD_REQUEST, "request body required"),
            },
            ToolRoute::Delete(name) => handle_tool_delete(registry, &name),
            ToolRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
        };
    }

    let resource_route = resolve_resource_route(ctx.method, ctx.path);
    if resource_route != ResourceRoute::NotFound {
        return match resource_route {
            ResourceRoute::List => handle_resource_list(registry),
            ResourceRoute::GetByName(name) => handle_resource_get(registry, &name),
            ResourceRoute::Update(name) => match ctx.body {
                Some(b) => handle_resource_update(registry, &name, b),
                None => json_err(StatusCode::BAD_REQUEST, "request body required"),
            },
            ResourceRoute::Delete(name) => handle_resource_delete(registry, &name),
            ResourceRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
        };
    }

    let prompt_route = resolve_prompt_route(ctx.method, ctx.path);
    if prompt_route != PromptRoute::NotFound {
        return match prompt_route {
            PromptRoute::List => handle_prompt_list(registry),
            PromptRoute::GetByName(name) => handle_prompt_get(registry, &name),
            PromptRoute::Delete(name) => handle_prompt_delete(registry, &name),
            PromptRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
        };
    }

    let ns_route = resolve_namespace_route(ctx.method, ctx.path);
    if ns_route != NamespaceRoute::NotFound {
        return match ns_route {
            NamespaceRoute::List => handle_namespace_list(registry),
            NamespaceRoute::GetByName(name) => handle_namespace_get(registry, &name),
            NamespaceRoute::Create => match ctx.body {
                Some(b) => handle_namespace_create(registry, b),
                None => json_err(StatusCode::BAD_REQUEST, "request body required"),
            },
            NamespaceRoute::Update(id) => match ctx.body {
                Some(b) => handle_namespace_update(registry, &id, b),
                None => json_err(StatusCode::BAD_REQUEST, "request body required"),
            },
            NamespaceRoute::Delete(name) => handle_namespace_delete(registry, &name),
            NamespaceRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
        };
    }

    let forward_route = resolve_forward_route(ctx.method, ctx.path);
    if forward_route != ForwardRoute::NotFound {
        return match forward_route {
            ForwardRoute::List => handle_forward_list(registry),
            ForwardRoute::GetByName(name) => handle_forward_get(registry, &name),
            ForwardRoute::Create => match ctx.body {
                Some(b) => handle_forward_create(registry, b).await,
                None => json_err(StatusCode::BAD_REQUEST, "request body required"),
            },
            ForwardRoute::Delete(name) => handle_forward_delete(registry, &name),
            ForwardRoute::Refresh(name) => handle_forward_refresh(registry, &name).await,
            ForwardRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
        };
    }

    for feature in features {
        if let Some(response) = feature.handle_route(ctx).await {
            return response;
        }
    }

    json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default())
}

#[cfg(test)]
mod dispatch_tests {
    use super::*;
    use wanaku_apis::feature::{Feature, HttpContext};
    use wanaku_apis::registry::InMemoryRegistry;

    fn parse_body(resp: &http::Response<Vec<u8>>) -> serde_json::Value {
        serde_json::from_slice(resp.body()).unwrap_or_default()
    }

    fn data_field(resp: &http::Response<Vec<u8>>) -> serde_json::Value {
        let body = parse_body(resp);
        body.get("data").cloned().unwrap_or_default()
    }

    #[tokio::test]
    async fn dispatch_get_tools_returns_empty_list() {
        let registry = InMemoryRegistry::new();
        let headers = http::HeaderMap::new();
        let features: &[Box<dyn Feature>] = &[];
        let ctx = HttpContext::new("GET", "/api/v1/tools", None, None, &headers);

        let resp = dispatch(&ctx, &registry, features).await;

        assert_eq!(resp.status(), 200);
        assert_eq!(data_field(&resp).as_array().map(|a| a.len()), Some(0));
    }

    #[tokio::test]
    async fn dispatch_unknown_path_returns_404() {
        let registry = InMemoryRegistry::new();
        let headers = http::HeaderMap::new();
        let features: &[Box<dyn Feature>] = &[];
        let ctx = HttpContext::new("GET", "/no/such/path", None, None, &headers);

        let resp = dispatch(&ctx, &registry, features).await;

        assert_eq!(resp.status(), 404);
    }

    #[tokio::test]
    async fn dispatch_post_namespace_create() {
        let registry = InMemoryRegistry::new();
        let headers = http::HeaderMap::new();
        let features: &[Box<dyn Feature>] = &[];
        let body = r#"{"name":"test-ns"}"#;
        let ctx = HttpContext::new("POST", "/api/v1/namespaces", None, Some(body), &headers);

        let resp = dispatch(&ctx, &registry, features).await;

        assert_eq!(resp.status(), 200);
        let data = data_field(&resp);
        assert_eq!(data.get("name").and_then(|v| v.as_str()), Some("test-ns"));
    }

    #[tokio::test]
    async fn dispatch_health_returns_ok() {
        let registry = InMemoryRegistry::new();
        let headers = http::HeaderMap::new();
        let features: &[Box<dyn Feature>] = &[];
        let ctx = HttpContext::new("GET", "/healthz", None, None, &headers);

        let resp = dispatch(&ctx, &registry, features).await;

        assert_eq!(resp.status(), 200);
        let body = parse_body(&resp);
        let data = body.get("data").cloned().unwrap_or_default();
        assert_eq!(data.get("status").and_then(|v| v.as_str()), Some("ok"));
    }

    #[tokio::test]
    async fn dispatch_post_without_body_returns_400() {
        let registry = InMemoryRegistry::new();
        let headers = http::HeaderMap::new();
        let features: &[Box<dyn Feature>] = &[];
        let ctx = HttpContext::new("POST", "/api/v1/namespaces", None, None, &headers);

        let resp = dispatch(&ctx, &registry, features).await;

        assert_eq!(resp.status(), 400);
    }
}
