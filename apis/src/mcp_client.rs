use std::time::Duration;

use serde_json::Value;

const TIMEOUT: Duration = Duration::from_secs(30);

#[derive(Debug, thiserror::Error)]
pub enum McpClientError {
    #[error("HTTP request failed: {0}")]
    Http(#[from] reqwest::Error),

    #[error("JSON-RPC error from server: code={code}, message={message}")]
    JsonRpc { code: i64, message: String },

    #[error("unexpected response format: {0}")]
    Format(String),
}

async fn initialize(client: &reqwest::Client, url: &str) -> Result<Option<String>, McpClientError> {
    let body = serde_json::json!({
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "wanaku-praxis", "version": "0.1.0"}
        }
    });

    tracing::debug!(url = %url, "sending MCP initialize");

    let response = client
        .post(url)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json, text/event-stream")
        .json(&body)
        .send()
        .await?;

    let status = response.status();
    let session_id = response
        .headers()
        .get("mcp-session-id")
        .and_then(|v| v.to_str().ok())
        .map(str::to_owned);

    tracing::debug!(url = %url, %status, session_id = ?session_id, "MCP initialize complete");

    Ok(session_id)
}

fn check_json_rpc_error(response: &Value) -> Result<(), McpClientError> {
    if let Some(error) = response.get("error") {
        let code = error.get("code").and_then(Value::as_i64).unwrap_or(-1);
        let message = error
            .get("message")
            .and_then(Value::as_str)
            .unwrap_or("unknown error")
            .to_owned();
        return Err(McpClientError::JsonRpc { code, message });
    }
    Ok(())
}

pub async fn list_tools(url: &str) -> Result<Vec<Value>, McpClientError> {
    let client = reqwest::Client::builder()
        .timeout(TIMEOUT)
        .build()?;

    let session_id = initialize(&client, url).await?;

    let body = serde_json::json!({
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/list",
    });

    let mut request = client
        .post(url)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json, text/event-stream");

    if let Some(sid) = &session_id {
        request = request.header("Mcp-Session-Id", sid);
    }

    let raw_response = request
        .json(&body)
        .send()
        .await?;

    let status = raw_response.status();
    let content_type = raw_response
        .headers()
        .get("content-type")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_owned();
    let response_text = raw_response.text().await?;

    tracing::debug!(
        url = %url,
        %status,
        content_type = %content_type,
        body_len = response_text.len(),
        "MCP tools/list response"
    );

    let response: Value = if content_type.contains("text/event-stream") {
        parse_sse_json(&response_text)?
    } else {
        serde_json::from_str(&response_text)
            .map_err(|e| McpClientError::Format(format!("invalid JSON response: {e}")))?
    };

    check_json_rpc_error(&response)?;

    let tools = response
        .get("result")
        .and_then(|r| r.get("tools"))
        .and_then(Value::as_array)
        .cloned()
        .ok_or_else(|| McpClientError::Format(format!("missing result.tools in response: {response}")))?;

    tracing::debug!(url = %url, tool_count = tools.len(), "discovered tools from MCP server");

    Ok(tools)
}

pub async fn call_tool(
    url: &str,
    tool_name: &str,
    arguments: Value,
) -> Result<Vec<String>, McpClientError> {
    let client = reqwest::Client::builder()
        .timeout(TIMEOUT)
        .build()?;

    let session_id = initialize(&client, url).await?;

    let body = serde_json::json!({
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/call",
        "params": {
            "name": tool_name,
            "arguments": arguments,
        }
    });

    let mut request = client
        .post(url)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json, text/event-stream");

    if let Some(sid) = &session_id {
        request = request.header("Mcp-Session-Id", sid);
    }

    let raw_response = request
        .json(&body)
        .send()
        .await?;

    let ct = raw_response
        .headers()
        .get("content-type")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_owned();
    let text = raw_response.text().await?;

    let response: Value = if ct.contains("text/event-stream") {
        parse_sse_json(&text)?
    } else {
        serde_json::from_str(&text)
            .map_err(|e| McpClientError::Format(format!("invalid JSON response: {e}")))?
    };

    check_json_rpc_error(&response)?;

    let content = response
        .get("result")
        .and_then(|r| r.get("content"))
        .and_then(Value::as_array)
        .ok_or_else(|| McpClientError::Format("missing result.content in response".to_owned()))?;

    Ok(content
        .iter()
        .filter_map(|c| c.get("text").and_then(Value::as_str).map(str::to_owned))
        .collect())
}

fn parse_sse_json(sse_text: &str) -> Result<Value, McpClientError> {
    for line in sse_text.lines() {
        if let Some(data) = line.strip_prefix("data: ") {
            if let Ok(value) = serde_json::from_str::<Value>(data) {
                if value.get("jsonrpc").is_some() {
                    return Ok(value);
                }
            }
        }
    }
    Err(McpClientError::Format(
        "no JSON-RPC message found in SSE response".to_owned(),
    ))
}
