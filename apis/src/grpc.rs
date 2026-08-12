use std::collections::HashMap;
use std::sync::Arc;

use dashmap::DashMap;
use tonic::transport::Channel;

pub mod tool_proto {
    tonic::include_proto!("ai.wanaku.tool.v1");
}

pub mod resource_proto {
    tonic::include_proto!("ai.wanaku.resource.v1");
}

pub use tool_proto::tool_invoker_client::ToolInvokerClient;
pub use tool_proto::{ToolInvokeReply, ToolInvokeRequest};
pub use resource_proto::resource_acquirer_client::ResourceAcquirerClient;
pub use resource_proto::{ResourceReply, ResourceRequest};

#[derive(Debug, thiserror::Error)]
pub enum GrpcError {
    #[error("invalid endpoint URI for {address}: {source}")]
    InvalidUri {
        address: String,
        source: http::uri::InvalidUri,
    },

    #[error("failed to connect to {address}: {source}")]
    Connect {
        address: String,
        source: tonic::transport::Error,
    },

    #[error("tool invocation failed: {0}")]
    Invocation(#[from] tonic::Status),
}

#[derive(Clone)]
pub struct GrpcPool {
    channels: Arc<DashMap<String, Channel>>,
}

impl GrpcPool {
    pub fn new() -> Self {
        Self {
            channels: Arc::new(DashMap::new()),
        }
    }

    async fn get_or_connect(&self, address: &str) -> Result<Channel, GrpcError> {
        if let Some(channel) = self.channels.get(address) {
            return Ok(channel.clone());
        }

        let endpoint = format!("http://{address}");
        let channel = Channel::from_shared(endpoint)
            .map_err(|e| GrpcError::InvalidUri {
                address: address.to_owned(),
                source: e,
            })?
            .connect()
            .await
            .map_err(|e| GrpcError::Connect {
                address: address.to_owned(),
                source: e,
            })?;

        // Use entry API to avoid TOCTOU: if another task connected
        // in parallel, use their channel instead.
        let entry = self
            .channels
            .entry(address.to_owned())
            .or_insert(channel);
        Ok(entry.value().clone())
    }

    pub async fn invoke_tool(
        &self,
        address: &str,
        uri: String,
        mut arguments: HashMap<String, String>,
        request_id: &str,
    ) -> Result<Vec<String>, GrpcError> {
        let channel = self.get_or_connect(address).await?;
        let mut client = ToolInvokerClient::new(channel);

        let body = arguments.remove(crate::WANAKU_BODY_ARG).unwrap_or_default();

        tracing::debug!(
            address = %address,
            uri = %uri,
            arguments = ?arguments,
            "sending ToolInvokeRequest via gRPC"
        );

        let grpc_request = ToolInvokeRequest {
            uri,
            body,
            arguments,
            configuration_uri: String::new(),
            secrets_uri: String::new(),
            headers: HashMap::new(),
            request_id: request_id.to_owned(),
        };

        let request = tonic::Request::new(grpc_request);

        let response = client.invoke_tool(request).await?;
        Ok(response.into_inner().content)
    }

    pub async fn acquire_resource(
        &self,
        address: &str,
        location: String,
        type_: String,
        name: String,
        request_id: &str,
    ) -> Result<Vec<String>, GrpcError> {
        let channel = self.get_or_connect(address).await?;
        let mut client = ResourceAcquirerClient::new(channel);

        let grpc_request = ResourceRequest {
            location: location.clone(),
            r#type: type_.clone(),
            name: name.clone(),
            params: HashMap::new(),
            configuration_uri: String::new(),
            secrets_uri: String::new(),
            request_id: request_id.to_owned(),
        };

        tracing::debug!(
            address = %address,
            location = %location,
            r#type = %type_,
            name = %name,
            "sending ResourceRequest via gRPC"
        );

        let request = tonic::Request::new(grpc_request);
        let response = client.resource_acquire(request).await?;
        Ok(response.into_inner().content)
    }
}

impl Default for GrpcPool {
    fn default() -> Self {
        Self::new()
    }
}
