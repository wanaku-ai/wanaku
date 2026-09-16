//! Scoped credential cache with expiry enforcement.
//!
//! Cache keys include every configured security dimension plus the binding
//! revision, so credentials are never shared across scopes and are invalidated
//! when a binding changes. Entries are never served past their expiry.

use std::collections::HashMap;
use std::sync::Mutex;

use time::OffsetDateTime;

use wanaku_types::credentials::binding::CredentialPurpose;
use wanaku_types::credentials::resolver::SecretRef;
use wanaku_types::credentials::secret::SecretMaterial;

/// A complete cache key covering all applicable security dimensions.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct CacheKey {
    /// Binding identifier.
    pub binding_id: String,
    /// Binding revision — a change invalidates prior entries.
    pub binding_revision: u64,
    /// The resolver scheme.
    pub resolver_scheme: String,
    /// The opaque secret reference.
    pub secret_ref: SecretRef,
    /// The credential purpose.
    pub purpose: CredentialPurpose,
    /// The stable forward identifier.
    pub forward_id: String,
    /// The normalized upstream origin.
    pub origin: String,
    /// Namespace scope, when the binding restricts by namespace.
    pub namespace: Option<String>,
    /// Identity scope, when the binding restricts by identity.
    pub identity: Option<String>,
    /// Governed item scope, when the binding restricts by governed item.
    pub governed_item: Option<String>,
    /// Operation scope, when the binding restricts by operation.
    pub operation: Option<String>,
}

struct CacheEntry {
    material: SecretMaterial,
    expires_at: OffsetDateTime,
    category: super::broker::ExpiryCategory,
}

/// An in-memory, expiry-aware credential cache.
#[derive(Default)]
pub struct CredentialCache {
    entries: Mutex<HashMap<CacheKey, CacheEntry>>,
}

impl CredentialCache {
    /// Create an empty cache.
    #[must_use]
    pub fn new() -> Self {
        Self {
            entries: Mutex::new(HashMap::new()),
        }
    }

    /// Fetch a cached credential if present and not expired. Expired entries are
    /// evicted so a credential is never served past its expiry.
    #[must_use]
    pub fn get(
        &self,
        key: &CacheKey,
        now: OffsetDateTime,
    ) -> Option<(SecretMaterial, super::broker::ExpiryCategory)> {
        let mut guard = self.entries.lock().ok()?;
        guard.retain(|_, entry| entry.expires_at > now);
        guard
            .get(key)
            .map(|entry| (entry.material.clone(), entry.category))
    }

    /// Insert a credential with an effective expiry.
    pub fn insert(
        &self,
        key: CacheKey,
        material: SecretMaterial,
        expires_at: OffsetDateTime,
        category: super::broker::ExpiryCategory,
    ) {
        if let Ok(mut guard) = self.entries.lock() {
            guard.insert(
                key,
                CacheEntry {
                    material,
                    expires_at,
                    category,
                },
            );
        }
    }

    /// Invalidate a single cache entry.
    pub fn invalidate(&self, key: &CacheKey) {
        if let Ok(mut guard) = self.entries.lock() {
            guard.remove(key);
        }
    }

    /// Invalidate every cached entry for a binding (e.g. on revision bump or
    /// forward address change).
    pub fn invalidate_binding(&self, binding_id: &str) {
        if let Ok(mut guard) = self.entries.lock() {
            guard.retain(|key, _| key.binding_id != binding_id);
        }
    }

    /// Invalidate every cached entry for a forward.
    pub fn invalidate_forward(&self, forward_id: &str) {
        if let Ok(mut guard) = self.entries.lock() {
            guard.retain(|key, _| key.forward_id != forward_id);
        }
    }

    /// The number of currently stored entries (for tests/metrics).
    #[must_use]
    pub fn len(&self) -> usize {
        self.entries.lock().map_or(0, |guard| guard.len())
    }

    /// Whether the cache is empty.
    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }
}

impl std::fmt::Debug for CredentialCache {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CredentialCache")
            .field("len", &self.len())
            .finish()
    }
}

/// Compute the effective expiry, honoring both resolver metadata and an
/// explicit binding TTL. Returns `None` when the credential must not be cached.
#[must_use]
pub fn effective_expiry(
    resolver_expiry: Option<OffsetDateTime>,
    max_ttl_seconds: Option<u64>,
    now: OffsetDateTime,
) -> Option<OffsetDateTime> {
    let ttl_expiry = max_ttl_seconds
        .and_then(|seconds| i64::try_from(seconds).ok())
        .and_then(|seconds| now.checked_add(time::Duration::seconds(seconds)));
    match (resolver_expiry, ttl_expiry) {
        (Some(a), Some(b)) => Some(a.min(b)),
        (Some(a), None) => Some(a),
        (None, Some(b)) => Some(b),
        // No resolver expiry and no explicit TTL: do not cache.
        (None, None) => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn key() -> CacheKey {
        CacheKey {
            binding_id: "b1".to_owned(),
            binding_revision: 1,
            resolver_scheme: "fake".to_owned(),
            secret_ref: SecretRef::parse("fake:token").unwrap(),
            purpose: CredentialPurpose::Invocation,
            forward_id: "fwd".to_owned(),
            origin: "https://api.example.com:443".to_owned(),
            namespace: None,
            identity: None,
            governed_item: None,
            operation: None,
        }
    }

    #[test]
    fn no_expiry_and_no_ttl_disables_caching() {
        let now = OffsetDateTime::UNIX_EPOCH;
        assert!(effective_expiry(None, None, now).is_none());
    }

    #[test]
    fn uses_earlier_of_resolver_and_ttl() {
        let now = OffsetDateTime::UNIX_EPOCH;
        let resolver = now + time::Duration::seconds(100);
        let effective = effective_expiry(Some(resolver), Some(10), now).unwrap();
        assert_eq!(effective, now + time::Duration::seconds(10));
    }

    #[test]
    fn does_not_serve_expired_entries() {
        let cache = CredentialCache::new();
        let now = OffsetDateTime::UNIX_EPOCH;
        cache.insert(
            key(),
            SecretMaterial::from("v"),
            now + time::Duration::seconds(5),
            super::super::broker::ExpiryCategory::BindingTtl,
        );
        assert!(
            cache
                .get(&key(), now + time::Duration::seconds(1))
                .is_some()
        );
        assert!(
            cache
                .get(&key(), now + time::Duration::seconds(10))
                .is_none()
        );
        // Expired entry evicted on access.
        assert!(cache.is_empty());
    }

    #[test]
    fn evicts_all_expired_entries_during_cache_access() {
        let cache = CredentialCache::new();
        let now = OffsetDateTime::UNIX_EPOCH;
        cache.insert(
            key(),
            SecretMaterial::from("expired"),
            now + time::Duration::seconds(5),
            super::super::broker::ExpiryCategory::BindingTtl,
        );
        let mut valid = key();
        valid.binding_revision = 2;
        cache.insert(
            valid.clone(),
            SecretMaterial::from("valid"),
            now + time::Duration::seconds(60),
            super::super::broker::ExpiryCategory::BindingTtl,
        );

        assert!(
            cache
                .get(&valid, now + time::Duration::seconds(10))
                .is_some()
        );
        assert_eq!(cache.len(), 1);
    }

    #[test]
    fn revision_bump_isolates_entries() {
        let cache = CredentialCache::new();
        let now = OffsetDateTime::UNIX_EPOCH;
        cache.insert(
            key(),
            SecretMaterial::from("v1"),
            now + time::Duration::seconds(60),
            super::super::broker::ExpiryCategory::BindingTtl,
        );
        let mut newer = key();
        newer.binding_revision = 2;
        assert!(cache.get(&newer, now).is_none());
        assert!(cache.get(&key(), now).is_some());
    }

    #[test]
    fn namespace_scope_isolates_entries() {
        let cache = CredentialCache::new();
        let now = OffsetDateTime::UNIX_EPOCH;
        let mut prod = key();
        prod.namespace = Some("prod".to_owned());
        cache.insert(
            prod,
            SecretMaterial::from("v"),
            now + time::Duration::seconds(60),
            super::super::broker::ExpiryCategory::BindingTtl,
        );
        let mut dev = key();
        dev.namespace = Some("dev".to_owned());
        assert!(cache.get(&dev, now).is_none());
    }

    #[test]
    fn invalidate_binding_clears_all_revisions() {
        let cache = CredentialCache::new();
        let now = OffsetDateTime::UNIX_EPOCH;
        cache.insert(
            key(),
            SecretMaterial::from("v"),
            now + time::Duration::seconds(60),
            super::super::broker::ExpiryCategory::BindingTtl,
        );
        cache.invalidate_binding("b1");
        assert!(cache.is_empty());
    }
}
