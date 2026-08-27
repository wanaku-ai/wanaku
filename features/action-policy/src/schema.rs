use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use serde_json::Value;

/// A complete action-policy document.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ActionPolicy {
    #[serde(default)]
    pub rules: Vec<Rule>,
}

/// One stable, independently auditable policy rule.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Rule {
    pub id: String,
    #[serde(default)]
    pub description: Option<String>,
    pub effect: Effect,
    #[serde(default)]
    pub selectors: Selectors,
    #[serde(default)]
    pub predicates: Vec<Predicate>,
    #[serde(default)]
    pub reason_code: Option<String>,
    /// A safe message that can be returned to an action caller.
    #[serde(default)]
    pub message: Option<String>,
    #[serde(default)]
    pub metadata: BTreeMap<String, Value>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Effect {
    Allow,
    Deny,
}

/// Transport-neutral action selectors.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Selectors {
    #[serde(default)]
    pub namespace: Option<String>,
    /// The action operation. MCP adapters use values such as `tools/call`.
    #[serde(default)]
    pub operation: Option<String>,
    #[serde(default)]
    pub target_type: Option<TargetType>,
    #[serde(default)]
    pub target_name: Option<MatchExpression>,
    #[serde(default)]
    pub labels: BTreeMap<String, String>,
    #[serde(default)]
    pub uri: Option<MatchExpression>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum TargetType {
    Tool,
    Resource,
    Prompt,
}

/// A named matcher and its literal pattern.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct MatchExpression {
    pub matcher: MatchKind,
    pub value: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum MatchKind {
    Exact,
    Glob,
    Prefix,
}

/// A typed comparison against a JSON Pointer.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "operator", rename_all = "snake_case", deny_unknown_fields)]
pub enum Predicate {
    Exists { pointer: String, value: bool },
    Equals { pointer: String, value: Value },
    NotEquals { pointer: String, value: Value },
    OneOf { pointer: String, values: Vec<Value> },
    NotOneOf { pointer: String, values: Vec<Value> },
}

impl Predicate {
    #[must_use]
    pub fn pointer(&self) -> &str {
        match self {
            Self::Exists { pointer, .. }
            | Self::Equals { pointer, .. }
            | Self::NotEquals { pointer, .. }
            | Self::OneOf { pointer, .. }
            | Self::NotOneOf { pointer, .. } => pointer,
        }
    }
}
