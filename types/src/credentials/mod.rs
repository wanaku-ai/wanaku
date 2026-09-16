//! Proxy-side credential brokerage.
//!
//! This module provides the shared security primitives that let Wanaku resolve
//! and inject upstream credentials **only after** an action has passed
//! governance checks, scoped to an exact forward, origin, purpose, and
//! operation. Callers that invoke a governed item never receive the credential
//! Wanaku uses to reach the backend.
//!
//! The pieces:
//!
//! * [`SecretMaterial`] — a non-serializable, redacted, zeroize-on-drop secret
//!   type.
//! * [`SecretResolver`] / [`ResolverRegistry`] — a pluggable resolution
//!   abstraction that fails closed for unknown schemes. Ships with an in-memory
//!   [`FakeResolver`] for tests and a narrowly scoped [`EnvResolver`].
//! * [`CredentialBinding`] — a top-level resource owned by one forward, scoped
//!   to an exact [`NormalizedOrigin`], with per-purpose use and optional
//!   restrictions.
//! * [`InjectionMechanism`] — Bearer, named-header, and Basic injection with
//!   header-name validation and collision detection.
//!
//! The `wanaku-infra` crate provides the runtime credential broker, cache, and
//! redacted audit metadata.

pub mod binding;
pub mod env_resolver;
pub mod fake_resolver;
pub mod injection;
pub mod resolver;
pub mod secret;

pub use binding::{
    BindingError, BindingRestrictions, CacheRules, CredentialBinding, CredentialPurpose,
    NormalizedOrigin, OriginError, UseScope,
};
pub use env_resolver::EnvResolver;
pub use fake_resolver::FakeResolver;
pub use injection::{
    CredentialHeader, InjectionError, InjectionMechanism, detect_collisions, validate_header_name,
};
pub use resolver::{
    ResolutionContext, ResolvedSecret, ResolverError, ResolverRegistry, SecretRef, SecretRefError,
    SecretResolver,
};
pub use secret::SecretMaterial;
