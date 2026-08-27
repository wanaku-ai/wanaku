#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

//! Transport-neutral action-policy schema and matcher compilation.

mod engine;
mod matcher;
mod predicate;
mod schema;
mod validation;

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
