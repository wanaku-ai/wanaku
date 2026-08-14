use std::time::Duration;

use rmcp::{
    ServiceExt as _,
    model::{CallToolRequestParams, PaginatedRequestParams, ReadResourceRequestParams},
    transport::{
        StreamableHttpClientTransport,
        streamable_http_client::StreamableHttpClientTransportConfig,
    },
};
use serde_json::Value;

const TIMEOUT: Duration = Duration::from_secs(30);
const MAX_TOOLS: usize = 500;
const MAX_RESOURCES: usize = 500;
const MAX_RESOURCE_TEMPLATES: usize = 500;

#[derive(Debug, thiserror::Error)]
pub enum McpClientError {
    #[error("failed to connect to MCP server at {url}: {message}")]
    Connection { url: String, message: String },

    #[error("MCP operation timed out for {url}")]
    Timeout { url: String },

    #[error("tools/list failed for {url}: {message}")]
    ListTools { url: String, message: String },

    #[error("tools/call failed for {url}, tool={tool_name}: {message}")]
    CallTool {
        url: String,
        tool_name: String,
        message: String,
    },

    #[error("resources/list failed for {url}: {message}")]
    ListResources { url: String, message: String },

    #[error("resources/templates/list failed for {url}: {message}")]
    ListResourceTemplates { url: String, message: String },

    #[error("resources/read failed for {url}, uri={resource_uri}: {message}")]
    ReadResource {
        url: String,
        resource_uri: String,
        message: String,
    },
}

fn build_transport(url: &str) -> impl rmcp::transport::Transport<rmcp::RoleClient> + use<> {
    let config = StreamableHttpClientTransportConfig::with_uri(url);
    StreamableHttpClientTransport::from_config(config)
}

pub async fn list_tools(url: &str) -> Result<Vec<Value>, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout {
            url: url.to_owned(),
        })?
        .map_err(|e| McpClientError::Connection {
            url: url.to_owned(),
            message: e.to_string(),
        })?;

    tracing::debug!(url = %url, "connected to MCP server for tools/list");

    let mut all_tools = Vec::new();
    let mut cursor = None;

    for _ in 0..100 {
        let params = PaginatedRequestParams::default().with_cursor(cursor);
        let page = tokio::time::timeout(
            TIMEOUT,
            Box::pin(client.list_tools(Some(params))),
        )
        .await
        .map_err(|_| McpClientError::Timeout {
            url: url.to_owned(),
        })?
        .map_err(|e| McpClientError::ListTools {
            url: url.to_owned(),
            message: e.to_string(),
        })?;

        all_tools.extend(page.tools);

        if all_tools.len() >= MAX_TOOLS {
            all_tools.truncate(MAX_TOOLS);
            break;
        }

        match page.next_cursor {
            Some(next) if !next.is_empty() => cursor = Some(next),
            _ => break,
        }
    }

    tracing::debug!(url = %url, tool_count = all_tools.len(), "discovered tools from MCP server");

    all_tools
        .into_iter()
        .map(|t| serde_json::to_value(t).map_err(|e| McpClientError::ListTools {
            url: url.to_owned(),
            message: e.to_string(),
        }))
        .collect()
}

#[derive(Debug, Clone)]
pub struct CallToolResponse {
    pub content: Vec<String>,
    pub is_error: bool,
}

pub async fn call_tool(
    url: &str,
    tool_name: &str,
    arguments: Value,
) -> Result<CallToolResponse, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout {
            url: url.to_owned(),
        })?
        .map_err(|e| McpClientError::Connection {
            url: url.to_owned(),
            message: e.to_string(),
        })?;

    let mut params = CallToolRequestParams::new(tool_name.to_owned());
    if let Value::Object(args) = &arguments {
        params = params.with_arguments(args.clone());
    }

    let result = tokio::time::timeout(TIMEOUT, Box::pin(client.call_tool(params)))
        .await
        .map_err(|_| McpClientError::Timeout {
            url: url.to_owned(),
        })?
        .map_err(|e| McpClientError::CallTool {
            url: url.to_owned(),
            tool_name: tool_name.to_owned(),
            message: e.to_string(),
        })?;

    let content = result
        .content
        .iter()
        .filter_map(|block| match block {
            rmcp::model::ContentBlock::Text(text) => Some(text.text.clone()),
            _ => None,
        })
        .collect();

    Ok(CallToolResponse {
        content,
        is_error: result.is_error.unwrap_or(false),
    })
}

pub async fn list_resources(url: &str) -> Result<Vec<Value>, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.to_owned() })?
        .map_err(|e| McpClientError::Connection {
            url: url.to_owned(),
            message: e.to_string(),
        })?;

    tracing::debug!(url = %url, "connected to MCP server for resources/list");

    let mut all_resources = Vec::new();
    let mut cursor = None;

    for _ in 0..100 {
        let params = PaginatedRequestParams::default().with_cursor(cursor);
        let page = tokio::time::timeout(
            TIMEOUT,
            Box::pin(client.list_resources(Some(params))),
        )
        .await
        .map_err(|_| McpClientError::Timeout { url: url.to_owned() })?
        .map_err(|e| McpClientError::ListResources {
            url: url.to_owned(),
            message: e.to_string(),
        })?;

        all_resources.extend(page.resources);

        if all_resources.len() >= MAX_RESOURCES {
            all_resources.truncate(MAX_RESOURCES);
            break;
        }

        match page.next_cursor {
            Some(next) if !next.is_empty() => cursor = Some(next),
            _ => break,
        }
    }

    tracing::debug!(url = %url, resource_count = all_resources.len(), "discovered resources from MCP server");

    all_resources
        .into_iter()
        .map(|r| serde_json::to_value(r).map_err(|e| McpClientError::ListResources {
            url: url.to_owned(),
            message: e.to_string(),
        }))
        .collect()
}

pub async fn read_resource(
    url: &str,
    resource_uri: &str,
) -> Result<Vec<Value>, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.to_owned() })?
        .map_err(|e| McpClientError::Connection {
            url: url.to_owned(),
            message: e.to_string(),
        })?;

    let params = ReadResourceRequestParams::new(resource_uri.to_owned());

    let result = tokio::time::timeout(TIMEOUT, Box::pin(client.read_resource(params)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.to_owned() })?
        .map_err(|e| McpClientError::ReadResource {
            url: url.to_owned(),
            resource_uri: resource_uri.to_owned(),
            message: e.to_string(),
        })?;

    result
        .contents
        .into_iter()
        .map(|c| serde_json::to_value(c).map_err(|e| McpClientError::ReadResource {
            url: url.to_owned(),
            resource_uri: resource_uri.to_owned(),
            message: e.to_string(),
        }))
        .collect()
}

pub async fn list_resource_templates(url: &str) -> Result<Vec<Value>, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.to_owned() })?
        .map_err(|e| McpClientError::Connection {
            url: url.to_owned(),
            message: e.to_string(),
        })?;

    tracing::debug!(url = %url, "connected to MCP server for resources/templates/list");

    let mut all_templates = Vec::new();
    let mut cursor = None;

    for _ in 0..100 {
        let params = PaginatedRequestParams::default().with_cursor(cursor);
        let page = tokio::time::timeout(
            TIMEOUT,
            Box::pin(client.list_resource_templates(Some(params))),
        )
        .await
        .map_err(|_| McpClientError::Timeout { url: url.to_owned() })?
        .map_err(|e| McpClientError::ListResourceTemplates {
            url: url.to_owned(),
            message: e.to_string(),
        })?;

        all_templates.extend(page.resource_templates);

        if all_templates.len() >= MAX_RESOURCE_TEMPLATES {
            all_templates.truncate(MAX_RESOURCE_TEMPLATES);
            break;
        }

        match page.next_cursor {
            Some(next) if !next.is_empty() => cursor = Some(next),
            _ => break,
        }
    }

    tracing::debug!(url = %url, template_count = all_templates.len(), "discovered resource templates from MCP server");

    all_templates
        .into_iter()
        .map(|t| serde_json::to_value(t).map_err(|e| McpClientError::ListResourceTemplates {
            url: url.to_owned(),
            message: e.to_string(),
        }))
        .collect()
}
