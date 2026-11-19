#![cfg_attr(
    feature = "openapi",
    expect(
        clippy::large_stack_frames,
        reason = "utoipa::ToSchema generates schema construction code"
    )
)]

use std::path::PathBuf;

use serde::{Deserialize, Serialize};
use wanaku_types::registry::ToolEntry;

/// Per-invocation limits. Host-call waiting shares the execution deadline.
#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(default, deny_unknown_fields)]
pub struct HandlerLimits {
    pub fuel: u64,
    pub memory_bytes: usize,
    pub timeout_ms: u64,
    pub max_concurrent: usize,
}

impl Default for HandlerLimits {
    fn default() -> Self {
        Self {
            fuel: 10_000_000,
            memory_bytes: 64 * 1024 * 1024,
            timeout_ms: 1000,
            max_concurrent: 8,
        }
    }
}

/// A concrete tool definition and the WASM component that implements it.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct HandlerDefinition {
    pub tool: ToolEntry,
    #[cfg_attr(feature = "openapi", schema(value_type = String))]
    pub path: PathBuf,
    /// Explicit concrete namespace grants. Empty means no MCP host access.
    #[serde(default)]
    pub allowed_namespaces: Vec<String>,
    #[serde(default)]
    pub limits: HandlerLimits,
}
