mod handlers;
mod response;
mod routes;
mod ui;

pub use handlers::discover_tools_from_forward;

use async_trait::async_trait;
use http::Response;
use pingora_core::apps::http_app::ServeHttp;
use pingora_core::protocols::http::ServerSession;
use tracing::info;

use wanaku_praxis_apis::feature::Feature;
use wanaku_praxis_apis::registry::InMemoryRegistry;

use self::handlers::{
    handle_capability_list, handle_capability_state,
    handle_forward_create, handle_forward_delete, handle_forward_get, handle_forward_list,
    handle_forward_refresh,
    handle_namespace_create, handle_namespace_delete, handle_namespace_get, handle_namespace_list,
    handle_namespace_update,
    handle_prompt_create, handle_prompt_delete, handle_prompt_get, handle_prompt_list,
    handle_resource_create, handle_resource_delete, handle_resource_get, handle_resource_list,
    handle_service_create, handle_service_delete, handle_service_get, handle_service_list,
    handle_statistics,
    handle_tool_create, handle_tool_delete, handle_tool_get, handle_tool_list,
};
use self::response::{json_err, json_ok, raw_json_response, read_body, redirect_response};
use self::routes::{
    CapabilityRoute, ForwardRoute, ManagementRoute, NamespaceRoute,
    PromptRoute, ResourceRoute, ServiceRoute, ToolRoute,
    resolve_capability_route, resolve_forward_route,
    resolve_management_route, resolve_namespace_route,
    resolve_prompt_route, resolve_resource_route, resolve_service_route,
    resolve_tool_route,
};
use self::ui::serve_ui;

pub struct WanakuManagementService {
    registry: InMemoryRegistry,
    features: Vec<Box<dyn Feature>>,
    proxy: Option<crate::proxy::ClassicProxy>,
    ui_path: Option<std::path::PathBuf>,
}

impl WanakuManagementService {
    pub fn new(
        registry: InMemoryRegistry,
        features: Vec<Box<dyn Feature>>,
    ) -> Self {
        let proxy = crate::proxy::ClassicProxy::from_config();
        if proxy.is_some() {
            info!("Classic proxy enabled via WANAKU_CLASSIC_URL");
        }

        let ui_path = wanaku_praxis_apis::config::ENV.ui_path.clone();
        if let Some(p) = &ui_path {
            info!(path = %p.display(), "Admin UI serving enabled");
        }

        Self {
            registry,
            features,
            proxy,
            ui_path,
        }
    }
}

#[async_trait]
impl ServeHttp for WanakuManagementService {
    async fn response(&self, http_session: &mut ServerSession) -> Response<Vec<u8>> {
        let uri = &http_session.req_header().uri;
        let path = uri.path().to_owned();
        let query = uri.query().map(|q| q.to_owned());
        let method = http_session.req_header().method.as_str().to_owned();

        if path == "/healthz" || path == "/health" {
            return json_ok(&serde_json::json!({"status": "ok"}));
        }

        if path == "/openapi.json" {
            let body = crate::openapi::openapi_json();
            return raw_json_response(body);
        }

        if path == "/" {
            return redirect_response("/admin/");
        }

        if path.starts_with("/admin") {
            return serve_ui(&self.ui_path, &path);
        }

        let mgmt_route = resolve_management_route(&method, &path);
        if mgmt_route != ManagementRoute::NotFound {
            return match mgmt_route {
                ManagementRoute::Statistics => handle_statistics(&self.registry),
                ManagementRoute::NotFound => json_err(404, "not found"),
            };
        }

        let capability_route = resolve_capability_route(&method, &path);
        if capability_route != CapabilityRoute::NotFound {
            return match capability_route {
                CapabilityRoute::List => handle_capability_list(&self.registry),
                CapabilityRoute::ToolsState => handle_capability_state(),
                CapabilityRoute::ResourcesState => handle_capability_state(),
                CapabilityRoute::FleetStatus => json_ok(&serde_json::json!({})),
                CapabilityRoute::NotFound => json_err(404, "not found"),
            };
        }

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

        let prompt_route = resolve_prompt_route(&method, &path);
        if prompt_route != PromptRoute::NotFound {
            return match prompt_route {
                PromptRoute::List => handle_prompt_list(&self.registry),
                PromptRoute::GetByName(name) => handle_prompt_get(&self.registry, &name),
                PromptRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_prompt_create(&self.registry, &body),
                    Err(resp) => resp,
                },
                PromptRoute::Delete(name) => handle_prompt_delete(&self.registry, &name),
                PromptRoute::NotFound => json_err(404, "not found"),
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
                NamespaceRoute::NotFound => json_err(404, "not found"),
            };
        }

        let service_route = resolve_service_route(&method, &path);
        if service_route != ServiceRoute::NotFound {
            return match service_route {
                ServiceRoute::List => handle_service_list(&self.registry),
                ServiceRoute::GetByName(name) => handle_service_get(&self.registry, &name),
                ServiceRoute::Create => match read_body(http_session).await {
                    Ok(body) => handle_service_create(&self.registry, &body),
                    Err(resp) => resp,
                },
                ServiceRoute::Delete(name) => handle_service_delete(&self.registry, &name),
                ServiceRoute::NotFound => json_err(404, "not found"),
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
                ForwardRoute::NotFound => json_err(404, "not found"),
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
                .handle_route(&method, &path, query.as_deref(), feature_body.as_deref())
                .await
            {
                return response;
            }
        }

        if crate::proxy::ClassicProxy::should_proxy(&path) {
            if let Some(proxy) = &self.proxy {
                return proxy.forward(&method, &path, feature_body).await;
            }
            return json_err(503, "Classic backend not configured (set WANAKU_CLASSIC_URL)");
        }

        json_err(404, "not found")
    }
}
