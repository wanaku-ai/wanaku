#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

//! Transport-neutral action-policy schema and matcher compilation.

mod engine;
pub mod filter;
mod matcher;
mod predicate;
mod schema;
mod state;
mod validation;

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

    async fn handle_route(&self, _ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        None
    }

    fn load_yaml_config(&self, root: &serde_yaml::Value) {
        self.state.load_yaml(root.get("action_policy"));
    }

    fn load_env_config(&self) {}
}
