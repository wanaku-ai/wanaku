use serde_json::Value;

use crate::Predicate;

/// A validated JSON Pointer predicate.
#[derive(Debug, Clone)]
pub struct CompiledPredicate(Predicate);

impl CompiledPredicate {
    pub(crate) const fn new(predicate: Predicate) -> Self {
        Self(predicate)
    }

    /// Test a predicate against a JSON document.
    ///
    /// Missing values never match comparison operators. They only match
    /// `exists: false`. JSON `null` is a present, typed value.
    #[must_use]
    pub fn matches(&self, document: &Value) -> bool {
        let value = document.pointer(self.0.pointer());
        match &self.0 {
            Predicate::Exists {
                value: expected, ..
            } => value.is_some() == *expected,
            Predicate::Equals {
                value: expected, ..
            } => value.is_some_and(|actual| actual == expected),
            Predicate::NotEquals {
                value: expected, ..
            } => value.is_some_and(|actual| actual != expected),
            Predicate::OneOf { values, .. } => {
                value.is_some_and(|actual| values.iter().any(|expected| actual == expected))
            }
            Predicate::NotOneOf { values, .. } => {
                value.is_some_and(|actual| values.iter().all(|expected| actual != expected))
            }
        }
    }
}
