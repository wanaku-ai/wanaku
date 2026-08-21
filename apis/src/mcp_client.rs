use std::collections::HashMap;
use std::time::Duration;

use http::{HeaderName, HeaderValue};
use rmcp::{
    ServiceExt as _,
    model::{
        CallToolRequestParams, GetPromptRequestParams, PaginatedRequestParams,
        ReadResourceRequestParams,
    },
    transport::{
        StreamableHttpClientTransport,
        streamable_http_client::StreamableHttpClientTransportConfig,
    },
};
use serde_json::Value;

use crate::registry::McpServerInfo;

const TIMEOUT: Duration = Duration::from_secs(30);
const MAX_TOOLS: usize = 500;
const MAX_RESOURCES: usize = 500;
const MAX_RESOURCE_TEMPLATES: usize = 500;
const MAX_PROMPTS: usize = 500;

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

    #[error("prompts/list failed for {url}: {message}")]
    ListPrompts { url: String, message: String },

    #[error("prompts/get failed for {url}, prompt={prompt_name}: {message}")]
    GetPrompt {
        url: String,
        prompt_name: String,
        message: String,
    },
}

fn build_transport(url: &str) -> impl rmcp::transport::Transport<rmcp::RoleClient> + use<> {
    build_transport_with_headers(url, HashMap::new())
}

fn build_transport_with_headers(
    url: &str,
    headers: HashMap<HeaderName, HeaderValue>,
) -> impl rmcp::transport::Transport<rmcp::RoleClient> + use<> {
    let mut config = StreamableHttpClientTransportConfig::with_uri(url);
    if !headers.is_empty() {
        config = config.custom_headers(headers);
    }
    StreamableHttpClientTransport::from_config(config)
}

#[expect(clippy::too_many_lines, clippy::large_stack_frames, reason = "MCP paginated client call")]
pub async fn list_tools(url: &str) -> Result<Vec<Value>, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout {
            url: url.clone(),
        })?
        .map_err(|e| McpClientError::Connection {
            url: url.clone(),
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
            url: url.clone(),
        })?
        .map_err(|e| McpClientError::ListTools {
            url: url.clone(),
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
            url: url.clone(),
            message: e.to_string(),
        }))
        .collect()
}

#[derive(Debug, Clone)]
pub struct CallToolResponse {
    pub content: Vec<String>,
    pub is_error: bool,
}

#[expect(clippy::too_many_lines, clippy::large_stack_frames, reason = "MCP client call with argument mapping")]
pub async fn call_tool(
    url: &str,
    tool_name: &str,
    arguments: Value,
    forward_headers: HashMap<HeaderName, HeaderValue>,
) -> Result<CallToolResponse, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport_with_headers(&url, forward_headers);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout {
            url: url.clone(),
        })?
        .map_err(|e| McpClientError::Connection {
            url: url.clone(),
            message: e.to_string(),
        })?;

    let mut params = CallToolRequestParams::new(tool_name.to_owned());
    if let Value::Object(args) = &arguments {
        params = params.with_arguments(args.clone());
    }

    let result = tokio::time::timeout(TIMEOUT, Box::pin(client.call_tool(params)))
        .await
        .map_err(|_| McpClientError::Timeout {
            url: url.clone(),
        })?
        .map_err(|e| McpClientError::CallTool {
            url: url.clone(),
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

#[expect(clippy::too_many_lines, clippy::large_stack_frames, reason = "MCP paginated client call")]
pub async fn list_resources(url: &str) -> Result<Vec<Value>, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.clone() })?
        .map_err(|e| McpClientError::Connection {
            url: url.clone(),
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
        .map_err(|_| McpClientError::Timeout { url: url.clone() })?
        .map_err(|e| McpClientError::ListResources {
            url: url.clone(),
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
            url: url.clone(),
            message: e.to_string(),
        }))
        .collect()
}

#[expect(clippy::large_stack_frames, reason = "MCP client call")]
pub async fn read_resource(
    url: &str,
    resource_uri: &str,
) -> Result<Vec<Value>, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.clone() })?
        .map_err(|e| McpClientError::Connection {
            url: url.clone(),
            message: e.to_string(),
        })?;

    let params = ReadResourceRequestParams::new(resource_uri.to_owned());

    let result = tokio::time::timeout(TIMEOUT, Box::pin(client.read_resource(params)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.clone() })?
        .map_err(|e| McpClientError::ReadResource {
            url: url.clone(),
            resource_uri: resource_uri.to_owned(),
            message: e.to_string(),
        })?;

    result
        .contents
        .into_iter()
        .map(|c| serde_json::to_value(c).map_err(|e| McpClientError::ReadResource {
            url: url.clone(),
            resource_uri: resource_uri.to_owned(),
            message: e.to_string(),
        }))
        .collect()
}

#[expect(clippy::too_many_lines, clippy::large_stack_frames, reason = "MCP paginated client call")]
pub async fn list_resource_templates(url: &str) -> Result<Vec<Value>, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.clone() })?
        .map_err(|e| McpClientError::Connection {
            url: url.clone(),
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
        .map_err(|_| McpClientError::Timeout { url: url.clone() })?
        .map_err(|e| McpClientError::ListResourceTemplates {
            url: url.clone(),
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
            url: url.clone(),
            message: e.to_string(),
        }))
        .collect()
}

#[expect(clippy::too_many_lines, clippy::large_stack_frames, reason = "MCP paginated client call")]
pub async fn list_prompts(url: &str) -> Result<Vec<Value>, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.clone() })?
        .map_err(|e| McpClientError::Connection {
            url: url.clone(),
            message: e.to_string(),
        })?;

    tracing::debug!(url = %url, "connected to MCP server for prompts/list");

    let mut all_prompts = Vec::new();
    let mut cursor = None;

    for _ in 0..100 {
        let params = PaginatedRequestParams::default().with_cursor(cursor);
        let page = tokio::time::timeout(
            TIMEOUT,
            Box::pin(client.list_prompts(Some(params))),
        )
        .await
        .map_err(|_| McpClientError::Timeout { url: url.clone() })?
        .map_err(|e| McpClientError::ListPrompts {
            url: url.clone(),
            message: e.to_string(),
        })?;

        all_prompts.extend(page.prompts);

        if all_prompts.len() >= MAX_PROMPTS {
            all_prompts.truncate(MAX_PROMPTS);
            break;
        }

        match page.next_cursor {
            Some(next) if !next.is_empty() => cursor = Some(next),
            _ => break,
        }
    }

    tracing::debug!(url = %url, prompt_count = all_prompts.len(), "discovered prompts from MCP server");

    all_prompts
        .into_iter()
        .map(|p| serde_json::to_value(p).map_err(|e| McpClientError::ListPrompts {
            url: url.clone(),
            message: e.to_string(),
        }))
        .collect()
}

#[expect(clippy::large_stack_frames, reason = "MCP client call")]
pub async fn get_prompt(
    url: &str,
    prompt_name: &str,
    arguments: Option<serde_json::Map<String, Value>>,
) -> Result<Value, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.clone() })?
        .map_err(|e| McpClientError::Connection {
            url: url.clone(),
            message: e.to_string(),
        })?;

    let mut params = GetPromptRequestParams::new(prompt_name.to_owned());
    if let Some(args) = arguments {
        params.arguments = Some(args);
    }

    let result = tokio::time::timeout(TIMEOUT, Box::pin(client.get_prompt(params)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.clone() })?
        .map_err(|e| McpClientError::GetPrompt {
            url: url.clone(),
            prompt_name: prompt_name.to_owned(),
            message: e.to_string(),
        })?;

    serde_json::to_value(result).map_err(|e| McpClientError::GetPrompt {
        url: url.clone(),
        prompt_name: prompt_name.to_owned(),
        message: e.to_string(),
    })
}

#[derive(Debug)]
pub struct ForwardDiscovery {
    pub server_info: Option<McpServerInfo>,
    pub tools: Vec<Value>,
    pub resources: Vec<Value>,
    pub resource_templates: Vec<Value>,
    pub prompts: Vec<Value>,
}

#[expect(clippy::too_many_lines, clippy::large_stack_frames, reason = "MCP forward discovery aggregating multiple calls")]
pub async fn discover_forward(url: &str) -> Result<ForwardDiscovery, McpClientError> {
    let url = url.to_owned();
    let transport = build_transport(&url);

    let client = tokio::time::timeout(TIMEOUT, Box::pin(().serve(transport)))
        .await
        .map_err(|_| McpClientError::Timeout { url: url.clone() })?
        .map_err(|e| McpClientError::Connection {
            url: url.clone(),
            message: e.to_string(),
        })?;

    tracing::debug!(url = %url, "connected to MCP server for forward discovery");

    let server_info = client.peer_info().and_then(|info| {
        let impl_info = info.server_info.as_ref()?;

        let mut capabilities = Vec::new();
        if info.capabilities.tools.is_some() {
            capabilities.push("tools".to_owned());
        }
        if info.capabilities.resources.is_some() {
            capabilities.push("resources".to_owned());
        }
        if info.capabilities.prompts.is_some() {
            capabilities.push("prompts".to_owned());
        }
        if info.capabilities.logging.is_some() {
            capabilities.push("logging".to_owned());
        }
        if info.capabilities.completions.is_some() {
            capabilities.push("completions".to_owned());
        }

        let extensions = info
            .capabilities
            .extensions
            .as_ref()
            .map(|ext| ext.keys().cloned().collect())
            .unwrap_or_default();

        Some(McpServerInfo {
            server_name: impl_info.name.clone(),
            version: impl_info.version.clone(),
            description: impl_info.description.clone(),
            website_url: impl_info.website_url.clone(),
            capabilities,
            extensions,
            instructions: info.instructions.clone(),
        })
    });

    if let Some(ref si) = server_info {
        tracing::info!(
            url = %url,
            server_name = %si.server_name,
            version = %si.version,
            "captured MCP server identity"
        );
    }

    let tools = discover_tools_on_session(&client, &url).await;
    let resources = discover_resources_on_session(&client, &url).await;
    let resource_templates = discover_resource_templates_on_session(&client, &url).await;
    let prompts = discover_prompts_on_session(&client, &url).await;

    Ok(ForwardDiscovery {
        server_info,
        tools,
        resources,
        resource_templates,
        prompts,
    })
}

#[expect(clippy::too_many_lines, clippy::cognitive_complexity, reason = "MCP paginated discovery with error handling")]
async fn discover_tools_on_session(
    client: &rmcp::service::Peer<rmcp::RoleClient>,
    url: &str,
) -> Vec<Value> {
    let mut all_tools = Vec::new();
    let mut cursor = None;

    for _ in 0..100 {
        let params = PaginatedRequestParams::default().with_cursor(cursor);
        let page = match tokio::time::timeout(TIMEOUT, Box::pin(client.list_tools(Some(params))))
            .await
        {
            Ok(Ok(page)) => page,
            Ok(Err(e)) => {
                tracing::warn!(url = %url, error = %e, "tools/list failed during discovery");
                break;
            }
            Err(_) => {
                tracing::warn!(url = %url, "tools/list timed out during discovery");
                break;
            }
        };

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

    tracing::debug!(url = %url, tool_count = all_tools.len(), "discovered tools");

    all_tools
        .into_iter()
        .filter_map(|t| serde_json::to_value(t).ok())
        .collect()
}

#[expect(clippy::too_many_lines, clippy::cognitive_complexity, reason = "MCP paginated discovery with error handling")]
async fn discover_resources_on_session(
    client: &rmcp::service::Peer<rmcp::RoleClient>,
    url: &str,
) -> Vec<Value> {
    let mut all_resources = Vec::new();
    let mut cursor = None;

    for _ in 0..100 {
        let params = PaginatedRequestParams::default().with_cursor(cursor);
        let page =
            match tokio::time::timeout(TIMEOUT, Box::pin(client.list_resources(Some(params))))
                .await
            {
                Ok(Ok(page)) => page,
                Ok(Err(e)) => {
                    tracing::warn!(url = %url, error = %e, "resources/list failed during discovery");
                    break;
                }
                Err(_) => {
                    tracing::warn!(url = %url, "resources/list timed out during discovery");
                    break;
                }
            };

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

    tracing::debug!(url = %url, resource_count = all_resources.len(), "discovered resources");

    all_resources
        .into_iter()
        .filter_map(|r| serde_json::to_value(r).ok())
        .collect()
}

#[expect(clippy::too_many_lines, clippy::cognitive_complexity, reason = "MCP paginated discovery with error handling")]
async fn discover_resource_templates_on_session(
    client: &rmcp::service::Peer<rmcp::RoleClient>,
    url: &str,
) -> Vec<Value> {
    let mut all_templates = Vec::new();
    let mut cursor = None;

    for _ in 0..100 {
        let params = PaginatedRequestParams::default().with_cursor(cursor);
        let page = match tokio::time::timeout(
            TIMEOUT,
            Box::pin(client.list_resource_templates(Some(params))),
        )
        .await
        {
            Ok(Ok(page)) => page,
            Ok(Err(e)) => {
                tracing::debug!(url = %url, error = %e, "resource_templates/list not supported");
                break;
            }
            Err(_) => {
                tracing::warn!(url = %url, "resource_templates/list timed out during discovery");
                break;
            }
        };

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

    tracing::debug!(url = %url, template_count = all_templates.len(), "discovered resource templates");

    all_templates
        .into_iter()
        .filter_map(|t| serde_json::to_value(t).ok())
        .collect()
}

#[expect(clippy::too_many_lines, clippy::cognitive_complexity, reason = "MCP paginated discovery with error handling")]
async fn discover_prompts_on_session(
    client: &rmcp::service::Peer<rmcp::RoleClient>,
    url: &str,
) -> Vec<Value> {
    let mut all_prompts = Vec::new();
    let mut cursor = None;

    for _ in 0..100 {
        let params = PaginatedRequestParams::default().with_cursor(cursor);
        let page =
            match tokio::time::timeout(TIMEOUT, Box::pin(client.list_prompts(Some(params)))).await
            {
                Ok(Ok(page)) => page,
                Ok(Err(e)) => {
                    tracing::warn!(url = %url, error = %e, "prompts/list failed during discovery");
                    break;
                }
                Err(_) => {
                    tracing::warn!(url = %url, "prompts/list timed out during discovery");
                    break;
                }
            };

        all_prompts.extend(page.prompts);
        if all_prompts.len() >= MAX_PROMPTS {
            all_prompts.truncate(MAX_PROMPTS);
            break;
        }

        match page.next_cursor {
            Some(next) if !next.is_empty() => cursor = Some(next),
            _ => break,
        }
    }

    tracing::debug!(url = %url, prompt_count = all_prompts.len(), "discovered prompts");

    all_prompts
        .into_iter()
        .filter_map(|p| serde_json::to_value(p).ok())
        .collect()
}
