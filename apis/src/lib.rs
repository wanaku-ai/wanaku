#![deny(unsafe_code)]

pub mod correlation;
pub mod grpc;
pub mod interactions;
pub mod mcp_client;
pub mod registry;

pub const WANAKU_BODY_ARG: &str = "wanaku_body";
