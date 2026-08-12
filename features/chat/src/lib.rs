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
    inference_base_url: String,
    upstream_host: Option<String>,
    api_key: String,
    client: reqwest::Client,
}

impl ChatFeature {
    #[must_use]
    pub fn new(
        inference_base_url: String,
        upstream_host: Option<String>,
        api_key: String,
    ) -> Self {
        Self {
            inference_base_url,
            upstream_host,
            api_key,
            client: reqwest::Client::new(),
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
            ChatRoute::ListModels(_) => {
                handle_chat_list_models(
                    &self.client,
                    &self.inference_base_url,
                    self.upstream_host.as_deref(),
                    &self.api_key,
                )
                .await
            }
            ChatRoute::Completions => {
                handle_chat_completions(
                    &self.client,
                    &self.inference_base_url,
                    self.upstream_host.as_deref(),
                    &self.api_key,
                    body.unwrap_or(""),
                )
                .await
            }
            ChatRoute::NotFound => return None,
        })
    }

    fn load_yaml_config(&self, _root: &serde_yaml::Value) {}

    fn load_env_config(&self) {}
}
