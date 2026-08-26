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
pub mod revision_persistence;
mod routes;
pub mod schema;
pub mod state;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};

use wanaku_apis::feature::{Feature, HttpContext};

use crate::config::EvaluatorsConfig;
use crate::routes::{
    EvaluatorRoute, handle_activate_revision, handle_active_revision, handle_bind_namespace,
    handle_get_revision, handle_list_bindings, handle_list_evaluators,
    handle_list_llm_connections, handle_list_revisions, handle_unbind_namespace,
    handle_update_evaluators, resolve_evaluator_route,
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

    /// Enable revision persistence, loading any previously persisted history and
    /// restoring the active revision as the live runtime configuration. Call
    /// after [`Self::with_metrics`] so the restored snapshot updates metrics.
    #[must_use]
    pub fn with_revision_persistence(
        mut self,
        backend: std::sync::Arc<dyn crate::revision_persistence::RevisionPersistence>,
    ) -> Self {
        self.state = self.state.with_revision_persistence(backend);
        self
    }

    fn load_llm_connections_from_yaml(&self, root: &serde_yaml::Value) {
        let Some(conn_val) = root.get("llm_connections") else {
            return;
        };
        let Some(connections) = parse_llm_connections_yaml(conn_val) else {
            return;
        };

        if let Err(e) = self.state.load_llm_connections(connections) {
            tracing::error!(error = %e, "llm_connections rejected; no connections loaded");
        }
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
            EvaluatorRoute::ListLlmConnections => handle_list_llm_connections(&self.state),
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
        // Connections are config-only and must load before reconciliation so
        // that activation can validate every evaluator's connection reference.
        self.load_llm_connections_from_yaml(root);

        // Absent or unparseable `evaluators` yields `None`, which tells
        // reconciliation to re-activate the persisted active revision (if any)
        // rather than clear it — a restart keeps the last known configuration.
        let startup_defs = root
            .get("evaluators")
            .and_then(|eval_val| parse_evaluator_yaml(eval_val));

        if let Some(ref defs) = startup_defs {
            tracing::info!(count = defs.len(), "evaluators loaded from wanaku.yaml");
        }

        // Reconcile the startup config against any persisted active revision.
        // Every path re-validates and re-compiles through the safe activation
        // path and fails closed, so a restart behaves like a first boot.
        self.state.reconcile_startup(startup_defs);
    }

    fn load_env_config(&self) {}
}

fn parse_llm_connections_yaml(
    conn_val: &serde_yaml::Value,
) -> Option<Vec<crate::config::LlmConnection>> {
    match serde_yaml::from_value::<Vec<crate::config::LlmConnection>>(conn_val.clone()) {
        Ok(connections) => Some(connections),
        Err(e) => {
            tracing::warn!(error = %e, "failed to parse llm_connections from wanaku.yaml");
            None
        }
    }
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
