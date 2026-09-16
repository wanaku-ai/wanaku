//! A narrowly scoped `env:` [`SecretResolver`].
//!
//! Only configured bindings can name environment variables; request data can
//! never select one. The resolver optionally restricts which variable names may
//! be read via an allowlist, and always fails closed for missing variables.

use async_trait::async_trait;

use super::resolver::{
    ResolutionContext, ResolvedSecret, ResolverError, SecretRef, SecretResolver,
};
use super::secret::SecretMaterial;

/// Resolves `env:VAR_NAME` references from the process environment.
#[derive(Debug, Default)]
pub struct EnvResolver {
    /// When non-empty, only these variable names may be resolved.
    allowlist: Option<Vec<String>>,
}

impl EnvResolver {
    /// Create a resolver that may read any environment variable named by a
    /// binding. (Bindings are operator-controlled; request data cannot select
    /// variables.)
    #[must_use]
    pub const fn new() -> Self {
        Self { allowlist: None }
    }

    /// Create a resolver that may only read the given variable names.
    #[must_use]
    pub const fn with_allowlist(names: Vec<String>) -> Self {
        Self {
            allowlist: Some(names),
        }
    }

    fn is_allowed(&self, name: &str) -> bool {
        match &self.allowlist {
            None => true,
            Some(list) => list.iter().any(|allowed| allowed == name),
        }
    }
}

#[async_trait]
impl SecretResolver for EnvResolver {
    fn scheme(&self) -> &str {
        "env"
    }

    async fn resolve(
        &self,
        secret_ref: &SecretRef,
        _context: &ResolutionContext,
    ) -> Result<ResolvedSecret, ResolverError> {
        let name = secret_ref.path();
        // Reject anything that is not a plausible environment variable name so
        // a misconfigured binding cannot smuggle shell-like expressions.
        if name.is_empty() || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_') {
            return Err(ResolverError::InvalidReference);
        }
        if !self.is_allowed(name) {
            return Err(ResolverError::InvalidReference);
        }
        match std::env::var(name) {
            Ok(value) if !value.is_empty() => Ok(ResolvedSecret::without_expiry(
                SecretMaterial::from_string(value),
            )),
            Ok(_) => Err(ResolverError::Malformed),
            Err(_) => Err(ResolverError::NotFound),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    #[allow(unsafe_code)]
    fn resolves_present_variable() {
        futures::executor::block_on(async {
            // SAFETY: this test uses a unique variable name and restores it.
            unsafe { std::env::set_var("WANAKU_TEST_ENV_RESOLVER", "topsecret") };
            let resolver = EnvResolver::new();
            let secret_ref = SecretRef::parse("env:WANAKU_TEST_ENV_RESOLVER").unwrap();
            let resolved = resolver
                .resolve(&secret_ref, &ResolutionContext::default())
                .await
                .unwrap();
            assert_eq!(resolved.material().expose_str(), Some("topsecret"));
            assert!(resolved.expires_at().is_none());
            unsafe { std::env::remove_var("WANAKU_TEST_ENV_RESOLVER") };
        });
    }

    #[test]
    fn missing_variable_fails_closed() {
        futures::executor::block_on(async {
            let resolver = EnvResolver::new();
            let secret_ref = SecretRef::parse("env:WANAKU_TEST_MISSING_VAR").unwrap();
            assert_eq!(
                resolver
                    .resolve(&secret_ref, &ResolutionContext::default())
                    .await
                    .unwrap_err(),
                ResolverError::NotFound
            );
        });
    }

    #[test]
    #[allow(unsafe_code)]
    fn rejects_non_allowlisted_variable() {
        futures::executor::block_on(async {
            unsafe { std::env::set_var("WANAKU_TEST_BLOCKED", "x") };
            let resolver = EnvResolver::with_allowlist(vec!["WANAKU_TEST_ALLOWED".to_owned()]);
            let secret_ref = SecretRef::parse("env:WANAKU_TEST_BLOCKED").unwrap();
            assert_eq!(
                resolver
                    .resolve(&secret_ref, &ResolutionContext::default())
                    .await
                    .unwrap_err(),
                ResolverError::InvalidReference
            );
            unsafe { std::env::remove_var("WANAKU_TEST_BLOCKED") };
        });
    }

    #[test]
    fn rejects_malicious_variable_name() {
        futures::executor::block_on(async {
            let resolver = EnvResolver::new();
            let secret_ref = SecretRef::parse("env:$(rm -rf)").unwrap();
            assert_eq!(
                resolver
                    .resolve(&secret_ref, &ResolutionContext::default())
                    .await
                    .unwrap_err(),
                ResolverError::InvalidReference
            );
        });
    }
}
