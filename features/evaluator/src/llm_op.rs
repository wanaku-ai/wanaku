use std::fmt::Write as _;

use wanaku_infra::llm::{self, LlmClient};
use wanaku_infra::metrics::MetricsStore;
use wanaku_types::mcp::McpContext;

use crate::config::{LlmConnection, LlmDef};

/// An evaluator's LLM operation definition paired with its resolved
/// connection — everything needed to actually call the LLM. Grouping these
/// keeps the functions below under the workspace's argument-count lint.
#[derive(Clone, Copy)]
pub struct ResolvedLlm<'a> {
    pub def: &'a LlmDef,
    pub connection: &'a LlmConnection,
}

/// Execute the LLM operation and return the raw result string.
/// The processor WASM module is responsible for parsing and acting on this.
pub async fn run_llm_operation(
    evaluator_name: &str,
    llm: ResolvedLlm<'_>,
    mcp: &McpContext<'_>,
    metrics: Option<&MetricsStore>,
) -> Option<String> {
    let client = LlmClient::new(&llm.connection.url, &llm.connection.model, &llm.connection.api_key)?;

    let user_prompt = build_context_prompt(mcp);

    let start = std::time::Instant::now();
    let result = client.chat(&llm.def.prompt, &user_prompt).await;

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
                            let _ = writeln!(
                                prompt,
                                "[{role}]: {}",
                                llm::sanitize(content, 1000)
                            );
                        }
                    }
                }
            prompt.push('\n');
        }
    }

    let _ = write!(prompt, "## Request: {}\n\n", mcp.method);

    if let Some(name) = mcp.tool_name {
        let _ = writeln!(prompt, "Tool: {name}");
    }

    if !mcp.arguments.is_empty() {
        prompt.push_str("Arguments:\n");
        for (key, value) in mcp.arguments {
            let _ = writeln!(
                prompt,
                "  {}: {}",
                llm::sanitize(key, 500),
                llm::sanitize(value, 500)
            );
        }
    }

    if !mcp.tools.is_empty() {
        prompt.push_str("\n## Available Tools\n\n");
        for tool in mcp.tools {
            let _ = writeln!(
                prompt,
                "- {}: {}",
                tool.name,
                llm::sanitize(&tool.description, 200)
            );
        }
    }

    prompt
}

/// Retry an LLM operation with a correction prompt that includes
/// the schema and the previous (invalid) response.
pub async fn retry_with_schema_correction(
    llm: ResolvedLlm<'_>,
    mcp: &McpContext<'_>,
    previous_result: &str,
    schema: &serde_json::Value,
    validation_error: &str,
) -> Option<String> {
    let client = LlmClient::new(&llm.connection.url, &llm.connection.model, &llm.connection.api_key)?;

    let base_prompt = build_context_prompt(mcp);
    let correction = format!(
        "{base_prompt}\n\n## Correction\n\n\
         Your previous response did not match the expected JSON schema.\n\
         Validation error: {validation_error}\n\
         Expected schema:\n```json\n{schema}\n```\n\
         Your response was:\n```\n{previous_result}\n```\n\n\
         Provide a response that strictly matches the expected JSON schema."
    );

    client.chat(&llm.def.prompt, &correction).await
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    use crate::config::LlmOperation;
    use http::{Method, StatusCode};
    use wanaku_types::interactions::Interaction;
    use wanaku_types::registry::ToolEntry;
    use wanaku_types::{TOOLS_CALL, TOOLS_LIST};
    use wiremock::matchers::{body_json, header, method, path};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    fn llm_def() -> LlmDef {
        LlmDef {
            operation: LlmOperation::Classify,
            prompt: "Classify the request".to_owned(),
            connection: "test".to_owned(),
            result_schema: None,
        }
    }

    fn llm_connection(url: String) -> LlmConnection {
        LlmConnection {
            name: "test".to_owned(),
            model: "test-model".to_owned(),
            url,
            api_key: "test-key".to_owned(),
        }
    }

    fn chat_response(content: &str) -> serde_json::Value {
        serde_json::json!({
            "choices": [{"message": {"content": content}}]
        })
    }

    async fn mount_chat_response(server: &MockServer, status: StatusCode, content: &str) {
        Mock::given(method(Method::POST))
            .and(path("/chat/completions"))
            .respond_with(ResponseTemplate::new(status).set_body_json(chat_response(content)))
            .expect(1)
            .mount(server)
            .await;
    }

    async fn mount_expected_chat_response(
        server: &MockServer,
        user_prompt: &str,
        content: &str,
    ) {
        Mock::given(method(Method::POST))
            .and(path("/chat/completions"))
            .and(header("authorization", "Bearer test-key"))
            .and(body_json(serde_json::json!({
                "model": "test-model",
                "messages": [
                    {"role": "system", "content": "Classify the request"},
                    {"role": "user", "content": user_prompt},
                ],
                "temperature": 0.0,
            })))
            .respond_with(
                ResponseTemplate::new(StatusCode::OK).set_body_json(chat_response(content)),
            )
            .expect(1)
            .mount(server)
            .await;
    }

    fn interaction_with_messages(messages: &serde_json::Value) -> Interaction {
        Interaction {
            epoch_ms: 0,
            path: "/v1/chat/completions".to_owned(),
            conversation_id: Some("wk-test".to_owned()),
            completion_id: None,
            model: None,
            request_body: serde_json::json!({ "messages": messages }),
            response_body: serde_json::Value::Null,
            status_code: StatusCode::OK.as_u16(),
            duration_ms: 0,
        }
    }

    fn tool(name: &str, description: &str) -> ToolEntry {
        ToolEntry {
            name: name.to_owned(),
            description: description.to_owned(),
            uri: "uri".to_owned(),
            type_: "http".to_owned(),
            input_schema: serde_json::Value::Null,
            labels: HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        }
    }

    #[test]
    fn empty_context_only_has_request_section() {
        let args = HashMap::new();
        let mcp = McpContext::new(TOOLS_CALL, None, &args, &[], &[]);
        let prompt = build_context_prompt(&mcp);
        assert_eq!(prompt, format!("## Request: {TOOLS_CALL}\n\n"));
    }

    #[test]
    fn includes_tool_name_and_arguments() {
        let mut args = HashMap::new();
        args.insert("city".to_owned(), "Berlin".to_owned());
        let mcp = McpContext::new(TOOLS_CALL, Some("weather"), &args, &[], &[]);
        let prompt = build_context_prompt(&mcp);

        assert!(prompt.contains(&format!("## Request: {TOOLS_CALL}")));
        assert!(prompt.contains("Tool: weather"));
        assert!(prompt.contains("Arguments:"));
        assert!(prompt.contains("city: Berlin"));
    }

    #[test]
    fn includes_available_tools_section() {
        let args = HashMap::new();
        let tools = vec![tool("search", "Search the web"), tool("calc", "Do math")];
        let mcp = McpContext::new(TOOLS_LIST, None, &args, &tools, &[]);
        let prompt = build_context_prompt(&mcp);

        assert!(prompt.contains("## Available Tools"));
        assert!(prompt.contains("- search: Search the web"));
        assert!(prompt.contains("- calc: Do math"));
    }

    #[test]
    fn includes_conversation_history_roles_and_content() {
        let args = HashMap::new();
        let history = vec![interaction_with_messages(&serde_json::json!([
            {"role": "user", "content": "hello"},
            {"role": "assistant", "content": "hi there"},
        ]))];
        let mcp = McpContext::new(TOOLS_CALL, None, &args, &[], &history);
        let prompt = build_context_prompt(&mcp);

        assert!(prompt.contains("## Conversation Context"));
        assert!(prompt.contains("[user]: hello"));
        assert!(prompt.contains("[assistant]: hi there"));
    }

    #[test]
    fn skips_messages_with_empty_content() {
        let args = HashMap::new();
        let history = vec![interaction_with_messages(&serde_json::json!([
            {"role": "user", "content": ""},
            {"role": "user", "content": "real message"},
        ]))];
        let mcp = McpContext::new(TOOLS_CALL, None, &args, &[], &history);
        let prompt = build_context_prompt(&mcp);

        assert!(!prompt.contains("[user]: \n"));
        assert!(prompt.contains("[user]: real message"));
    }

    #[test]
    fn history_is_capped_to_last_ten_interactions() {
        let args = HashMap::new();
        let history: Vec<Interaction> = (0..15)
            .map(|i| {
                interaction_with_messages(&serde_json::json!([
                    {"role": "user", "content": format!("msg-{i}")},
                ]))
            })
            .collect();
        let mcp = McpContext::new(TOOLS_CALL, None, &args, &[], &history);
        let prompt = build_context_prompt(&mcp);

        // The first five (msg-0..msg-4) are dropped; the last ten remain.
        assert!(!prompt.contains("msg-0"));
        assert!(!prompt.contains("msg-4"));
        assert!(prompt.contains("msg-5"));
        assert!(prompt.contains("msg-14"));
    }

    #[test]
    fn sanitizes_content_stripping_hashes_and_newlines() {
        let args = HashMap::new();
        let history = vec![interaction_with_messages(&serde_json::json!([
            {"role": "user", "content": "line1\nline2 ## header"},
        ]))];
        let mcp = McpContext::new(TOOLS_CALL, None, &args, &[], &history);
        let prompt = build_context_prompt(&mcp);

        // sanitize() removes '#' and replaces newlines with spaces.
        assert!(prompt.contains("[user]: line1 line2  header"));
        assert!(!prompt.contains("line1\nline2"));
    }

    #[test]
    fn unknown_role_falls_back_to_unknown() {
        let args = HashMap::new();
        let history = vec![interaction_with_messages(&serde_json::json!([
            {"content": "no role here"},
        ]))];
        let mcp = McpContext::new(TOOLS_CALL, None, &args, &[], &history);
        let prompt = build_context_prompt(&mcp);
        assert!(prompt.contains("[unknown]: no role here"));
    }

    #[tokio::test]
    async fn run_llm_operation_returns_successful_response() {
        let server = MockServer::start().await;
        let args = HashMap::new();
        let mcp = McpContext::new(TOOLS_CALL, Some("weather"), &args, &[], &[]);
        let def = llm_def();
        let connection = llm_connection(server.uri());
        mount_expected_chat_response(
            &server,
            &format!("## Request: {TOOLS_CALL}\n\nTool: weather\n"),
            "allow",
        )
        .await;

        let result = run_llm_operation(
            "security",
            ResolvedLlm {
                def: &def,
                connection: &connection,
            },
            &mcp,
            None,
        )
        .await;

        assert_eq!(result.as_deref(), Some("allow"));
    }

    #[tokio::test]
    async fn run_llm_operation_returns_empty_response() {
        let server = MockServer::start().await;
        mount_chat_response(&server, StatusCode::OK, "").await;
        let args = HashMap::new();
        let mcp = McpContext::new(TOOLS_LIST, None, &args, &[], &[]);
        let def = llm_def();
        let connection = llm_connection(server.uri());

        let result = run_llm_operation(
            "security",
            ResolvedLlm {
                def: &def,
                connection: &connection,
            },
            &mcp,
            None,
        )
        .await;

        assert_eq!(result.as_deref(), Some(""));
    }

    #[tokio::test]
    async fn run_llm_operation_returns_none_for_http_failure() {
        let server = MockServer::start().await;
        mount_chat_response(&server, StatusCode::INTERNAL_SERVER_ERROR, "ignored").await;
        let args = HashMap::new();
        let mcp = McpContext::new(TOOLS_CALL, None, &args, &[], &[]);
        let def = llm_def();
        let connection = llm_connection(server.uri());

        let result = run_llm_operation(
            "security",
            ResolvedLlm {
                def: &def,
                connection: &connection,
            },
            &mcp,
            None,
        )
        .await;

        assert!(result.is_none());
    }

    #[tokio::test]
    async fn run_llm_operation_records_success_failure_and_empty_metrics() {
        let success_server = MockServer::start().await;
        let empty_server = MockServer::start().await;
        let failure_server = MockServer::start().await;
        mount_chat_response(&success_server, StatusCode::OK, "allow").await;
        mount_chat_response(&empty_server, StatusCode::OK, "").await;
        mount_chat_response(&failure_server, StatusCode::SERVICE_UNAVAILABLE, "ignored").await;
        let args = HashMap::new();
        let mcp = McpContext::new(TOOLS_CALL, None, &args, &[], &[]);
        let def = llm_def();
        let metrics = MetricsStore::new();

        for server in [&success_server, &empty_server, &failure_server] {
            let connection = llm_connection(server.uri());
            let _ = run_llm_operation(
                "security",
                ResolvedLlm {
                    def: &def,
                    connection: &connection,
                },
                &mcp,
                Some(&metrics),
            )
            .await;
        }

        let snapshot = metrics.snapshot();
        let evaluator = &snapshot.evaluators["security"];
        assert_eq!(evaluator.llm.calls_success, 2);
        assert_eq!(evaluator.llm.calls_failure, 1);
        assert_eq!(evaluator.llm.empty_results, 2);
        assert_eq!(evaluator.llm.duration.count, 3);
    }

    #[tokio::test]
    async fn retry_with_schema_correction_sends_correction_and_returns_response() {
        let server = MockServer::start().await;
        let args = HashMap::new();
        let mcp = McpContext::new(TOOLS_CALL, None, &args, &[], &[]);
        let def = llm_def();
        let connection = llm_connection(server.uri());
        let schema = serde_json::json!({
            "type": "object",
            "required": ["decision"],
        });
        let expected_prompt = format!(
            "## Request: {TOOLS_CALL}\n\n\n\n## Correction\n\n\
             Your previous response did not match the expected JSON schema.\n\
             Validation error: required property `decision` is missing\n\
             Expected schema:\n```json\n{schema}\n```\n\
             Your response was:\n```\n{{\"answer\":true}}\n```\n\n\
             Provide a response that strictly matches the expected JSON schema."
        );
        mount_expected_chat_response(&server, &expected_prompt, r#"{"decision":"allow"}"#).await;

        let result = retry_with_schema_correction(
            ResolvedLlm {
                def: &def,
                connection: &connection,
            },
            &mcp,
            r#"{"answer":true}"#,
            &schema,
            "required property `decision` is missing",
        )
        .await;

        assert_eq!(result.as_deref(), Some(r#"{"decision":"allow"}"#));
    }
}
