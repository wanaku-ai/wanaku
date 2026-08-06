
pub mod config;
pub mod correlation;
pub mod feature;
pub mod grpc;
pub mod interactions;
pub mod llm;
pub mod mcp_client;
pub mod persistence;
pub mod registry;
pub mod safety;

pub const WANAKU_BODY_ARG: &str = "wanaku_body";
pub const NAMESPACE_METADATA_KEY: &str = "wanaku.namespace";
