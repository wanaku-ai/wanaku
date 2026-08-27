#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

//! Transport-neutral action-policy schema and matcher compilation.

mod engine;
pub mod filter;
mod handlers;
mod matcher;
mod predicate;
pub mod revision;
pub mod revision_persistence;
mod routes;
mod schema;
mod state;
mod validation;

use crate::routes::ActionPolicyRoute;
use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};
use wanaku_types::feature::{Feature, HttpContext};

pub use engine::{
    ActionContext, ActorContext, DEFAULT_DENY_MESSAGE, DEFAULT_DENY_REASON_CODE, DecisionDetails,
    MatchedRule, PolicyDecision, PolicyEngine, PolicyState, PrimaryDenyReason,
};
pub use matcher::{CompiledMatcher, Matcher, MatcherCompileError};
pub use predicate::CompiledPredicate;
pub use schema::{
    ActionPolicy, Effect, MatchExpression, MatchKind, Predicate, Rule, Selectors, TargetType,
};
pub use validation::{CompiledPolicy, CompiledRule, ValidationError};

pub use state::{ActionPolicyState, PolicySnapshot};

/// Startup-configured action-policy feature.
pub struct ActionPolicyFeature {
    state: ActionPolicyState,
}

impl ActionPolicyFeature {
    #[must_use]
    pub fn new() -> Self {
        Self {
            state: ActionPolicyState::new(),
        }
    }

    #[must_use]
    pub fn with_revision_persistence(
        mut self,
        persistence: std::sync::Arc<dyn revision_persistence::RevisionPersistence>,
    ) -> Self {
        self.state = self.state.with_revision_persistence(persistence);
        self
    }
}

impl Default for ActionPolicyFeature {
    fn default() -> Self {
        Self::new()
    }
}

struct ActionPolicyExtension {
    state: ActionPolicyState,
}

impl PipelineExtension for ActionPolicyExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.state.clone());
    }
}

#[async_trait::async_trait]
impl Feature for ActionPolicyFeature {
    fn name(&self) -> &'static str {
        "action-policy"
    }

    fn register_filters(&self, registry: &mut FilterRegistry) {
        praxis_filter::register_filters!(
            @register registry,
            http "wanaku_action_policy" => crate::filter::ActionPolicyFilter::from_config
        );
    }

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        vec![Box::new(ActionPolicyExtension {
            state: self.state.clone(),
        })]
    }

    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        use handlers::{
            handle_activate_revision, handle_active_revision, handle_effective,
            handle_get_revision, handle_list_revisions, handle_update,
        };
        Some(
            match routes::resolve_action_policy_route(ctx.method, ctx.path) {
                ActionPolicyRoute::Effective => handle_effective(&self.state),
                ActionPolicyRoute::Update => handle_update(&self.state, ctx.body.unwrap_or("")),
                ActionPolicyRoute::ListRevisions => handle_list_revisions(&self.state),
                ActionPolicyRoute::ActiveRevision => handle_active_revision(&self.state),
                ActionPolicyRoute::GetRevision(id) => handle_get_revision(&self.state, id),
                ActionPolicyRoute::ActivateRevision(id) => {
                    handle_activate_revision(&self.state, id, ctx.body.unwrap_or(""))
                }
                ActionPolicyRoute::NotFound => return None,
            },
        )
    }

    fn load_yaml_config(&self, root: &serde_yaml::Value) {
        self.state.reconcile_startup(root.get("action_policy"));
    }

    fn load_env_config(&self) {}
}
