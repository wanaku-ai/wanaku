//! An in-memory fake [`SecretResolver`] for tests and integration harnesses.

use std::collections::HashMap;

use async_trait::async_trait;
use time::OffsetDateTime;

use super::resolver::{
    ResolutionContext, ResolvedSecret, ResolverError, SecretRef, SecretResolver,
};
use super::secret::SecretMaterial;

/// A fake resolver backed by an in-memory map. Intended for tests only.
#[derive(Debug, Default)]
pub struct FakeResolver {
    scheme: String,
    values: HashMap<String, (String, Option<OffsetDateTime>)>,
}

impl FakeResolver {
    /// Create a fake resolver for the given scheme (default `fake`).
    #[must_use]
    pub fn new() -> Self {
        Self {
            scheme: "fake".to_owned(),
            values: HashMap::new(),
        }
    }

    /// Create a fake resolver for a custom scheme.
    #[must_use]
    pub fn for_scheme(scheme: &str) -> Self {
        Self {
            scheme: scheme.to_ascii_lowercase(),
            values: HashMap::new(),
        }
    }

    /// Insert a value that never expires, keyed by the reference path.
    #[must_use]
    pub fn with_value(mut self, path: &str, value: &str) -> Self {
        self.values
            .insert(path.to_owned(), (value.to_owned(), None));
        self
    }

    /// Insert a value with resolver-supplied expiry, keyed by the reference path.
    #[must_use]
    pub fn with_expiring_value(
        mut self,
        path: &str,
        value: &str,
        expires_at: OffsetDateTime,
    ) -> Self {
        self.values
            .insert(path.to_owned(), (value.to_owned(), Some(expires_at)));
        self
    }
}

#[async_trait]
impl SecretResolver for FakeResolver {
    fn scheme(&self) -> &str {
        &self.scheme
    }

    async fn resolve(
        &self,
        secret_ref: &SecretRef,
        _context: &ResolutionContext,
    ) -> Result<ResolvedSecret, ResolverError> {
        let (value, expiry) = self
            .values
            .get(secret_ref.path())
            .ok_or(ResolverError::NotFound)?;
        let material = SecretMaterial::from_string(value.clone());
        Ok(match expiry {
            Some(at) => ResolvedSecret::expiring_at(material, *at),
            None => ResolvedSecret::without_expiry(material),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolves_configured_value() {
        futures::executor::block_on(async {
            let resolver = FakeResolver::new().with_value("token", "abc");
            let secret_ref = SecretRef::parse("fake:token").unwrap();
            let resolved = resolver
                .resolve(&secret_ref, &ResolutionContext::default())
                .await
                .unwrap();
            assert_eq!(resolved.material().expose_str(), Some("abc"));
        });
    }

    #[test]
    fn missing_value_fails_closed() {
        futures::executor::block_on(async {
            let resolver = FakeResolver::new();
            let secret_ref = SecretRef::parse("fake:nope").unwrap();
            assert_eq!(
                resolver
                    .resolve(&secret_ref, &ResolutionContext::default())
                    .await
                    .unwrap_err(),
                ResolverError::NotFound
            );
        });
    }
}
