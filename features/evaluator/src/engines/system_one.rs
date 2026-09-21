use serde::{Deserialize, Serialize};
use wanaku_types::{interactions::Interaction, mcp::McpContext};

use crate::state::EvaluatorState;

/// A named TypeSafe System One connection. Connections are loaded only from
/// `wanaku.yaml`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemOneConnection {
    pub name: String,
    #[serde(default = "default_model")]
    pub model: String,
    #[serde(default = "default_url")]
    pub url: String,
    pub api_key: String,
}

fn default_model() -> String {
    "jev-latest".to_owned()
}

fn default_url() -> String {
    "https://api.typesafe.ai".to_owned()
}

/// TypeSafe System One engine configuration. It currently supports Noul.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct SystemOneDef {
    pub connection: String,
    #[serde(default)]
    pub state: SystemOneState,
    pub noul: NoulDef,
}

/// Selects the MCP data that becomes the TypeSafe state.
#[derive(Debug, Clone, Copy, Serialize, Deserialize, Default)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "snake_case")]
pub enum SystemOneState {
    Arguments,
    #[default]
    Context,
}

/// A TypeSafe Noul primitive.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct NoulDef {
    pub id: String,
    pub instructions: serde_json::Value,
    #[serde(default)]
    pub criteria: Option<NoulCriteria>,
}

/// Optional descriptions for Noul true and false outcomes.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct NoulCriteria {
    #[serde(rename = "true")]
    pub yes: serde_json::Value,
    #[serde(rename = "false")]
    pub no: serde_json::Value,
}

/// Execute TypeSafe System One and normalize its response for a WASM processor.
pub async fn execute(
    definition: &SystemOneDef,
    state: &EvaluatorState,
    mcp: &McpContext<'_>,
) -> Result<String, String> {
    let connection = state
        .get_system_one_connection(&definition.connection)
        .ok_or_else(|| {
            format!(
                "evaluator TypeSafe System One connection '{}' not available",
                definition.connection
            )
        })?;
    let client = wanaku_infra::typesafe::SystemOneClient::new(
        &connection.url,
        &connection.model,
        &connection.api_key,
    )
    .ok_or_else(|| "failed to initialize TypeSafe System One client".to_owned())?;
    let response = client
        .evaluate(
            &request_state(definition.state, mcp),
            &definition.noul.id,
            &noul_question(&definition.noul),
        )
        .await?;
    normalize_noul_response(&definition.noul.id, &response)
}

fn request_state(mapping: SystemOneState, mcp: &McpContext<'_>) -> serde_json::Value {
    let history = history_messages(mcp.history);
    match mapping {
        SystemOneState::Arguments => {
            if history.is_empty() {
                serde_json::json!(mcp.arguments)
            } else {
                serde_json::json!({
                "arguments": mcp.arguments,
                "history": history,
                })
            }
        }
        SystemOneState::Context => {
            let mut state = serde_json::json!({
                "method": mcp.method,
                "tool_name": mcp.tool_name,
                "arguments": mcp.arguments,
                "tools": mcp.tools,
            });
            if !history.is_empty() {
                state["history"] = serde_json::json!(history);
            }
            state
        }
    }
}

fn history_messages(history: &[Interaction]) -> Vec<serde_json::Value> {
    let mut messages = Vec::new();
    for interaction in history {
        let Some(history_messages) = interaction
            .request_body
            .get("messages")
            .and_then(serde_json::Value::as_array)
        else {
            continue;
        };
        for message in history_messages {
            let Some(content) = message.get("content").and_then(serde_json::Value::as_str) else {
                continue;
            };
            if content.is_empty() {
                continue;
            }
            let role = message
                .get("role")
                .and_then(serde_json::Value::as_str)
                .unwrap_or("unknown");
            messages.push(serde_json::json!({ "role": role, "content": content }));
        }
    }
    messages
}

fn noul_question(definition: &NoulDef) -> serde_json::Value {
    let mut question = serde_json::json!({
        "type": "noul",
        "instructions": definition.instructions,
    });
    if let Some(criteria) = &definition.criteria {
        question["criteria"] = serde_json::json!({
            "true": criteria.yes,
            "false": criteria.no,
        });
    }
    question
}

fn normalize_noul_response(
    question_id: &str,
    response: &serde_json::Value,
) -> Result<String, String> {
    let answer = response
        .pointer(&format!("/answers/{question_id}"))
        .ok_or_else(|| format!("TypeSafe System One response has no answer for '{question_id}'"))?;
    let probability = answer
        .get("noul")
        .and_then(serde_json::Value::as_f64)
        .filter(|value| (0.0..=1.0).contains(value))
        .ok_or_else(|| {
            format!("TypeSafe System One answer '{question_id}' has an invalid noul value")
        })?;
    serde_json::to_string(&serde_json::json!({
        "engine": "typesafe-system-one",
        "primitive": { "id": question_id, "type": "noul" },
        "answer": { "noul": probability },
        "probabilities": { "true": probability, "false": 1.0 - probability },
    }))
    .map_err(|error| format!("failed to serialize normalized System One result: {error}"))
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;

    use wanaku_types::interactions::Interaction;
    use wiremock::matchers::{body_json, method, path};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    use super::{
        NoulDef, SystemOneConnection, SystemOneDef, SystemOneState, execute,
        normalize_noul_response, request_state,
    };
    use crate::state::EvaluatorState;

    fn interaction(messages: &serde_json::Value) -> Interaction {
        Interaction {
            epoch_ms: 0,
            path: "/v1/chat/completions".to_owned(),
            conversation_id: Some("wk-test".to_owned()),
            completion_id: None,
            model: None,
            request_body: serde_json::json!({ "messages": messages }),
            response_body: serde_json::Value::Null,
            status_code: 200,
            duration_ms: 0,
        }
    }

    fn context<'a>(
        arguments: &'a HashMap<String, String>,
        history: &'a [Interaction],
    ) -> wanaku_types::mcp::McpContext<'a> {
        wanaku_types::mcp::McpContext::new("tools/call", Some("weather"), arguments, &[], history)
    }

    fn evaluator_state(url: String) -> EvaluatorState {
        let state = EvaluatorState::new();
        assert!(
            state
                .load_system_one_connections(vec![SystemOneConnection {
                    name: "typesafe".to_owned(),
                    model: "jev-latest".to_owned(),
                    url,
                    api_key: "test-key".to_owned(),
                }])
                .is_ok()
        );
        state
    }

    fn definition() -> SystemOneDef {
        SystemOneDef {
            connection: "typesafe".to_owned(),
            state: SystemOneState::Context,
            noul: NoulDef {
                id: "is_safe".to_owned(),
                instructions: serde_json::json!("Is this safe?"),
                criteria: None,
            },
        }
    }

    async fn mount_system_one_response(server: &MockServer, state: serde_json::Value) {
        Mock::given(method("POST"))
            .and(path("/v1/systemone"))
            .and(body_json(serde_json::json!({
                "state": state,
                "model": "jev-latest",
                "questions": {
                    "is_safe": {
                        "type": "noul",
                        "instructions": "Is this safe?",
                    },
                },
            })))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "answers": { "is_safe": { "noul": 0.9 } },
            })))
            .expect(1)
            .mount(server)
            .await;
    }

    #[test]
    fn normalizes_noul_response_for_the_processor() {
        let response = serde_json::json!({ "answers": { "allowed": { "noul": 0.8 } } });
        let normalized =
            normalize_noul_response("allowed", &response).expect("response normalizes");
        let normalized: serde_json::Value =
            serde_json::from_str(&normalized).expect("normalized JSON");
        assert_eq!(normalized["engine"], "typesafe-system-one");
        assert_eq!(normalized["primitive"]["id"], "allowed");
        assert_eq!(normalized["answer"]["noul"], 0.8);
        let false_probability = normalized["probabilities"]["false"]
            .as_f64()
            .expect("false probability");
        assert!((false_probability - 0.2).abs() < f64::EPSILON);
    }

    #[test]
    fn rejects_invalid_noul_response() {
        let response = serde_json::json!({ "answers": { "allowed": { "noul": 2.0 } } });
        assert!(normalize_noul_response("allowed", &response).is_err());
    }

    #[test]
    fn context_state_uses_chronological_role_content_history() {
        let mut arguments = HashMap::new();
        arguments.insert("city".to_owned(), "Berlin".to_owned());
        let history = vec![
            interaction(&serde_json::json!([
                { "role": "user", "content": "first request" },
                { "role": "assistant", "content": "first response" },
            ])),
            interaction(&serde_json::json!([
                { "role": "user", "content": "second request" },
                { "content": "unknown role" },
                { "role": "assistant", "content": "" },
            ])),
        ];

        let state = request_state(SystemOneState::Context, &context(&arguments, &history));

        assert_eq!(state["method"], "tools/call");
        assert_eq!(state["arguments"]["city"], "Berlin");
        let expected_history = serde_json::json!([
            { "role": "user", "content": "first request" },
            { "role": "assistant", "content": "first response" },
            { "role": "user", "content": "second request" },
            { "role": "unknown", "content": "unknown role" },
        ]);
        assert_eq!(state["history"], expected_history);
        assert_eq!(
            request_state(SystemOneState::Arguments, &context(&arguments, &history)),
            serde_json::json!({
                "arguments": { "city": "Berlin" },
                "history": expected_history,
            })
        );
    }

    #[test]
    fn omits_history_and_preserves_state_shape_without_usable_messages() {
        let mut arguments = HashMap::new();
        arguments.insert("city".to_owned(), "Berlin".to_owned());
        let history = vec![interaction(&serde_json::json!([
            { "role": "user", "content": "" },
            { "role": "assistant", "content": null },
        ]))];

        let mcp = context(&arguments, &history);
        assert_eq!(
            request_state(SystemOneState::Arguments, &mcp),
            serde_json::json!({ "city": "Berlin" })
        );
        assert_eq!(
            request_state(SystemOneState::Context, &mcp),
            serde_json::json!({
                "method": "tools/call",
                "tool_name": "weather",
                "arguments": { "city": "Berlin" },
                "tools": [],
            })
        );
    }

    #[tokio::test]
    async fn sends_normalized_history_to_system_one() {
        let server = MockServer::start().await;
        mount_system_one_response(
            &server,
            serde_json::json!({
                "method": "tools/call",
                "tool_name": "weather",
                "arguments": { "city": "Berlin" },
                "tools": [],
                "history": [
                    { "role": "user", "content": "Will it rain?" },
                    { "role": "assistant", "content": "Yes." },
                ],
            }),
        )
        .await;

        let state = evaluator_state(server.uri());
        let definition = definition();
        let mut arguments = HashMap::new();
        arguments.insert("city".to_owned(), "Berlin".to_owned());
        let history = vec![interaction(&serde_json::json!([
            { "role": "user", "content": "Will it rain?" },
            { "role": "assistant", "content": "Yes." },
        ]))];

        let result = execute(&definition, &state, &context(&arguments, &history)).await;

        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn omits_history_from_system_one_request_when_absent() {
        let server = MockServer::start().await;
        mount_system_one_response(
            &server,
            serde_json::json!({
                "method": "tools/call",
                "tool_name": "weather",
                "arguments": { "city": "Berlin" },
                "tools": [],
            }),
        )
        .await;

        let state = evaluator_state(server.uri());
        let definition = definition();
        let mut arguments = HashMap::new();
        arguments.insert("city".to_owned(), "Berlin".to_owned());

        let result = execute(&definition, &state, &context(&arguments, &[])).await;

        assert!(result.is_ok());
    }
}
