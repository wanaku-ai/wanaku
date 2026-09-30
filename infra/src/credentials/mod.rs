//! Runtime credential brokerage and caching.
//!
//! Credential contracts and resolver implementations live in `wanaku-types`.
//! This module owns the asynchronous resolution, cache, and audit runtime.

mod audit_sink;
mod broker;
mod cache;
mod discovery;
mod redaction;

pub use audit_sink::{CredentialAuditSink, record_audit};
pub use broker::{
    BrokerError, BrokerErrorKind, BrokeredCredential, CredentialAuditRecord, CredentialBroker,
    ExpiryCategory, ResolutionOutcome,
};
pub use cache::{CacheKey, CredentialCache, effective_expiry};
pub use discovery::{DiscoveryCredentialError, DiscoveryExchange, resolve_discovery_headers};
pub use redaction::CredentialRedactor;
