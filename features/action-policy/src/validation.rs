use std::collections::HashSet;

use thiserror::Error;

use crate::{
    ActionPolicy, CompiledMatcher, CompiledPredicate, MatchKind, MatcherCompileError, Rule,
};

/// A validated and compiled policy document.
pub struct CompiledPolicy {
    rules: Vec<CompiledRule>,
}

/// The compiled selectors and predicates for one rule.
pub struct CompiledRule {
    rule: Rule,
    target_name: Option<CompiledMatcher>,
    uri: Option<CompiledMatcher>,
    predicates: Vec<CompiledPredicate>,
}

#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum ValidationError {
    #[error("rule ID '{0}' is not a valid identifier")]
    InvalidRuleId(String),
    #[error("rule ID '{0}' is duplicated")]
    DuplicateRuleId(String),
    #[error("rule '{rule_id}' has an invalid {field} identifier: '{value}'")]
    InvalidIdentifier {
        rule_id: String,
        field: &'static str,
        value: String,
    },
    #[error("rule '{0}' must have at least one action selector")]
    MissingSelector(String),
    #[error("rule '{rule_id}' has an empty matcher value for {field}")]
    EmptyMatcher {
        rule_id: String,
        field: &'static str,
    },
    #[error("rule '{0}' can use only exact or prefix URI matching")]
    InvalidUriMatcher(String),
    #[error("rule '{0}' can use only exact or glob target-name matching")]
    InvalidTargetNameMatcher(String),
    #[error("rule '{rule_id}' has an invalid {field}: {source}")]
    InvalidMatcher {
        rule_id: String,
        field: &'static str,
        source: MatcherCompileError,
    },
    #[error("rule '{rule_id}' has an invalid JSON Pointer: '{pointer}'")]
    InvalidJsonPointer { rule_id: String, pointer: String },
    #[error("rule '{rule_id}' has an empty operand for operator '{operator}'")]
    EmptyOperand {
        rule_id: String,
        operator: &'static str,
    },
}

impl ActionPolicy {
    /// Validate and compile all matchers before policy activation.
    pub fn compile(self) -> Result<CompiledPolicy, ValidationError> {
        let mut ids = HashSet::new();
        let mut rules = Vec::with_capacity(self.rules.len());
        for rule in self.rules {
            validate_identifier(&rule.id)
                .map_err(|()| ValidationError::InvalidRuleId(rule.id.clone()))?;
            if !ids.insert(rule.id.clone()) {
                return Err(ValidationError::DuplicateRuleId(rule.id));
            }
            rules.push(CompiledRule::compile(rule)?);
        }
        Ok(CompiledPolicy { rules })
    }
}

impl CompiledPolicy {
    #[must_use]
    pub fn rules(&self) -> &[CompiledRule] {
        &self.rules
    }
}

impl CompiledRule {
    #[must_use]
    pub const fn rule(&self) -> &Rule {
        &self.rule
    }

    #[must_use]
    pub const fn target_name(&self) -> Option<&CompiledMatcher> {
        self.target_name.as_ref()
    }

    #[must_use]
    pub const fn uri(&self) -> Option<&CompiledMatcher> {
        self.uri.as_ref()
    }

    #[must_use]
    pub fn predicates(&self) -> &[CompiledPredicate] {
        &self.predicates
    }

    fn compile(rule: Rule) -> Result<Self, ValidationError> {
        if !has_selector(&rule) {
            return Err(ValidationError::MissingSelector(rule.id.clone()));
        }
        validate_rule_identifiers(&rule)?;

        let target_name = match rule.selectors.target_name.as_ref() {
            Some(expression)
                if !matches!(expression.matcher, MatchKind::Exact | MatchKind::Glob) =>
            {
                return Err(ValidationError::InvalidTargetNameMatcher(rule.id.clone()));
            }
            expression => compile_matcher(&rule, "target_name", expression)?,
        };
        let uri = match rule.selectors.uri.as_ref() {
            Some(expression)
                if !matches!(expression.matcher, MatchKind::Exact | MatchKind::Prefix) =>
            {
                return Err(ValidationError::InvalidUriMatcher(rule.id.clone()));
            }
            expression => compile_matcher(&rule, "uri", expression)?,
        };

        let predicates = compile_predicates(&rule)?;

        Ok(Self {
            rule,
            target_name,
            uri,
            predicates,
        })
    }
}

fn validate_rule_identifiers(rule: &Rule) -> Result<(), ValidationError> {
    validate_optional_identifier(rule, "namespace", rule.selectors.namespace.as_deref())?;
    validate_optional_identifier(rule, "operation", rule.selectors.operation.as_deref())?;
    validate_optional_identifier(rule, "reason_code", rule.reason_code.as_deref())?;
    for label in rule.selectors.labels.keys() {
        validate_named_identifier(&rule.id, "label", label)?;
    }
    for key in rule.metadata.keys() {
        validate_named_identifier(&rule.id, "metadata", key)?;
    }
    Ok(())
}

fn has_selector(rule: &Rule) -> bool {
    let selectors = &rule.selectors;
    selectors.namespace.is_some()
        || selectors.operation.is_some()
        || selectors.target_type.is_some()
        || selectors.target_name.is_some()
        || !selectors.labels.is_empty()
        || selectors.uri.is_some()
}

fn compile_predicates(rule: &Rule) -> Result<Vec<CompiledPredicate>, ValidationError> {
    let mut predicates = Vec::with_capacity(rule.predicates.len());
    for predicate in &rule.predicates {
        if !valid_json_pointer(predicate.pointer()) {
            return Err(ValidationError::InvalidJsonPointer {
                rule_id: rule.id.clone(),
                pointer: predicate.pointer().to_owned(),
            });
        }
        match predicate {
            crate::Predicate::OneOf { values, .. } if values.is_empty() => {
                return empty_operand(&rule.id, "one_of");
            }
            crate::Predicate::NotOneOf { values, .. } if values.is_empty() => {
                return empty_operand(&rule.id, "not_one_of");
            }
            _ => {}
        }
        predicates.push(CompiledPredicate::new(predicate.clone()));
    }
    Ok(predicates)
}

fn empty_operand<T>(rule_id: &str, operator: &'static str) -> Result<T, ValidationError> {
    Err(ValidationError::EmptyOperand {
        rule_id: rule_id.to_owned(),
        operator,
    })
}

fn compile_matcher(
    rule: &Rule,
    field: &'static str,
    expression: Option<&crate::MatchExpression>,
) -> Result<Option<CompiledMatcher>, ValidationError> {
    let Some(expression) = expression else {
        return Ok(None);
    };
    if expression.value.is_empty() {
        return Err(ValidationError::EmptyMatcher {
            rule_id: rule.id.clone(),
            field,
        });
    }
    CompiledMatcher::compile(expression)
        .map(Some)
        .map_err(|source| ValidationError::InvalidMatcher {
            rule_id: rule.id.clone(),
            field,
            source,
        })
}

fn validate_optional_identifier(
    rule: &Rule,
    field: &'static str,
    value: Option<&str>,
) -> Result<(), ValidationError> {
    match value {
        Some(value) => validate_named_identifier(&rule.id, field, value),
        None => Ok(()),
    }
}

fn validate_named_identifier(
    rule_id: &str,
    field: &'static str,
    value: &str,
) -> Result<(), ValidationError> {
    validate_identifier(value).map_err(|()| ValidationError::InvalidIdentifier {
        rule_id: rule_id.to_owned(),
        field,
        value: value.to_owned(),
    })
}

fn validate_identifier(value: &str) -> Result<(), ()> {
    let mut characters = value.chars();
    match characters.next() {
        Some(first) if first.is_ascii_alphanumeric() => {}
        _ => return Err(()),
    }
    if characters.all(|character| {
        character.is_ascii_alphanumeric() || matches!(character, '-' | '_' | '.' | '/' | ':')
    }) {
        Ok(())
    } else {
        Err(())
    }
}

fn valid_json_pointer(pointer: &str) -> bool {
    if pointer.is_empty() {
        return true;
    }
    if !pointer.starts_with('/') {
        return false;
    }
    let mut characters = pointer.chars();
    while let Some(character) = characters.next() {
        if character == '~' && !matches!(characters.next(), Some('0' | '1')) {
            return false;
        }
    }
    true
}
