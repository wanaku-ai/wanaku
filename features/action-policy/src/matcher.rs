use thiserror::Error;

use crate::{MatchExpression, MatchKind};

/// Interface for compiled string matching strategies.
pub trait Matcher: Send + Sync {
    fn matches(&self, candidate: &str) -> bool;
}

/// A compiled matcher. Later strategies can implement [`Matcher`] without
/// changing policy evaluation.
pub struct CompiledMatcher {
    matcher: Box<dyn Matcher>,
}

impl CompiledMatcher {
    /// Wrap a custom matching strategy.
    #[must_use]
    pub fn new(matcher: impl Matcher + 'static) -> Self {
        Self {
            matcher: Box::new(matcher),
        }
    }

    pub(crate) fn compile(expression: &MatchExpression) -> Result<Self, MatcherCompileError> {
        let matcher: Box<dyn Matcher> = match expression.matcher {
            MatchKind::Exact => Box::new(ExactMatcher(expression.value.clone())),
            MatchKind::Prefix => Box::new(PrefixMatcher(expression.value.clone())),
            MatchKind::Glob => Box::new(GlobMatcher::compile(&expression.value)?),
        };
        Ok(Self { matcher })
    }

    #[must_use]
    pub fn matches(&self, candidate: &str) -> bool {
        self.matcher.matches(candidate)
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum MatcherCompileError {
    #[error("glob pattern has a trailing escape")]
    TrailingEscape,
}

struct ExactMatcher(String);

impl Matcher for ExactMatcher {
    fn matches(&self, candidate: &str) -> bool {
        candidate == self.0
    }
}

struct PrefixMatcher(String);

impl Matcher for PrefixMatcher {
    fn matches(&self, candidate: &str) -> bool {
        candidate.starts_with(&self.0)
    }
}

#[derive(Clone, Copy)]
enum GlobToken {
    AnySequence,
    AnyCharacter,
    Literal(char),
}

struct GlobMatcher(Vec<GlobToken>);

impl GlobMatcher {
    fn compile(pattern: &str) -> Result<Self, MatcherCompileError> {
        let mut tokens = Vec::new();
        let mut chars = pattern.chars();
        while let Some(character) = chars.next() {
            match character {
                '*' => tokens.push(GlobToken::AnySequence),
                '?' => tokens.push(GlobToken::AnyCharacter),
                '\\' => match chars.next() {
                    Some(literal) => tokens.push(GlobToken::Literal(literal)),
                    None => return Err(MatcherCompileError::TrailingEscape),
                },
                literal => tokens.push(GlobToken::Literal(literal)),
            }
        }
        Ok(Self(tokens))
    }
}

impl Matcher for GlobMatcher {
    fn matches(&self, candidate: &str) -> bool {
        let characters: Vec<char> = candidate.chars().collect();
        let mut previous = vec![false; characters.len() + 1];
        previous[0] = true;

        for token in &self.0 {
            let mut current = vec![false; characters.len() + 1];
            if matches!(token, GlobToken::AnySequence) {
                current[0] = previous[0];
            }
            for (index, character) in characters.iter().enumerate() {
                current[index + 1] = match token {
                    GlobToken::AnySequence => previous[index + 1] || current[index],
                    GlobToken::AnyCharacter => previous[index],
                    GlobToken::Literal(literal) => previous[index] && literal == character,
                };
            }
            previous = current;
        }

        previous[characters.len()]
    }
}
