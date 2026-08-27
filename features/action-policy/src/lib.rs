#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

//! Transport-neutral action-policy schema and matcher compilation.

mod matcher;
mod predicate;
mod schema;
mod validation;

pub use matcher::{CompiledMatcher, Matcher, MatcherCompileError};
pub use predicate::CompiledPredicate;
pub use schema::{
    ActionPolicy, Effect, MatchExpression, MatchKind, Predicate, Rule, Selectors, TargetType,
};
pub use validation::{CompiledPolicy, CompiledRule, ValidationError};
