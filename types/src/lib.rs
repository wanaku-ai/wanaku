#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

pub mod config;
pub mod correlation;
pub mod feature;
pub mod http_response;
pub mod interactions;
pub mod mcp;
pub mod metadata;
pub mod persistence;
pub mod registry;
pub mod revision;
pub mod time;

pub use metadata::{MCP_ID_KEY, MCP_METHOD_KEY, MCP_NAME_KEY, NAMESPACE_METADATA_KEY};

pub const WANAKU_BODY_ARG: &str = "wanaku_body";
