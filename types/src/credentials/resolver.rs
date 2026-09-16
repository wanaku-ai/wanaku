//! The [`SecretResolver`] abstraction, secret references, and resolution context.
//!
//! A resolver accepts an opaque [`SecretRef`] plus a bounded
//! [`ResolutionContext`] and returns ephemeral [`ResolvedSecret`] material. It
//! never receives complete tool arguments or request bodies. Unknown or
//! disabled resolver schemes fail closed.

use std::collections::HashMap;
use std::sync::Arc;

use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use time::OffsetDateTime;

use super::binding::CredentialPurpose;
use super::secret::SecretMaterial;

/// An opaque secret reference of the form `<scheme>:<path>`.
///
/// The scheme selects the resolver (e.g. `env`). The path is resolver-specific
/// and is never derived from request data.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(try_from = "String", into = "String")]
pub struct SecretRef {
    scheme: String,
    path: String,
}

/// Error parsing a [`SecretRef`].
#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum SecretRefError {
    /// The reference is missing a `<scheme>:` prefix.
    #[error("secret reference must be of the form '<scheme>:<path>'")]
    MissingScheme,
    /// The reference has an empty scheme or path.
    #[error("secret reference has an empty scheme or path")]
    Empty,
}

impl SecretRef {
    /// Parse an opaque secret reference of the form `<scheme>:<path>`.
    pub fn parse(value: &str) -> Result<Self, SecretRefError> {
        let (scheme, path) = value.split_once(':').ok_or(SecretRefError::MissingScheme)?;
        if scheme.is_empty() || path.is_empty() {
            return Err(SecretRefError::Empty);
        }
        Ok(Self {
            scheme: scheme.to_ascii_lowercase(),
            path: path.to_owned(),
        })
    }

    /// The resolver scheme (lower-cased), e.g. `env`.
    #[must_use]
    pub fn scheme(&self) -> &str {
        &self.scheme
    }

    /// The resolver-specific path portion.
    #[must_use]
    pub fn path(&self) -> &str {
        &self.path
    }
}

impl TryFrom<String> for SecretRef {
    type Error = SecretRefError;

    fn try_from(value: String) -> Result<Self, Self::Error> {
        Self::parse(&value)
    }
}

impl From<SecretRef> for String {
    fn from(value: SecretRef) -> Self {
        format!("{}:{}", value.scheme, value.path)
    }
}

impl std::fmt::Display for SecretRef {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}:{}", self.scheme, self.path)
    }
}

/// The bounded context passed to a resolver. It deliberately excludes complete
/// tool arguments and request bodies.
#[derive(Debug, Clone, Default)]
pub struct ResolutionContext {
    /// The resolved namespace.
    pub namespace: Option<String>,
    /// The governed item type (e.g. `tool`, `resource`, `prompt`).
    pub governed_item_type: Option<String>,
    /// The governed item identifier.
    pub governed_item_id: Option<String>,
    /// The stable forward identifier.
    pub forward_id: Option<String>,
    /// The normalized upstream origin string.
    pub upstream_origin: Option<String>,
    /// The MCP operation.
    pub operation: Option<String>,
    /// The authenticated actor or workload identity.
    pub identity: Option<String>,
    /// The correlation identifier.
    pub correlation_id: Option<String>,
    /// The credential purpose.
    pub purpose: Option<CredentialPurpose>,
}

/// Ephemeral resolved secret material plus optional expiry metadata.
pub struct ResolvedSecret {
    material: SecretMaterial,
    expires_at: Option<OffsetDateTime>,
}

impl ResolvedSecret {
    /// Create a resolved secret with no resolver-supplied expiry metadata.
    #[must_use]
    pub const fn without_expiry(material: SecretMaterial) -> Self {
        Self {
            material,
            expires_at: None,
        }
    }

    /// Create a resolved secret that expires at the given instant.
    #[must_use]
    pub const fn expiring_at(material: SecretMaterial, expires_at: OffsetDateTime) -> Self {
        Self {
            material,
            expires_at: Some(expires_at),
        }
    }

    /// Borrow the secret material.
    #[must_use]
    pub const fn material(&self) -> &SecretMaterial {
        &self.material
    }

    /// The resolver-supplied expiry, if any.
    #[must_use]
    pub const fn expires_at(&self) -> Option<OffsetDateTime> {
        self.expires_at
    }

    /// Consume the resolved secret, returning its material.
    #[must_use]
    pub fn into_material(self) -> SecretMaterial {
        self.material
    }
}

impl std::fmt::Debug for ResolvedSecret {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ResolvedSecret")
            .field("material", &"[REDACTED]")
            .field("expires_at", &self.expires_at)
            .finish()
    }
}

/// Errors returned by a resolver. These never contain secret material and map
/// to fail-closed audit reason codes.
#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum ResolverError {
    /// The requested scheme has no registered (or enabled) resolver.
    #[error("no resolver is registered for scheme '{0}'")]
    UnknownScheme(String),
    /// The referenced secret does not exist.
    #[error("referenced secret was not found")]
    NotFound,
    /// The reference is not valid for this resolver.
    #[error("secret reference is invalid for this resolver")]
    InvalidReference,
    /// The resolver is temporarily unavailable.
    #[error("resolver is unavailable")]
    Unavailable,
    /// The resolved value was malformed.
    #[error("resolved secret is malformed")]
    Malformed,
}

/// A pluggable resolver that turns an opaque [`SecretRef`] into ephemeral
/// credential material within a bounded [`ResolutionContext`].
#[async_trait]
pub trait SecretResolver: Send + Sync {
    /// The scheme this resolver handles (e.g. `env`).
    fn scheme(&self) -> &str;

    /// Resolve a secret reference. Implementations must not log the value and
    /// must apply strict timeouts and bounded response sizes.
    async fn resolve(
        &self,
        secret_ref: &SecretRef,
        context: &ResolutionContext,
    ) -> Result<ResolvedSecret, ResolverError>;
}

/// A registry of resolvers keyed by scheme. Dispatch fails closed for unknown
/// or disabled schemes.
#[derive(Clone, Default)]
pub struct ResolverRegistry {
    resolvers: HashMap<String, Arc<dyn SecretResolver>>,
}

impl ResolverRegistry {
    /// Create an empty registry.
    #[must_use]
    pub fn new() -> Self {
        Self {
            resolvers: HashMap::new(),
        }
    }

    /// Register a resolver under its declared scheme.
    #[must_use]
    pub fn with_resolver(mut self, resolver: Arc<dyn SecretResolver>) -> Self {
        self.resolvers
            .insert(resolver.scheme().to_ascii_lowercase(), resolver);
        self
    }

    /// Resolve a reference by dispatching to the resolver for its scheme.
    ///
    /// Returns [`ResolverError::UnknownScheme`] when no resolver is registered,
    /// so unknown/disabled schemes fail closed.
    pub async fn resolve(
        &self,
        secret_ref: &SecretRef,
        context: &ResolutionContext,
    ) -> Result<ResolvedSecret, ResolverError> {
        let resolver = self
            .resolvers
            .get(secret_ref.scheme())
            .ok_or_else(|| ResolverError::UnknownScheme(secret_ref.scheme().to_owned()))?;
        resolver.resolve(secret_ref, context).await
    }

    /// The set of registered resolver schemes (for health reporting).
    #[must_use]
    pub fn schemes(&self) -> Vec<String> {
        let mut schemes: Vec<String> = self.resolvers.keys().cloned().collect();
        schemes.sort();
        schemes
    }
}

impl std::fmt::Debug for ResolverRegistry {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ResolverRegistry")
            .field("schemes", &self.schemes())
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_scheme_and_path() {
        let r = SecretRef::parse("ENV:API_TOKEN").unwrap();
        assert_eq!(r.scheme(), "env");
        assert_eq!(r.path(), "API_TOKEN");
    }

    #[test]
    fn rejects_missing_scheme() {
        assert_eq!(
            SecretRef::parse("no-scheme"),
            Err(SecretRefError::MissingScheme)
        );
    }

    #[test]
    fn rejects_empty_parts() {
        assert_eq!(SecretRef::parse("env:"), Err(SecretRefError::Empty));
        assert_eq!(SecretRef::parse(":path"), Err(SecretRefError::Empty));
    }

    #[test]
    fn resolved_secret_debug_is_redacted() {
        let secret = ResolvedSecret::without_expiry(SecretMaterial::from("tok"));
        assert!(!format!("{secret:?}").contains("tok"));
    }

    #[test]
    fn unknown_scheme_fails_closed() {
        futures::executor::block_on(async {
            let registry = ResolverRegistry::new();
            let secret_ref = SecretRef::parse("vault:foo").unwrap();
            let err = registry
                .resolve(&secret_ref, &ResolutionContext::default())
                .await
                .unwrap_err();
            assert_eq!(err, ResolverError::UnknownScheme("vault".to_owned()));
        });
    }
}
