//! The credential broker: the single just-in-time resolution + injection path.
//!
//! The broker enforces scope, resolves opaque secret references through the
//! resolver registry (with a single-flight guard per complete cache key),
//! honors expiry/TTL caching rules, and produces redacted audit metadata. It is
//! the one path used by discovery, refresh, tool calls, resource reads, and
//! prompt retrieval.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use time::OffsetDateTime;

use super::cache::{CacheKey, CredentialCache, effective_expiry};
use wanaku_types::credentials::binding::{
    BindingError, CredentialBinding, CredentialPurpose, UseScope,
};
use wanaku_types::credentials::injection::{CredentialHeader, InjectionError};
use wanaku_types::credentials::resolver::{
    ResolutionContext, ResolverError, ResolverRegistry, SecretRef,
};
use wanaku_types::credentials::secret::SecretMaterial;

/// The categorized outcome of a brokerage attempt (for audit; no secrets).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ResolutionOutcome {
    /// Credential(s) served from cache.
    CacheHit,
    /// Credential(s) freshly resolved.
    Resolved,
    /// The request was denied by scope enforcement before resolution.
    Denied,
    /// Resolution or injection failed.
    Failed,
}

impl ResolutionOutcome {
    /// A stable, non-secret string form for audit records.
    #[must_use]
    pub const fn as_str(&self) -> &'static str {
        match self {
            Self::CacheHit => "cache_hit",
            Self::Resolved => "resolved",
            Self::Denied => "denied",
            Self::Failed => "failed",
        }
    }
}

/// The category of expiry that governed caching (for audit; no secrets).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExpiryCategory {
    /// Not cached (no resolver expiry and no binding TTL).
    NotCached,
    /// Cached using resolver-supplied expiry.
    ResolverProvided,
    /// Cached using an explicit binding TTL.
    BindingTtl,
    /// Cached using the earlier of resolver expiry and binding TTL.
    Earliest,
}

impl ExpiryCategory {
    /// A stable, non-secret string form for audit records.
    #[must_use]
    pub const fn as_str(&self) -> &'static str {
        match self {
            Self::NotCached => "not_cached",
            Self::ResolverProvided => "resolver_provided",
            Self::BindingTtl => "binding_ttl",
            Self::Earliest => "earliest",
        }
    }
}

/// Redacted, secret-free metadata about a brokerage attempt, suitable for audit
/// events, structured logs, and metrics. It never contains a secret reference
/// path or a resolved credential value.
#[derive(Debug, Clone)]
pub struct CredentialAuditRecord {
    /// Credential binding identifier.
    pub binding_id: String,
    /// Binding revision.
    pub binding_revision: u64,
    /// Resolver types (schemes) involved.
    pub resolver_types: Vec<String>,
    /// The resolution outcome.
    pub outcome: ResolutionOutcome,
    /// The injection mechanism (type only).
    pub mechanism: String,
    /// The forward identifier.
    pub forward_id: String,
    /// The normalized upstream origin.
    pub upstream_origin: String,
    /// The credential purpose.
    pub purpose: CredentialPurpose,
    /// The expiry category that governed caching.
    pub expiry_category: ExpiryCategory,
    /// A stable failure reason code, when the outcome was denied or failed.
    pub failure_reason: Option<String>,
}

/// A successfully brokered credential: injectable headers plus audit metadata.
pub struct BrokeredCredential {
    /// The credential header(s) to inject at the transport boundary.
    pub headers: Vec<CredentialHeader>,
    /// Redacted audit metadata.
    pub audit: CredentialAuditRecord,
}

impl std::fmt::Debug for BrokeredCredential {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("BrokeredCredential")
            .field("headers", &self.headers)
            .field("audit", &self.audit)
            .finish()
    }
}

/// The categorized cause of a brokerage failure.
#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum BrokerErrorKind {
    /// Scope enforcement rejected the request before resolution.
    #[error(transparent)]
    Binding(#[from] BindingError),
    /// A resolver failed.
    #[error(transparent)]
    Resolver(#[from] ResolverError),
    /// Injection construction failed.
    #[error(transparent)]
    Injection(#[from] InjectionError),
}

impl BrokerErrorKind {
    /// A stable, non-secret failure reason code for audit records.
    #[must_use]
    pub fn reason_code(&self) -> String {
        match self {
            Self::Binding(BindingError::ForwardMismatch) => "forward_mismatch",
            Self::Binding(BindingError::OriginMismatch) => "origin_mismatch",
            Self::Binding(BindingError::PurposeNotAllowed) => "purpose_not_allowed",
            Self::Binding(BindingError::RestrictionViolation) => "restriction_violation",
            Self::Binding(BindingError::InvalidInjection(_)) => "invalid_injection",
            Self::Binding(BindingError::SecretArityMismatch) => "secret_arity_mismatch",
            Self::Resolver(ResolverError::UnknownScheme(_)) => "unknown_resolver_scheme",
            Self::Resolver(ResolverError::NotFound) => "secret_not_found",
            Self::Resolver(ResolverError::InvalidReference) => "invalid_secret_reference",
            Self::Resolver(ResolverError::Unavailable) => "resolver_unavailable",
            Self::Resolver(ResolverError::Malformed) => "secret_malformed",
            Self::Injection(_) => "injection_failure",
        }
        .to_owned()
    }
}

/// A brokerage failure carrying redacted audit metadata.
#[derive(Debug, thiserror::Error)]
#[error("credential brokerage failed: {kind}")]
pub struct BrokerError {
    /// The categorized cause.
    pub kind: BrokerErrorKind,
    /// Redacted audit metadata describing the failed attempt.
    pub audit: CredentialAuditRecord,
}

/// The just-in-time credential broker.
pub struct CredentialBroker {
    resolvers: ResolverRegistry,
    cache: CredentialCache,
    inflight: Mutex<HashMap<CacheKey, Arc<tokio::sync::Mutex<()>>>>,
}

impl CredentialBroker {
    /// Create a broker over the given resolver registry.
    #[must_use]
    pub fn new(resolvers: ResolverRegistry) -> Self {
        Self {
            resolvers,
            cache: CredentialCache::new(),
            inflight: Mutex::new(HashMap::new()),
        }
    }

    /// Access the underlying cache (for invalidation on forward/binding change).
    #[must_use]
    pub const fn cache(&self) -> &CredentialCache {
        &self.cache
    }

    /// Resolve and inject credentials for a binding, enforcing scope, expiry,
    /// and single-flight resolution. This is the single brokerage path.
    ///
    /// `forward_id` and `address` are the *already resolved and validated*
    /// stable forward identity and final upstream address. Callers must invoke
    /// this only after governance authorizes the action.
    pub async fn broker(
        &self,
        binding: &CredentialBinding,
        forward_id: &str,
        address: &str,
        purpose: CredentialPurpose,
        scope: &UseScope<'_>,
    ) -> Result<BrokeredCredential, BrokerError> {
        let now = OffsetDateTime::now_utc();
        let origin = binding.origin.as_str().to_owned();

        // Fail-closed scope enforcement runs before any secret is resolved.
        if let Err(e) = binding.validate() {
            return Err(Self::fail(
                binding,
                purpose,
                &origin,
                ResolutionOutcome::Failed,
                e.into(),
            ));
        }
        if let Err(e) = binding.authorize_use(forward_id, address, purpose, scope) {
            return Err(Self::fail(
                binding,
                purpose,
                &origin,
                ResolutionOutcome::Denied,
                e.into(),
            ));
        }
        let context = ResolutionContext {
            namespace: scope.namespace.map(str::to_owned),
            governed_item_type: None,
            governed_item_id: scope.governed_item.map(str::to_owned),
            forward_id: Some(forward_id.to_owned()),
            upstream_origin: Some(origin.clone()),
            operation: scope.operation.map(str::to_owned),
            identity: scope.identity.map(str::to_owned),
            correlation_id: None,
            purpose: Some(purpose),
        };

        let mut materials: Vec<SecretMaterial> = Vec::with_capacity(binding.secret_refs.len());
        let mut resolver_types: Vec<String> = Vec::new();
        let mut any_fresh = false;
        let mut expiry_category = ExpiryCategory::NotCached;

        for secret_ref in &binding.secret_refs {
            resolver_types.push(secret_ref.scheme().to_owned());
            let key = build_cache_key(binding, purpose, forward_id, &origin, secret_ref, scope);

            match self
                .resolve_one(binding, secret_ref, &key, &context, now)
                .await
            {
                Ok(Resolved {
                    material,
                    fresh,
                    category,
                }) => {
                    if fresh {
                        any_fresh = true;
                    }
                    if category != ExpiryCategory::NotCached {
                        expiry_category = category;
                    }
                    materials.push(material);
                }
                Err(kind) => {
                    return Err(Self::fail(
                        binding,
                        purpose,
                        &origin,
                        ResolutionOutcome::Failed,
                        kind,
                    ));
                }
            }
        }

        let headers = match binding.mechanism.build_headers(&materials) {
            Ok(h) => h,
            Err(e) => {
                return Err(Self::fail(
                    binding,
                    purpose,
                    &origin,
                    ResolutionOutcome::Failed,
                    e.into(),
                ));
            }
        };

        let outcome = if any_fresh {
            ResolutionOutcome::Resolved
        } else {
            ResolutionOutcome::CacheHit
        };
        Ok(BrokeredCredential {
            headers,
            audit: CredentialAuditRecord {
                binding_id: binding.id.clone(),
                binding_revision: binding.revision,
                resolver_types,
                outcome,
                mechanism: mechanism_name(binding),
                forward_id: forward_id.to_owned(),
                upstream_origin: origin,
                purpose,
                expiry_category,
                failure_reason: None,
            },
        })
    }

    async fn resolve_one(
        &self,
        binding: &CredentialBinding,
        secret_ref: &SecretRef,
        key: &CacheKey,
        context: &ResolutionContext,
        now: OffsetDateTime,
    ) -> Result<Resolved, BrokerErrorKind> {
        if let Some((material, category)) = self.cache.get(key, now) {
            return Ok(Resolved {
                material,
                fresh: false,
                category,
            });
        }

        // Single-flight: serialize concurrent resolution for this exact key.
        let lock = self.inflight_lock(key);
        let guard = lock.lock().await;

        // Re-check the cache now that we hold the single-flight lock.
        if let Some((material, category)) = self.cache.get(key, now) {
            drop(guard);
            self.remove_inflight_lock(key, &lock);
            return Ok(Resolved {
                material,
                fresh: false,
                category,
            });
        }

        let resolution = self.resolvers.resolve(secret_ref, context).await;
        let result = match resolution {
            Ok(resolved) => {
                let expiry =
                    effective_expiry(resolved.expires_at(), binding.cache.max_ttl_seconds, now);
                let category =
                    classify_expiry(resolved.expires_at(), binding.cache.max_ttl_seconds);
                let material = resolved.into_material();
                if let Some(expires_at) = expiry {
                    self.cache
                        .insert(key.clone(), material.clone(), expires_at, category);
                }
                Ok(Resolved {
                    material,
                    fresh: true,
                    category,
                })
            }
            Err(error) => Err(error.into()),
        };
        drop(guard);
        self.remove_inflight_lock(key, &lock);
        result
    }

    fn inflight_lock(&self, key: &CacheKey) -> Arc<tokio::sync::Mutex<()>> {
        let mut guard = match self.inflight.lock() {
            Ok(g) => g,
            Err(poisoned) => poisoned.into_inner(),
        };
        guard
            .entry(key.clone())
            .or_insert_with(|| Arc::new(tokio::sync::Mutex::new(())))
            .clone()
    }

    fn remove_inflight_lock(&self, key: &CacheKey, lock: &Arc<tokio::sync::Mutex<()>>) {
        let mut guard = match self.inflight.lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        };
        if guard
            .get(key)
            .is_some_and(|current| Arc::ptr_eq(current, lock) && Arc::strong_count(current) == 2)
        {
            guard.remove(key);
        }
    }

    fn fail(
        binding: &CredentialBinding,
        purpose: CredentialPurpose,
        origin: &str,
        outcome: ResolutionOutcome,
        kind: BrokerErrorKind,
    ) -> BrokerError {
        let audit = CredentialAuditRecord {
            binding_id: binding.id.clone(),
            binding_revision: binding.revision,
            resolver_types: binding
                .secret_refs
                .iter()
                .map(|r| r.scheme().to_owned())
                .collect(),
            outcome,
            mechanism: mechanism_name(binding),
            forward_id: binding.forward_id.clone(),
            upstream_origin: origin.to_owned(),
            purpose,
            expiry_category: ExpiryCategory::NotCached,
            failure_reason: Some(kind.reason_code()),
        };
        BrokerError { kind, audit }
    }
}

impl std::fmt::Debug for CredentialBroker {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CredentialBroker")
            .field("resolvers", &self.resolvers)
            .field("cache", &self.cache)
            .finish()
    }
}

struct Resolved {
    material: SecretMaterial,
    fresh: bool,
    category: ExpiryCategory,
}

const fn classify_expiry(
    resolver_expiry: Option<OffsetDateTime>,
    max_ttl_seconds: Option<u64>,
) -> ExpiryCategory {
    match (resolver_expiry, max_ttl_seconds) {
        (Some(_), Some(_)) => ExpiryCategory::Earliest,
        (Some(_), None) => ExpiryCategory::ResolverProvided,
        (None, Some(_)) => ExpiryCategory::BindingTtl,
        (None, None) => ExpiryCategory::NotCached,
    }
}

fn mechanism_name(binding: &CredentialBinding) -> String {
    match binding.mechanism {
        wanaku_types::credentials::injection::InjectionMechanism::Bearer => "bearer",
        wanaku_types::credentials::injection::InjectionMechanism::NamedHeader { .. } => {
            "named_header"
        }
        wanaku_types::credentials::injection::InjectionMechanism::Basic => "basic",
    }
    .to_owned()
}

fn build_cache_key(
    binding: &CredentialBinding,
    purpose: CredentialPurpose,
    forward_id: &str,
    origin: &str,
    secret_ref: &SecretRef,
    scope: &UseScope<'_>,
) -> CacheKey {
    CacheKey {
        binding_id: binding.id.clone(),
        binding_revision: binding.revision,
        resolver_scheme: secret_ref.scheme().to_owned(),
        secret_ref: secret_ref.clone(),
        purpose,
        forward_id: forward_id.to_owned(),
        origin: origin.to_owned(),
        // Resolvers receive this complete context. Every caller-supplied scope
        // dimension is therefore part of the cache boundary, even when the
        // binding does not restrict that dimension itself.
        namespace: scope.namespace.map(str::to_owned),
        identity: scope.identity.map(str::to_owned),
        governed_item: scope.governed_item.map(str::to_owned),
        operation: scope.operation.map(str::to_owned),
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    use super::*;
    use wanaku_types::credentials::binding::NormalizedOrigin;
    use wanaku_types::credentials::fake_resolver::FakeResolver;
    use wanaku_types::credentials::injection::InjectionMechanism;

    fn registry() -> ResolverRegistry {
        ResolverRegistry::new()
            .with_resolver(Arc::new(FakeResolver::new().with_value("token", "s3cr3t")))
    }

    fn binding() -> CredentialBinding {
        CredentialBinding {
            id: "b1".to_owned(),
            forward_id: "fwd-a".to_owned(),
            origin: NormalizedOrigin::from_address("https://api.example.com").unwrap(),
            mechanism: InjectionMechanism::Bearer,
            secret_refs: vec![SecretRef::parse("fake:token").unwrap()],
            allowed_purposes: vec![CredentialPurpose::Invocation],
            restrictions: wanaku_types::credentials::binding::BindingRestrictions::default(),
            cache: wanaku_types::credentials::binding::CacheRules::default(),
            revision: 1,
        }
    }

    #[tokio::test]
    async fn brokers_bearer_header() {
        let broker = CredentialBroker::new(registry());
        let out = broker
            .broker(
                &binding(),
                "fwd-a",
                "https://api.example.com/mcp",
                CredentialPurpose::Invocation,
                &UseScope::default(),
            )
            .await
            .unwrap();
        assert_eq!(out.headers[0].name(), "authorization");
        assert_eq!(
            out.headers[0].expose_value().expose_str(),
            Some("Bearer s3cr3t")
        );
        assert_eq!(out.audit.outcome, ResolutionOutcome::Resolved);
        assert_eq!(out.audit.failure_reason, None);
    }

    #[tokio::test]
    async fn does_not_cache_without_expiry_or_ttl() {
        let broker = CredentialBroker::new(registry());
        broker
            .broker(
                &binding(),
                "fwd-a",
                "https://api.example.com",
                CredentialPurpose::Invocation,
                &UseScope::default(),
            )
            .await
            .unwrap();
        assert!(broker.cache().is_empty());
    }

    #[tokio::test]
    async fn caches_with_binding_ttl() {
        let mut b = binding();
        b.cache.max_ttl_seconds = Some(60);
        let broker = CredentialBroker::new(registry());
        let first = broker
            .broker(
                &b,
                "fwd-a",
                "https://api.example.com",
                CredentialPurpose::Invocation,
                &UseScope::default(),
            )
            .await
            .unwrap();
        assert_eq!(first.audit.outcome, ResolutionOutcome::Resolved);
        assert_eq!(broker.cache().len(), 1);
        let second = broker
            .broker(
                &b,
                "fwd-a",
                "https://api.example.com",
                CredentialPurpose::Invocation,
                &UseScope::default(),
            )
            .await
            .unwrap();
        assert_eq!(second.audit.outcome, ResolutionOutcome::CacheHit);
        assert_eq!(second.audit.expiry_category, ExpiryCategory::BindingTtl);
        assert!(!format!("{:?}", second.audit).contains("fake:token"));
        assert!(broker.inflight.lock().is_ok_and(|locks| locks.is_empty()));
    }

    #[tokio::test]
    async fn denies_before_resolution_on_origin_mismatch() {
        let broker = CredentialBroker::new(registry());
        let err = broker
            .broker(
                &binding(),
                "fwd-a",
                "https://evil.example.com",
                CredentialPurpose::Invocation,
                &UseScope::default(),
            )
            .await
            .unwrap_err();
        assert_eq!(err.audit.outcome, ResolutionOutcome::Denied);
        assert_eq!(err.audit.failure_reason.as_deref(), Some("origin_mismatch"));
        assert!(broker.cache().is_empty());
    }

    #[tokio::test]
    async fn unknown_scheme_fails_closed() {
        let mut b = binding();
        b.secret_refs = vec![SecretRef::parse("vault:token").unwrap()];
        let broker = CredentialBroker::new(registry());
        let err = broker
            .broker(
                &b,
                "fwd-a",
                "https://api.example.com",
                CredentialPurpose::Invocation,
                &UseScope::default(),
            )
            .await
            .unwrap_err();
        assert_eq!(
            err.audit.failure_reason.as_deref(),
            Some("unknown_resolver_scheme")
        );
    }

    #[test]
    fn cache_key_includes_unrestricted_resolver_context() {
        let binding = binding();
        let reference = SecretRef::parse("fake:token").unwrap();
        let prod = build_cache_key(
            &binding,
            CredentialPurpose::Invocation,
            "fwd-a",
            binding.origin.as_str(),
            &reference,
            &UseScope {
                namespace: Some("prod"),
                ..UseScope::default()
            },
        );
        let dev = build_cache_key(
            &binding,
            CredentialPurpose::Invocation,
            "fwd-a",
            binding.origin.as_str(),
            &reference,
            &UseScope {
                namespace: Some("dev"),
                ..UseScope::default()
            },
        );
        assert_ne!(prod, dev);
    }
}
