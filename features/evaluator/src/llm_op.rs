use wanaku_apis::llm::{self, LlmClient};
use wanaku_apis::mcp::McpContext;
use wanaku_apis::metrics::MetricsStore;

use crate::config::LlmDef;

/// Execute the LLM operation and return the raw result string.
/// The processor WASM module is responsible for parsing and acting on this.
pub async fn run_llm_operation(
    evaluator_name: &str,
    llm_def: &LlmDef,
    mcp: &McpContext<'_>,
    metrics: Option<&MetricsStore>,
) -> Option<String> {
    let client = LlmClient::new(&llm_def.url, &llm_def.model, &llm_def.api_key)?;

    let user_prompt = build_context_prompt(mcp);

    let start = std::time::Instant::now();
    let result = client.chat(&llm_def.prompt, &user_prompt).await;

    if let Some(store) = metrics {
        store.record_llm_call(evaluator_name, result.is_some(), start.elapsed());
        if result.as_ref().is_none_or(String::is_empty) {
            store.record_llm_empty_result(evaluator_name);
        }
    }

    result
}

#[expect(clippy::too_many_lines, reason = "prompt assembly with multiple optional sections")]
fn build_context_prompt(mcp: &McpContext<'_>) -> String {
    let mut prompt = String::with_capacity(4096);

    if !mcp.history.is_empty() {
        prompt.push_str("## Conversation Context\n\n");
        let capped = if mcp.history.len() > 10 {
            &mcp.history[mcp.history.len() - 10..]
        } else {
            mcp.history
        };
        for interaction in capped {
            if let Some(messages) = interaction.request_body.get("messages")
                && let Some(arr) = messages.as_array() {
                    for msg in arr {
                        let role = msg
                            .get("role")
                            .and_then(serde_json::Value::as_str)
                            .unwrap_or("unknown");
                        let content = msg
                            .get("content")
                            .and_then(serde_json::Value::as_str)
                            .unwrap_or("");
                        if !content.is_empty() {
                            prompt.push_str(&format!(
                                "[{role}]: {}\n",
                                llm::sanitize(content, 1000)
                            ));
                        }
                    }
                }
            prompt.push('\n');
        }
    }

    prompt.push_str(&format!("## Request: {}\n\n", mcp.method));

    if let Some(name) = mcp.tool_name {
        prompt.push_str(&format!("Tool: {name}\n"));
    }

    if !mcp.arguments.is_empty() {
        prompt.push_str("Arguments:\n");
        for (key, value) in mcp.arguments {
            prompt.push_str(&format!(
                "  {}: {}\n",
                llm::sanitize(key, 500),
                llm::sanitize(value, 500)
            ));
        }
    }

    if !mcp.tools.is_empty() {
        prompt.push_str("\n## Available Tools\n\n");
        for tool in mcp.tools {
            prompt.push_str(&format!(
                "- {}: {}\n",
                tool.name,
                llm::sanitize(&tool.description, 200)
            ));
        }
    }

    prompt
}

/// Retry an LLM operation with a correction prompt that includes
/// the schema and the previous (invalid) response.
pub async fn retry_with_schema_correction(
    llm_def: &LlmDef,
    mcp: &McpContext<'_>,
    previous_result: &str,
    schema: &serde_json::Value,
    validation_error: &str,
) -> Option<String> {
    let client = LlmClient::new(&llm_def.url, &llm_def.model, &llm_def.api_key)?;

    let base_prompt = build_context_prompt(mcp);
    let correction = format!(
        "{base_prompt}\n\n## Correction\n\n\
         Your previous response did not match the expected JSON schema.\n\
         Validation error: {validation_error}\n\
         Expected schema:\n```json\n{schema}\n```\n\
         Your response was:\n```\n{previous_result}\n```\n\n\
         Provide a response that strictly matches the expected JSON schema."
    );

    client.chat(&llm_def.prompt, &correction).await
}
