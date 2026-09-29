#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

pub mod action;
pub mod api;
pub mod config;
pub mod engine;
pub mod engines;
pub mod evaluation;
pub mod filter;
mod host;
pub use host::types as wit_types;
pub mod revision;
pub mod revision_persistence;
mod routes;
pub mod schema;
pub mod state;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};

use wanaku_types::feature::{Feature, HttpContext};

use crate::config::EvaluatorsConfig;
use crate::routes::{
    EvaluatorRoute, handle_activate_revision, handle_active_revision, handle_bind_namespace,
    handle_get_revision, handle_list_bindings, handle_list_evaluators, handle_list_llm_connections,
    handle_list_revisions, handle_unbind_namespace, handle_update_evaluators,
    resolve_evaluator_route,
};
use crate::state::EvaluatorState;

pub struct EvaluatorFeature {
    state: EvaluatorState,
    audit: Option<wanaku_types::audit::InMemoryAuditStore>,
    governance: wanaku_types::governance::GovernanceConfig,
    startup_deny: std::sync::atomic::AtomicBool,
}

impl EvaluatorFeature {
    #[must_use]
    pub fn new() -> Self {
        Self {
            state: EvaluatorState::new(),
            audit: None,
            governance: wanaku_types::governance::GovernanceConfig::default(),
            startup_deny: std::sync::atomic::AtomicBool::new(false),
        }
    }

    #[must_use]
    pub fn with_governance(mut self, config: wanaku_types::governance::GovernanceConfig) -> Self {
        self.governance = config;
        self
    }

    #[must_use]
    pub fn with_metrics(mut self, store: wanaku_infra::metrics::MetricsStore) -> Self {
        self.state = self.state.with_metrics(store);
        self
    }

    #[must_use]
    pub fn with_audit(mut self, store: wanaku_types::audit::InMemoryAuditStore) -> Self {
        self.audit = Some(store);
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

    fn load_startup_policy(&self, root: &serde_yaml::Value) -> bool {
        let deny = match root.get("evaluator_startup_failure") {
            None => false,
            Some(value) => match value.as_str() {
                Some("abort") => false,
                Some("deny") => true,
                _ => {
                    self.state.mark_invalid("startup_policy_invalid");
                    return false;
                }
            },
        };
        self.startup_deny
            .store(deny, std::sync::atomic::Ordering::Relaxed);
        true
    }

    fn load_llm_connections_from_yaml(&self, root: &serde_yaml::Value) {
        let Some(conn_val) = root.get("llm_connections") else {
            return;
        };
        let Some(connections) = parse_llm_connections_yaml(conn_val) else {
            self.state.mark_invalid("connection_configuration_invalid");
            return;
        };

        if let Err(e) = self.state.load_llm_connections(connections) {
            self.state.mark_invalid("connection_configuration_invalid");
            tracing::error!(error = %e, "llm_connections rejected; no connections loaded");
        }
    }

    fn load_system_one_connections_from_yaml(&self, root: &serde_yaml::Value) {
        let Some(conn_val) = root.get("typesafe_system_one_connections") else {
            return;
        };
        let Some(connections) = parse_system_one_connections_yaml(conn_val) else {
            self.state.mark_invalid("connection_configuration_invalid");
            return;
        };
        if let Err(error) = self.state.load_system_one_connections(connections) {
            self.state.mark_invalid("connection_configuration_invalid");
            tracing::error!(error = %error, "typesafe_system_one_connections rejected; no connections loaded");
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
        let administrative_operation = administrative_operation(&route);
        let response = match route {
            EvaluatorRoute::Status => {
                crate::routes::handle_status(&self.state, &self.governance, ctx.query)
            }
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
        };
        if let Some(operation) = administrative_operation {
            record_administrative_event(self.audit.as_ref(), ctx, operation, response.status());
        }
        Some(response)
    }

    fn load_yaml_config(&self, root: &serde_yaml::Value) {
        // Connections are config-only and must load before reconciliation so
        // that activation can validate every evaluator's connection reference.
        self.load_llm_connections_from_yaml(root);
        self.load_system_one_connections_from_yaml(root);

        if !self.load_startup_policy(root) {
            return;
        }
        if self.state.active_config().invalid_reason.is_some() {
            return;
        }
        let startup_defs = match root.get("evaluators") {
            Some(value) => match parse_evaluator_yaml(value) {
                Some(defs) => Some(defs),
                None => {
                    self.state.mark_invalid("configuration_invalid");
                    return;
                }
            },
            None => None,
        };

        if let Some(ref defs) = startup_defs {
            tracing::info!(count = defs.len(), "evaluators loaded from wanaku.yaml");
        }

        // Reconcile the startup config against any persisted active revision.
        // Every path re-validates and re-compiles through the safe activation
        // path and fails closed, so a restart behaves like a first boot.
        self.state.reconcile_startup(startup_defs);
    }

    fn validate_startup(&self) -> Result<(), String> {
        let snapshot = self.state.try_active_config().map_err(str::to_owned)?;
        match snapshot.invalid_reason {
            Some(reason) if !self.startup_deny.load(std::sync::atomic::Ordering::Relaxed) => {
                Err(format!("evaluator startup failed: {reason}"))
            }
            _ => Ok(()),
        }
    }

    fn load_env_config(&self) {}
}

const fn administrative_operation(route: &EvaluatorRoute) -> Option<&'static str> {
    match route {
        EvaluatorRoute::UpdateEvaluators => Some("evaluator.update"),
        EvaluatorRoute::ActivateRevision(_) => Some("evaluator.revision.activate"),
        EvaluatorRoute::BindNamespace(_) => Some("evaluator.namespace.bind"),
        EvaluatorRoute::UnbindNamespace(_) => Some("evaluator.namespace.unbind"),
        _ => None,
    }
}

fn record_administrative_event(
    store: Option<&wanaku_types::audit::InMemoryAuditStore>,
    ctx: &HttpContext<'_>,
    operation: &str,
    status: http::StatusCode,
) {
    use wanaku_types::audit::{AuditCategory, AuditDecision, AuditEvent, AuditStore as _};
    let Some(store) = store else {
        return;
    };
    let success = status.is_success();
    let mut event = AuditEvent::new(
        AuditCategory::Administrative,
        if success {
            AuditDecision::Allow
        } else {
            AuditDecision::Error
        },
        operation,
        if success {
            "configuration_changed"
        } else {
            "configuration_change_failed"
        },
        if success {
            "The governance configuration changed."
        } else {
            "The governance configuration change failed."
        },
    );
    event.protocol = "http".to_owned();
    event.target_type = Some("evaluator_configuration".to_owned());
    event.target = Some(ctx.path.to_owned());
    event.response_status = Some(status.as_u16());
    if let Some(request_id) = ctx
        .headers
        .get("x-request-id")
        .and_then(|value| value.to_str().ok())
    {
        event.request_id = Some(request_id.to_owned());
        event.correlation_id = request_id.to_owned();
        event.stream_id = request_id.to_owned();
    }
    store.record(event);
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

fn parse_system_one_connections_yaml(
    conn_val: &serde_yaml::Value,
) -> Option<Vec<crate::config::SystemOneConnection>> {
    match serde_yaml::from_value::<Vec<crate::config::SystemOneConnection>>(conn_val.clone()) {
        Ok(connections) => Some(connections),
        Err(error) => {
            tracing::warn!(error = %error, "failed to parse typesafe_system_one_connections from wanaku.yaml");
            None
        }
    }
}

fn parse_evaluator_yaml(eval_val: &serde_yaml::Value) -> Option<Vec<crate::config::EvaluatorDef>> {
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

#[cfg(test)]
mod audit_tests {
    use http::{HeaderMap, HeaderValue, StatusCode};
    use wanaku_types::audit::{AuditQuery, AuditStore as _, InMemoryAuditStore};

    use super::record_administrative_event;

    #[test]
    fn malformed_connections_make_startup_invalid() {
        use wanaku_types::feature::Feature;
        for yaml in [
            "llm_connections: invalid",
            "typesafe_system_one_connections: invalid",
        ] {
            let feature = super::EvaluatorFeature::new();
            feature.load_yaml_config(&serde_yaml::from_str(yaml).unwrap());
            assert!(feature.validate_startup().is_err());
            assert_eq!(
                feature.state.active_config().invalid_reason,
                Some("connection_configuration_invalid")
            );
        }
    }

    #[test]
    fn non_string_startup_policy_is_invalid() {
        use wanaku_types::feature::Feature;
        let feature = super::EvaluatorFeature::new();
        feature.load_yaml_config(&serde_yaml::from_str("evaluator_startup_failure: true").unwrap());
        assert!(feature.validate_startup().is_err());
        assert_eq!(
            feature.state.active_config().invalid_reason,
            Some("startup_policy_invalid")
        );
    }

    #[test]
    fn malformed_startup_defaults_to_abort_and_can_explicitly_deny() {
        use wanaku_types::feature::Feature;
        let feature = super::EvaluatorFeature::new();
        feature.load_yaml_config(&serde_yaml::from_str("evaluators: {evalutors: []}").unwrap());
        assert!(feature.validate_startup().is_err());
        assert_eq!(
            feature.state.active_config().invalid_reason,
            Some("configuration_invalid")
        );
        let feature = super::EvaluatorFeature::new();
        feature.load_yaml_config(
            &serde_yaml::from_str("evaluators: invalid\nevaluator_startup_failure: deny").unwrap(),
        );
        assert!(feature.validate_startup().is_ok());
        assert_eq!(
            feature.state.active_config().invalid_reason,
            Some("configuration_invalid")
        );
    }

    #[test]
    fn administrative_events_use_request_correlation_header() {
        let store = InMemoryAuditStore::new(10);
        let mut headers = HeaderMap::new();
        headers.insert("x-request-id", HeaderValue::from_static("request-42"));
        let ctx = wanaku_types::feature::HttpContext::new(
            "PUT",
            "/api/v1/evaluators",
            None,
            None,
            &headers,
        );

        record_administrative_event(Some(&store), &ctx, "evaluator.update", StatusCode::OK);

        let events = store.query(&AuditQuery {
            limit: 10,
            ..AuditQuery::default()
        });
        assert_eq!(events.total, 1);
        assert_eq!(events.events[0].request_id.as_deref(), Some("request-42"));
        assert_eq!(events.events[0].correlation_id, "request-42");
        assert_eq!(events.events[0].stream_id, "request-42");
        assert!(events.events[0].actor.is_none());
    }
}
