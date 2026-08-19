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

use wanaku_apis::feature::Feature;
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
    #[expect(clippy::too_many_lines, clippy::large_stack_frames, reason = "route dispatch requires sequential matching")]
    async fn response(&self, http_session: &mut ServerSession) -> Response<Vec<u8>> {
        let req = http_session.req_header();
        let uri = &req.uri;
        let path = uri.path().to_owned();
        let query = uri.query().map(std::borrow::ToOwned::to_owned);
        let method = req.method.as_str().to_owned();
        let headers = req.headers.clone();

        if path == "/healthz" || path == "/health" {
            return json_ok(&serde_json::json!({"status": "ok"}));
        }

        if path == "/openapi.json" {
            let body = crate::openapi::openapi_json();
            return raw_json_response(body);
        }

        #[cfg(feature = "ui")]
        {
            if path == "/" {
                return redirect_response("/admin/");
            }

            if path.starts_with("/admin") {
                return ui::serve_ui(&self.ui_path, &path);
            }
        }

        let mgmt_route = resolve_management_route(&method, &path);
        if mgmt_route != ManagementRoute::NotFound {
            return match mgmt_route {
                ManagementRoute::Statistics => handle_statistics(&self.registry),
                ManagementRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
            };
        }

        tracing::debug!(%method, %path, "management API request");

        let tool_route = resolve_tool_route(&method, &path);
        if tool_route != ToolRoute::NotFound {
            return match tool_route {
                ToolRoute::List => handle_tool_list(&self.registry),
                ToolRoute::GetByName(name) => handle_tool_get(&self.registry, &name),
                ToolRoute::Update(name) => match read_body(http_session).await {
                    Ok(body) => handle_tool_update(&self.registry, &name, &body),
                    Err(resp) => resp,
                },
                ToolRoute::Delete(name) => handle_tool_delete(&self.registry, &name),
                ToolRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
            };
        }

        let resource_route = resolve_resource_route(&method, &path);
        if resource_route != ResourceRoute::NotFound {
            return match resource_route {
                ResourceRoute::List => handle_resource_list(&self.registry),
                ResourceRoute::GetByName(name) => handle_resource_get(&self.registry, &name),
                ResourceRoute::Update(name) => match read_body(http_session).await {
                    Ok(body) => handle_resource_update(&self.registry, &name, &body),
                    Err(resp) => resp,
                },
                ResourceRoute::Delete(name) => handle_resource_delete(&self.registry, &name),
                ResourceRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
            };
        }

        let prompt_route = resolve_prompt_route(&method, &path);
        if prompt_route != PromptRoute::NotFound {
            return match prompt_route {
                PromptRoute::List => handle_prompt_list(&self.registry),
                PromptRoute::GetByName(name) => handle_prompt_get(&self.registry, &name),
                PromptRoute::Delete(name) => handle_prompt_delete(&self.registry, &name),
                PromptRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
            };
        }

        let ns_route = resolve_namespace_route(&method, &path);
        if ns_route != NamespaceRoute::NotFound {
            return match ns_route {
                NamespaceRoute::List => handle_namespace_list(&self.registry),
                NamespaceRoute::GetByName(name) => handle_namespace_get(&self.registry, &name),
                NamespaceRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_namespace_create(&self.registry, &body),
                    Err(resp) => resp,
                },
                NamespaceRoute::Update(id) => match read_body(http_session).await {
                    Ok(body) => handle_namespace_update(&self.registry, &id, &body),
                    Err(resp) => resp,
                },
                NamespaceRoute::Delete(name) => handle_namespace_delete(&self.registry, &name),
                NamespaceRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
            };
        }

        let forward_route = resolve_forward_route(&method, &path);
        if forward_route != ForwardRoute::NotFound {
            return match forward_route {
                ForwardRoute::List => handle_forward_list(&self.registry),
                ForwardRoute::GetByName(name) => handle_forward_get(&self.registry, &name),
                ForwardRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_forward_create(&self.registry, &body).await,
                    Err(resp) => resp,
                },
                ForwardRoute::Delete(name) => handle_forward_delete(&self.registry, &name),
                ForwardRoute::Refresh(name) => handle_forward_refresh(&self.registry, &name).await,
                ForwardRoute::NotFound => json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default()),
            };
        }

        // Feature dispatch — read body once for POST/PUT, then try each feature
        let feature_body = match method.as_str() {
            "POST" | "PUT" | "PATCH" => match read_body(http_session).await {
                Ok(b) => Some(b),
                Err(resp) => return resp,
            },
            _ => None,
        };

        for feature in &self.features {
            if let Some(response) = feature
                .handle_route(&method, &path, query.as_deref(), feature_body.as_deref(), &headers)
                .await
            {
                return response;
            }
        }

        json_err(StatusCode::NOT_FOUND, StatusCode::NOT_FOUND.canonical_reason().unwrap_or_default())
    }
}
