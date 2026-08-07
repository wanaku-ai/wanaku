#![deny(unsafe_code)]

mod routes;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension};

use wanaku_praxis_apis::feature::Feature;

use crate::routes::{
    ChatRoute, handle_chat_completions, handle_chat_list_llms, handle_chat_list_models,
    resolve_chat_route,
};

pub struct ChatFeature {
    ollama_proxy: String,
}

impl ChatFeature {
    #[must_use]
    pub fn new(ollama_proxy_port: u16) -> Self {
        Self {
            ollama_proxy: format!("http://127.0.0.1:{ollama_proxy_port}"),
        }
    }
}

#[async_trait::async_trait]
impl Feature for ChatFeature {
    fn name(&self) -> &'static str {
        "chat"
    }

    fn register_filters(&self, _registry: &mut FilterRegistry) {}

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        vec![]
    }

    async fn handle_route(
        &self,
        method: &str,
        path: &str,
        _query: Option<&str>,
        body: Option<&str>,
    ) -> Option<Response<Vec<u8>>> {
        let route = resolve_chat_route(method, path);
        if route == ChatRoute::NotFound {
            return None;
        }
        Some(match route {
            ChatRoute::ListLlms => handle_chat_list_llms(),
            ChatRoute::ListModels(_) => handle_chat_list_models(&self.ollama_proxy).await,
            ChatRoute::Completions => {
                handle_chat_completions(&self.ollama_proxy, body.unwrap_or("")).await
            }
            ChatRoute::NotFound => return None,
        })
    }

    fn load_yaml_config(&self, _root: &serde_yaml::Value) {}

    fn load_env_config(&self) {}
}
