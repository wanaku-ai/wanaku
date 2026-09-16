//! Runtime credential brokerage and caching.
//!
//! Credential contracts and resolver implementations live in `wanaku-types`.
//! This module owns the asynchronous resolution, cache, and audit runtime.

mod broker;
mod cache;

pub use broker::{
    BrokerError, BrokerErrorKind, BrokeredCredential, CredentialAuditRecord, CredentialBroker,
    ExpiryCategory, ResolutionOutcome,
};
pub use cache::{CacheKey, CredentialCache, effective_expiry};
