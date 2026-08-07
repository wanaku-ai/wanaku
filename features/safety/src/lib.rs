#![deny(unsafe_code)]

pub mod classifier;
pub mod filter;
mod routes;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};

use wanaku_praxis_apis::feature::Feature;

use crate::classifier::{SafetyConfig, SafetyState};
use crate::routes::{SafetyRoute, handle_safety_delete, handle_safety_get, handle_safety_update, resolve_safety_route};

pub struct SafetyFeature {
    state: SafetyState,
}

impl SafetyFeature {
    #[must_use]
    pub fn new() -> Self {
        Self {
            state: SafetyState::new(),
        }
    }
}

struct SafetyStateExtension {
    state: SafetyState,
}

impl PipelineExtension for SafetyStateExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.state.clone());
    }
}

#[async_trait::async_trait]
impl Feature for SafetyFeature {
    fn name(&self) -> &'static str {
        "safety"
    }

    fn register_filters(&self, registry: &mut FilterRegistry) {
        praxis_filter::register_filters!(
            @register registry,
            http "wanaku_safety_check" => crate::filter::SafetyCheckFilter::from_config
        );
    }

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        vec![Box::new(SafetyStateExtension {
            state: self.state.clone(),
        })]
    }

    async fn handle_route(
        &self,
        method: &str,
        path: &str,
        _query: Option<&str>,
        body: Option<&str>,
    ) -> Option<Response<Vec<u8>>> {
        let route = resolve_safety_route(method, path);
        if route == SafetyRoute::NotFound {
            return None;
        }
        Some(match route {
            SafetyRoute::Get => handle_safety_get(&self.state),
            SafetyRoute::Update => handle_safety_update(&self.state, body.unwrap_or("")),
            SafetyRoute::Delete => handle_safety_delete(&self.state),
            SafetyRoute::NotFound => return None,
        })
    }

    fn load_yaml_config(&self, root: &serde_yaml::Value) {
        if let Some(safety_val) = root.get("safety") {
            match serde_yaml::from_value::<SafetyConfig>(safety_val.clone()) {
                Ok(cfg) => {
                    tracing::info!(
                        model = %cfg.llm_model,
                        url = %cfg.llm_url,
                        "safety classifier configured from wanaku.yaml"
                    );
                    self.state.configure(cfg);
                }
                Err(e) => {
                    tracing::warn!(error = %e, "failed to parse safety config from wanaku.yaml");
                }
            }
        }
    }

    fn load_env_config(&self) {
        if let Some(env_cfg) = SafetyConfig::from_env() {
            tracing::info!("safety classifier configured from environment variables");
            self.state.configure(env_cfg);
        }
    }
}
