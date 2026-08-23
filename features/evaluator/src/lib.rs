#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

pub mod action;
pub mod config;
pub mod engine;
pub mod filter;
mod host;
pub use host::types as wit_types;
pub mod llm_op;
pub mod revision;
mod routes;
pub mod schema;
pub mod state;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};

use wanaku_apis::feature::{Feature, HttpContext};

use crate::config::EvaluatorsConfig;
use crate::routes::{
    EvaluatorRoute, handle_activate_revision, handle_active_revision, handle_bind_namespace,
    handle_get_revision, handle_list_bindings, handle_list_evaluators, handle_list_revisions,
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

    #[must_use]
    pub fn with_metrics(mut self, store: wanaku_apis::metrics::MetricsStore) -> Self {
        self.state = self.state.with_metrics(store);
        self
    }
}

impl Default for EvaluatorFeature {
    fn default() -> Self {
        Self::new()
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

    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        let route = resolve_evaluator_route(ctx.method, ctx.path);
        if route == EvaluatorRoute::NotFound {
            return None;
        }
        Some(match route {
            EvaluatorRoute::ListEvaluators => handle_list_evaluators(&self.state),
            EvaluatorRoute::UpdateEvaluators => {
                handle_update_evaluators(&self.state, ctx.body.unwrap_or(""))
            }
            EvaluatorRoute::ListRevisions => handle_list_revisions(&self.state),
            EvaluatorRoute::ActiveRevision => handle_active_revision(&self.state),
            EvaluatorRoute::GetRevision(id) => handle_get_revision(&self.state, id),
            EvaluatorRoute::ActivateRevision(id) => {
                handle_activate_revision(&self.state, id, ctx.body.unwrap_or(""))
            }
            EvaluatorRoute::ListBindings => handle_list_bindings(&self.state),
            EvaluatorRoute::BindNamespace(ns) => {
                handle_bind_namespace(&self.state, &ns, ctx.body.unwrap_or(""))
            }
            EvaluatorRoute::UnbindNamespace(ns) => handle_unbind_namespace(&self.state, &ns),
            EvaluatorRoute::NotFound => return None,
        })
    }

    fn load_yaml_config(&self, root: &serde_yaml::Value) {
        let Some(eval_val) = root.get("evaluators") else {
            return;
        };
        let Some(defs) = parse_evaluator_yaml(eval_val) else {
            return;
        };

        let count = defs.len();
        tracing::info!(count = count, "evaluators loaded from wanaku.yaml");

        match self.state.try_activate(
            defs.clone(),
            crate::revision::RevisionOrigin::Startup,
            None,
            None,
        ) {
            Ok(rev) => {
                tracing::info!(
                    revision_id = rev.metadata.id,
                    count = count,
                    "startup evaluator revision created"
                );
            }
            Err(e) => {
                tracing::warn!(error = %e, "startup revision failed, loading without tracking");
                self.state.load_evaluators(defs);
            }
        }
    }

    fn load_env_config(&self) {}
}

fn parse_evaluator_yaml(
    eval_val: &serde_yaml::Value,
) -> Option<Vec<crate::config::EvaluatorDef>> {
    match serde_yaml::from_value::<EvaluatorsConfig>(eval_val.clone()) {
        Ok(config) => Some(config.evaluators),
        Err(e) => {
            match serde_yaml::from_value::<Vec<crate::config::EvaluatorDef>>(eval_val.clone()) {
                Ok(defs) => Some(defs),
                Err(_) => {
                    tracing::warn!(error = %e, "failed to parse evaluators config from wanaku.yaml");
                    None
                }
            }
        }
    }
}
