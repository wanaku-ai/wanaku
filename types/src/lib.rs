#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

pub mod config;
pub mod correlation;
pub mod feature;
pub mod http_response;
pub mod interactions;
pub mod mcp;
pub mod mcp_methods;
pub mod metadata;
pub mod persistence;
pub mod registry;
pub mod revision;
pub mod time;

pub use metadata::{MCP_ID_KEY, MCP_METHOD_KEY, MCP_NAME_KEY, NAMESPACE_METADATA_KEY};

pub use mcp_methods::{
    INITIALIZE, NOTIFICATIONS_INITIALIZED, PING, PROMPTS_GET, PROMPTS_LIST, RESOURCES_LIST,
    RESOURCES_READ, RESOURCES_TEMPLATES_LIST, TOOLS_CALL, TOOLS_LIST,
};

pub const WANAKU_BODY_ARG: &str = "wanaku_body";
