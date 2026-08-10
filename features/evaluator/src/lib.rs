#![deny(unsafe_code)]

pub mod action;
pub mod config;
pub mod engine;
pub mod filter;
mod host;
pub mod llm_op;
mod routes;
pub mod state;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};

use wanaku_praxis_apis::feature::Feature;

use crate::config::EvaluatorsConfig;
use crate::routes::{
    EvaluatorRoute, handle_bind_namespace, handle_list_bindings, handle_list_evaluators,
    handle_unbind_namespace, handle_update_evaluators, resolve_evaluator_route,
};
use crate::state::EvaluatorState;

pub struct EvaluatorFeature {
    state: EvaluatorState,
}

impl EvaluatorFeature {
    #[must_use]
    pub fn new() -> Self {
        Self {
            state: EvaluatorState::new(),
        }
    }
}

struct EvaluatorStateExtension {
    state: EvaluatorState,
}

impl PipelineExtension for EvaluatorStateExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.state.clone());
    }
}

#[async_trait::async_trait]
impl Feature for EvaluatorFeature {
    fn name(&self) -> &'static str {
        "evaluator"
    }

    fn register_filters(&self, registry: &mut FilterRegistry) {
        praxis_filter::register_filters!(
            @register registry,
            http "wanaku_evaluator" => crate::filter::EvaluatorFilter::from_config
        );
    }

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        vec![Box::new(EvaluatorStateExtension {
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
        let route = resolve_evaluator_route(method, path);
        if route == EvaluatorRoute::NotFound {
            return None;
        }
        Some(match route {
            EvaluatorRoute::ListEvaluators => handle_list_evaluators(&self.state),
            EvaluatorRoute::UpdateEvaluators => {
                handle_update_evaluators(&self.state, body.unwrap_or(""))
            }
            EvaluatorRoute::ListBindings => handle_list_bindings(&self.state),
            EvaluatorRoute::BindNamespace(ns) => {
                handle_bind_namespace(&self.state, &ns, body.unwrap_or(""))
            }
            EvaluatorRoute::UnbindNamespace(ns) => handle_unbind_namespace(&self.state, &ns),
            EvaluatorRoute::NotFound => return None,
        })
    }

    fn load_yaml_config(&self, root: &serde_yaml::Value) {
        if let Some(eval_val) = root.get("evaluators") {
            match serde_yaml::from_value::<EvaluatorsConfig>(eval_val.clone()) {
                Ok(config) => {
                    let count = config.evaluators.len();
                    tracing::info!(count = count, "evaluators loaded from wanaku.yaml");
                    self.state.load_evaluators(config.evaluators);
                }
                Err(e) => {
                    // Try parsing as a direct list
                    match serde_yaml::from_value::<Vec<crate::config::EvaluatorDef>>(
                        eval_val.clone(),
                    ) {
                        Ok(defs) => {
                            let count = defs.len();
                            tracing::info!(count = count, "evaluators loaded from wanaku.yaml");
                            self.state.load_evaluators(defs);
                        }
                        Err(_) => {
                            tracing::warn!(
                                error = %e,
                                "failed to parse evaluators config from wanaku.yaml"
                            );
                        }
                    }
                }
            }
        }
    }

    fn load_env_config(&self) {}
}
