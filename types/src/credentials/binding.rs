//! Credential bindings: top-level resources owned by exactly one forward.
//!
//! A [`CredentialBinding`] ties a forward and an exact normalized upstream
//! origin to one or more opaque secret references, an injection mechanism, and
//! the policy dimensions that constrain credential use. Tools, resources, and
//! prompts never carry secret or binding references; they inherit the
//! invocation binding through their `forwardId`.

use std::str::FromStr;

use serde::{Deserialize, Serialize};

use super::injection::{InjectionError, InjectionMechanism};
use super::resolver::SecretRef;

/// The purpose a credential is used for. Discovery and invocation are kept
/// distinct in policy checks, resolution context, audit events, and cache keys.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "snake_case")]
pub enum CredentialPurpose {
    /// Authenticated forward discovery and refresh.
    Discovery,
    /// Authenticated tool calls, resource reads, and prompt retrieval.
    Invocation,
}

impl std::fmt::Display for CredentialPurpose {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Discovery => f.write_str("discovery"),
            Self::Invocation => f.write_str("invocation"),
        }
    }
}

/// An exact, normalized upstream origin (scheme + host + port).
///
/// Normalization lower-cases the scheme and host and makes the port explicit,
/// so that bindings can be matched exactly and credentials are never sent to a
/// different origin because of address-normalization ambiguity.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct NormalizedOrigin(String);

/// Error normalizing an upstream address into an origin.
#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum OriginError {
    /// The address could not be parsed.
    #[error("invalid upstream address")]
    InvalidAddress,
    /// The address is missing a scheme (only http/https are supported).
    #[error("upstream address is missing an http(s) scheme")]
    MissingScheme,
    /// The address is missing a host.
    #[error("upstream address is missing a host")]
    MissingHost,
}

impl NormalizedOrigin {
    /// Parse and normalize an upstream address into an exact origin.
    pub fn from_address(address: &str) -> Result<Self, OriginError> {
        let uri = http::Uri::from_str(address).map_err(|_| OriginError::InvalidAddress)?;
        let scheme = uri.scheme_str().ok_or(OriginError::MissingScheme)?.to_ascii_lowercase();
        if scheme != "http" && scheme != "https" {
            return Err(OriginError::MissingScheme);
        }
        let host = uri.host().ok_or(OriginError::MissingHost)?.to_ascii_lowercase();
        if host.is_empty() {
            return Err(OriginError::MissingHost);
        }
        let port = uri
            .port_u16()
            .unwrap_or(if scheme == "https" { 443 } else { 80 });
        Ok(Self(format!("{scheme}://{host}:{port}")))
    }

    /// The normalized origin string, e.g. `https://api.example.com:443`.
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }

    /// Whether the given address resolves to exactly this origin.
    #[must_use]
    pub fn matches_address(&self, address: &str) -> bool {
        Self::from_address(address).is_ok_and(|other| other == *self)
    }
}

impl std::fmt::Display for NormalizedOrigin {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.0)
    }
}

/// Optional restrictions that further constrain where a binding may be used.
/// An empty vector means "no restriction on this dimension".
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct BindingRestrictions {
    /// Namespaces in which this binding may be used.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub namespaces: Vec<String>,
    /// Governed item identifiers this binding may be used for.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub governed_items: Vec<String>,
    /// MCP operations this binding may be used for.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub operations: Vec<String>,
    /// Authenticated identities this binding may be used for.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub identities: Vec<String>,
}

impl BindingRestrictions {
    fn allows(list: &[String], candidate: Option<&str>) -> bool {
        if list.is_empty() {
            return true;
        }
        candidate.is_some_and(|value| list.iter().any(|allowed| allowed == value))
    }
}

/// Cache rules for a binding's resolved credentials.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct CacheRules {
    /// An explicit bounded maximum TTL, in seconds. When absent and the
    /// resolver supplies no expiry metadata, results are not cached.
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "maxTtlSeconds")]
    pub max_ttl_seconds: Option<u64>,
}

/// A credential binding: a top-level resource operationally owned by one forward.
///
/// The binding holds only **opaque** secret references. It never stores inline
/// secret values, and management APIs expose only its non-secret metadata.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct CredentialBinding {
    /// Stable, non-secret binding identifier.
    pub id: String,
    /// The owning forward's stable identifier (the forward name).
    #[serde(rename = "forwardId", alias = "forward_id")]
    pub forward_id: String,
    /// The exact normalized upstream origin this binding may target.
    pub origin: NormalizedOrigin,
    /// The outbound injection mechanism.
    pub mechanism: InjectionMechanism,
    /// One or more opaque secret references resolved just in time.
    #[serde(rename = "secretRefs", alias = "secret_refs")]
    pub secret_refs: Vec<SecretRef>,
    /// The credential purposes this binding is allowed to serve.
    #[serde(rename = "allowedPurposes", alias = "allowed_purposes")]
    pub allowed_purposes: Vec<CredentialPurpose>,
    /// Optional additional restrictions.
    #[serde(default)]
    pub restrictions: BindingRestrictions,
    /// Cache rules for resolved credentials.
    #[serde(default)]
    pub cache: CacheRules,
    /// Monotonic binding revision. Any change to the binding must bump this so
    /// cached credentials for the old revision are never reused.
    #[serde(default)]
    pub revision: u64,
}

/// The reasons a binding may reject a resolution request. Distinct variants
/// support fail-closed audit reason codes without exposing secret material.
#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum BindingError {
    /// The resolved/target forward does not own this binding.
    #[error("binding is not owned by the requesting forward")]
    ForwardMismatch,
    /// The upstream origin does not match the binding's exact origin.
    #[error("upstream origin does not match the binding origin")]
    OriginMismatch,
    /// The requested purpose is not permitted by the binding.
    #[error("credential purpose is not permitted by the binding")]
    PurposeNotAllowed,
    /// A configured restriction (namespace/item/operation/identity) rejected use.
    #[error("binding restriction rejected the request")]
    RestrictionViolation,
    /// The configured injection mechanism is invalid.
    #[error("invalid injection configuration: {0}")]
    InvalidInjection(#[from] InjectionError),
    /// The number of secret references does not match the injection mechanism.
    #[error("binding secret references do not match the injection mechanism")]
    SecretArityMismatch,
}

impl CredentialBinding {
    /// Validate the binding's static structure at configuration-activation time.
    pub fn validate(&self) -> Result<(), BindingError> {
        self.mechanism.validate()?;
        let required = match self.mechanism {
            InjectionMechanism::Basic => 2,
            InjectionMechanism::Bearer | InjectionMechanism::NamedHeader { .. } => 1,
        };
        if self.secret_refs.len() != required {
            return Err(BindingError::SecretArityMismatch);
        }
        Ok(())
    }

    /// Enforce that this binding may be used for the given request scope.
    ///
    /// This is the fail-closed scope gate that runs *before* any secret is
    /// resolved. It never inspects secret material.
    pub fn authorize_use(
        &self,
        forward_id: &str,
        address: &str,
        purpose: CredentialPurpose,
        scope: &UseScope<'_>,
    ) -> Result<(), BindingError> {
        if self.forward_id != forward_id {
            return Err(BindingError::ForwardMismatch);
        }
        if !self.origin.matches_address(address) {
            return Err(BindingError::OriginMismatch);
        }
        if !self.allowed_purposes.contains(&purpose) {
            return Err(BindingError::PurposeNotAllowed);
        }
        let r = &self.restrictions;
        if !BindingRestrictions::allows(&r.namespaces, scope.namespace)
            || !BindingRestrictions::allows(&r.governed_items, scope.governed_item)
            || !BindingRestrictions::allows(&r.operations, scope.operation)
            || !BindingRestrictions::allows(&r.identities, scope.identity)
        {
            return Err(BindingError::RestrictionViolation);
        }
        Ok(())
    }
}

/// The scope dimensions checked against a binding's restrictions.
#[derive(Debug, Clone, Copy, Default)]
pub struct UseScope<'a> {
    /// The resolved namespace.
    pub namespace: Option<&'a str>,
    /// The governed item identifier.
    pub governed_item: Option<&'a str>,
    /// The MCP operation.
    pub operation: Option<&'a str>,
    /// The authenticated actor or workload identity.
    pub identity: Option<&'a str>,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn binding() -> CredentialBinding {
        CredentialBinding {
            id: "b1".to_owned(),
            forward_id: "fwd-a".to_owned(),
            origin: NormalizedOrigin::from_address("https://api.example.com").unwrap(),
            mechanism: InjectionMechanism::Bearer,
            secret_refs: vec![SecretRef::parse("env:API_TOKEN").unwrap()],
            allowed_purposes: vec![CredentialPurpose::Invocation],
            restrictions: BindingRestrictions::default(),
            cache: CacheRules::default(),
            revision: 1,
        }
    }

    #[test]
    fn origin_normalizes_default_https_port() {
        let o = NormalizedOrigin::from_address("https://API.Example.com/path?x=1").unwrap();
        assert_eq!(o.as_str(), "https://api.example.com:443");
    }

    #[test]
    fn origin_normalizes_explicit_port() {
        let o = NormalizedOrigin::from_address("http://host:8080").unwrap();
        assert_eq!(o.as_str(), "http://host:8080");
    }

    #[test]
    fn origin_rejects_non_http_scheme() {
        assert_eq!(
            NormalizedOrigin::from_address("ftp://host"),
            Err(OriginError::MissingScheme)
        );
    }

    #[test]
    fn origin_exact_match() {
        let o = NormalizedOrigin::from_address("https://api.example.com").unwrap();
        assert!(o.matches_address("https://api.example.com/anything"));
        assert!(!o.matches_address("https://api.example.com:8443"));
        assert!(!o.matches_address("https://evil.example.com"));
    }

    #[test]
    fn validate_checks_secret_arity() {
        let mut b = binding();
        b.mechanism = InjectionMechanism::Basic;
        assert_eq!(b.validate(), Err(BindingError::SecretArityMismatch));
        b.secret_refs.push(SecretRef::parse("env:PASSWORD").unwrap());
        assert!(b.validate().is_ok());
    }

    #[test]
    fn authorize_rejects_wrong_forward() {
        let b = binding();
        let err = b
            .authorize_use("fwd-b", "https://api.example.com", CredentialPurpose::Invocation, &UseScope::default())
            .unwrap_err();
        assert_eq!(err, BindingError::ForwardMismatch);
    }

    #[test]
    fn authorize_rejects_wrong_origin() {
        let b = binding();
        let err = b
            .authorize_use("fwd-a", "https://evil.example.com", CredentialPurpose::Invocation, &UseScope::default())
            .unwrap_err();
        assert_eq!(err, BindingError::OriginMismatch);
    }

    #[test]
    fn authorize_rejects_wrong_purpose() {
        let b = binding();
        let err = b
            .authorize_use("fwd-a", "https://api.example.com", CredentialPurpose::Discovery, &UseScope::default())
            .unwrap_err();
        assert_eq!(err, BindingError::PurposeNotAllowed);
    }

    #[test]
    fn authorize_enforces_namespace_restriction() {
        let mut b = binding();
        b.restrictions.namespaces = vec!["prod".to_owned()];
        let scope = UseScope { namespace: Some("dev"), ..UseScope::default() };
        let err = b
            .authorize_use("fwd-a", "https://api.example.com", CredentialPurpose::Invocation, &scope)
            .unwrap_err();
        assert_eq!(err, BindingError::RestrictionViolation);

        let ok_scope = UseScope { namespace: Some("prod"), ..UseScope::default() };
        assert!(b
            .authorize_use("fwd-a", "https://api.example.com", CredentialPurpose::Invocation, &ok_scope)
            .is_ok());
    }
}
