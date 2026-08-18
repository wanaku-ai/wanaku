#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

pub mod config;
pub mod correlation;
pub mod feature;
pub mod http_response;
pub mod interactions;
pub mod llm;
pub mod mcp_client;
pub mod persistence;
pub mod registry;

pub const WANAKU_BODY_ARG: &str = "wanaku_body";
pub const NAMESPACE_METADATA_KEY: &str = "wanaku.namespace";
