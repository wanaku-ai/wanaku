use wanaku_infra::llm::LlmError;
use wanaku_infra::metrics::MetricsStore;
use wanaku_types::mcp::McpContext;

use crate::config::{EvaluationEngine, EvaluatorDef, LlmDef};
use crate::schema::CompiledSchema;
use crate::state::EvaluatorState;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EvaluationError {
    MissingConnection,
    Llm,
    Schema,
    Remote,
    RemoteUnavailable,
    RemoteTimeout,
    RemoteAuth,
    Policy,
    DecisionUndefined,
    DecisionInvalid,
    Internal,
}

impl EvaluationError {
    pub const fn reason_code(self) -> &'static str {
        match self {
            Self::MissingConnection => "evaluator_connection_unavailable",
            Self::Llm => "evaluator_llm_failed",
            Self::Schema => "evaluator_schema_invalid",
            Self::Remote => "evaluator_remote_engine_failed",
            Self::RemoteUnavailable => "evaluator_remote_unavailable",
            Self::RemoteTimeout => "evaluator_remote_timeout",
            Self::RemoteAuth => "evaluator_remote_auth_failed",
            Self::Policy => "evaluator_policy_error",
            Self::DecisionUndefined => "evaluator_decision_undefined",
            Self::DecisionInvalid => "evaluator_decision_invalid",
            Self::Internal => "evaluator_internal_error",
        }
    }
}

impl std::fmt::Display for EvaluationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.reason_code())
    }
}

pub struct EvaluationContext<'a, 'm> {
    pub state: &'a EvaluatorState,
    pub mcp: &'a McpContext<'m>,
    pub namespace: &'a str,
    /// MCP arguments with their original JSON types.
    pub arguments: &'a serde_json::Value,
    pub compiled_schema: Option<&'a CompiledSchema>,
    pub metrics: Option<&'a MetricsStore>,
}

/// Execute the selected engine and return the normalized processor input.
pub async fn execute(
    evaluator: &EvaluatorDef,
    engine: &EvaluationEngine,
    context: &EvaluationContext<'_, '_>,
) -> Result<String, EvaluationError> {
    let start = std::time::Instant::now();
    let result = match engine {
        EvaluationEngine::Llm(def) => execute_llm(&evaluator.name, def, context).await,
        EvaluationEngine::TypesafeSystemOne(def) => execute_system_one(def, context).await,
        EvaluationEngine::Opa(def) => execute_opa(def, context).await,
        EvaluationEngine::Passthrough => passthrough_input(context.mcp),
    };
    if let Some(store) = context.metrics {
        store.record_evaluation_engine(
            &evaluator.name,
            engine.kind(),
            result.is_ok(),
            start.elapsed(),
        );
    }
    result
}

async fn execute_system_one(
    definition: &crate::config::SystemOneDef,
    context: &EvaluationContext<'_, '_>,
) -> Result<String, EvaluationError> {
    context
        .state
        .get_system_one_connection(&definition.connection)
        .ok_or(EvaluationError::MissingConnection)?;
    crate::engines::system_one::execute(definition, context.state, context.mcp)
        .await
        .map_err(|_| EvaluationError::Remote)
}

async fn execute_opa(
    definition: &crate::config::OpaDef,
    context: &EvaluationContext<'_, '_>,
) -> Result<String, EvaluationError> {
    let client = context
        .state
        .get_opa_client(&definition.connection)
        .ok_or(EvaluationError::MissingConnection)?;
    let input = crate::engines::opa::input(
        context.mcp.method,
        context.namespace,
        context.mcp.tool_name,
        context.arguments,
    );
    crate::engines::opa::execute(definition, &client, &input).await
}

pub(crate) fn passthrough_input(mcp: &McpContext<'_>) -> Result<String, EvaluationError> {
    serde_json::to_string(&serde_json::json!({
        "method": mcp.method,
        "tool_name": mcp.tool_name,
        "arguments": mcp.arguments,
        "tools": mcp.tools,
        "history": mcp.history,
    }))
    .map_err(|_| EvaluationError::Internal)
}

pub const fn requires_tools(engine: &EvaluationEngine) -> bool {
    matches!(
        engine,
        EvaluationEngine::Llm(LlmDef {
            operation: crate::config::LlmOperation::Filter,
            ..
        })
    )
}

async fn execute_llm(
    evaluator_name: &str,
    definition: &LlmDef,
    context: &EvaluationContext<'_, '_>,
) -> Result<String, EvaluationError> {
    let connection = context
        .state
        .get_llm_connection(&definition.connection)
        .ok_or(EvaluationError::MissingConnection)?;
    let llm = crate::engines::llm::ResolvedLlm {
        def: definition,
        connection: &connection,
    };
    // Only successful output reaches schema validation and its retry.
    let result =
        crate::engines::llm::run_llm_operation(evaluator_name, llm, context.mcp, context.metrics)
            .await
            .map_err(llm_failure)?;
    validate_and_retry(evaluator_name, llm, context, &result).await
}

/// Map an LLM client failure to a safe evaluator failure category.
const fn llm_failure(error: LlmError) -> EvaluationError {
    match error {
        LlmError::Timeout => EvaluationError::RemoteTimeout,
        LlmError::Connection => EvaluationError::RemoteUnavailable,
        LlmError::Auth(_) => EvaluationError::RemoteAuth,
        LlmError::Client
        | LlmError::Status(_)
        | LlmError::MalformedJson
        | LlmError::MissingContent
        | LlmError::BlankContent => EvaluationError::Llm,
    }
}

async fn validate_and_retry(
    evaluator_name: &str,
    llm: crate::engines::llm::ResolvedLlm<'_>,
    context: &EvaluationContext<'_, '_>,
    result: &str,
) -> Result<String, EvaluationError> {
    let Some(schema) = context.compiled_schema else {
        return Ok(result.to_owned());
    };
    let validation = schema.validate(result);
    if let Some(store) = context.metrics {
        store.record_schema_validation(evaluator_name, validation.is_ok());
    }
    match validation {
        Ok(()) => Ok(result.to_owned()),
        Err(error) => retry_invalid_result(evaluator_name, llm, context, result, &error).await,
    }
}

async fn retry_invalid_result(
    evaluator_name: &str,
    llm: crate::engines::llm::ResolvedLlm<'_>,
    context: &EvaluationContext<'_, '_>,
    result: &str,
    validation_error: &str,
) -> Result<String, EvaluationError> {
    let raw_schema = llm
        .def
        .result_schema
        .as_ref()
        .ok_or(EvaluationError::Schema)?;
    let schema = context.compiled_schema.ok_or(EvaluationError::Schema)?;
    let retry = crate::engines::llm::retry_with_schema_correction(
        llm,
        context.mcp,
        result,
        raw_schema,
        validation_error,
    )
    .await;
    let validated = retry.map_err(llm_failure).and_then(|retried| {
        schema
            .validate(&retried)
            .map(|()| retried)
            .map_err(|_| EvaluationError::Schema)
    });
    if let Some(store) = context.metrics {
        store.record_schema_retry(evaluator_name, validated.is_ok());
    }
    validated
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    #[tokio::test]
    async fn passthrough_returns_normalized_context_without_llm_connection() {
        let args = HashMap::from([("city".to_owned(), "Berlin".to_owned())]);
        let context = McpContext::new("tools/call", Some("weather"), &args, &[], &[]);
        let evaluator = EvaluatorDef {
            name: "pass".to_owned(),
            trigger: crate::config::TriggerDef {
                method: "tools/call".to_owned(),
                namespace: None,
            },
            engine: EvaluationEngine::Passthrough,
            processor: crate::config::ProcessorRef {
                path: "/unused.wasm".into(),
            },
        };
        let result = execute(
            &evaluator,
            &EvaluationEngine::Passthrough,
            &EvaluationContext {
                state: &EvaluatorState::new(),
                mcp: &context,
                namespace: "default",
                arguments: &serde_json::json!({ "city": "Berlin" }),
                compiled_schema: None,
                metrics: None,
            },
        )
        .await
        .expect("pass-through result");
        assert_eq!(
            serde_json::from_str::<serde_json::Value>(&result).expect("JSON")["arguments"]["city"],
            "Berlin"
        );
    }

    #[test]
    fn llm_failures_map_to_safe_categories() {
        for (error, expected) in [
            (LlmError::Timeout, EvaluationError::RemoteTimeout),
            (LlmError::Connection, EvaluationError::RemoteUnavailable),
            (LlmError::Auth(401), EvaluationError::RemoteAuth),
            (LlmError::Auth(403), EvaluationError::RemoteAuth),
            (LlmError::Status(500), EvaluationError::Llm),
            (LlmError::MalformedJson, EvaluationError::Llm),
            (LlmError::MissingContent, EvaluationError::Llm),
            (LlmError::BlankContent, EvaluationError::Llm),
            (LlmError::Client, EvaluationError::Llm),
        ] {
            assert_eq!(llm_failure(error), expected);
        }
    }

    /// Run an LLM evaluator with a result schema against one mock response
    /// per call. The mock verifies the exact number of LLM calls.
    #[expect(
        clippy::too_many_lines,
        reason = "complete LLM state, schema, and mock server setup"
    )]
    async fn execute_llm_with_schema(
        responses: Vec<wiremock::ResponseTemplate>,
    ) -> (Result<String, EvaluationError>, MetricsStore) {
        use wiremock::matchers::{method, path};
        use wiremock::{Mock, MockServer};

        let server = MockServer::start().await;
        for response in responses {
            Mock::given(method("POST"))
                .and(path("/chat/completions"))
                .respond_with(response)
                .up_to_n_times(1)
                .expect(1)
                .mount(&server)
                .await;
        }
        let raw_schema = serde_json::json!({
            "type": "object",
            "properties": {"allowed": {"type": "boolean"}},
            "required": ["allowed"]
        });
        let schema = CompiledSchema::compile(&raw_schema).expect("valid schema");
        let definition = LlmDef {
            operation: crate::config::LlmOperation::Classify,
            prompt: "classify".to_owned(),
            connection: "test".to_owned(),
            result_schema: Some(raw_schema),
        };
        let state = EvaluatorState::new();
        state
            .load_llm_connections(vec![crate::config::LlmConnection {
                name: "test".to_owned(),
                model: "test".to_owned(),
                url: server.uri(),
                api_key: String::new(),
            }])
            .expect("valid connection");
        let arguments = HashMap::new();
        let mcp = McpContext::new("tools/call", Some("test"), &arguments, &[], &[]);
        let metrics = MetricsStore::new();
        let result = execute_llm(
            "test",
            &definition,
            &EvaluationContext {
                state: &state,
                mcp: &mcp,
                namespace: "default",
                arguments: &serde_json::json!({}),
                compiled_schema: Some(&schema),
                metrics: Some(&metrics),
            },
        )
        .await;
        server.verify().await;
        (result, metrics)
    }

    fn content(value: &str) -> wiremock::ResponseTemplate {
        wiremock::ResponseTemplate::new(200)
            .set_body_json(serde_json::json!({"choices": [{"message": {"content": value}}]}))
    }

    #[tokio::test]
    #[expect(
        clippy::too_many_lines,
        reason = "failure category matrix and the retry case stay together"
    )]
    async fn only_schema_invalid_output_triggers_schema_correction() {
        for (response, expected) in [
            (
                wiremock::ResponseTemplate::new(401),
                EvaluationError::RemoteAuth,
            ),
            (wiremock::ResponseTemplate::new(503), EvaluationError::Llm),
            (
                wiremock::ResponseTemplate::new(200).set_body_string("not json"),
                EvaluationError::Llm,
            ),
            (
                wiremock::ResponseTemplate::new(200).set_body_json(serde_json::json!({})),
                EvaluationError::Llm,
            ),
            (content(" "), EvaluationError::Llm),
        ] {
            // One mounted response: a correction request would fail verification.
            let (result, metrics) = execute_llm_with_schema(vec![response]).await;
            assert_eq!(result, Err(expected));
            let snapshot = metrics.snapshot();
            let schema = &snapshot.evaluators["test"].schema;
            assert_eq!(schema.validations_pass + schema.validations_fail, 0);
            assert_eq!(schema.retries_success + schema.retries_failure, 0);
        }

        let (result, metrics) = execute_llm_with_schema(vec![
            content(r#"{"allowed":"invalid"}"#),
            content(r#"{"allowed":true}"#),
        ])
        .await;
        assert_eq!(result.as_deref(), Ok(r#"{"allowed":true}"#));
        assert_eq!(
            metrics.snapshot().evaluators["test"].schema.retries_success,
            1
        );
    }

    #[tokio::test]
    async fn failed_correction_call_keeps_its_failure_category() {
        let (result, _) = execute_llm_with_schema(vec![
            content(r#"{"allowed":"invalid"}"#),
            wiremock::ResponseTemplate::new(403),
        ])
        .await;
        assert_eq!(result, Err(EvaluationError::RemoteAuth));
    }

    #[tokio::test]
    #[expect(
        clippy::too_many_lines,
        reason = "complete initial-response and correction HTTP scenario"
    )]
    async fn exhausted_schema_correction_returns_failure_instead_of_invalid_result() {
        use wiremock::matchers::{method, path};
        use wiremock::{Mock, MockServer, ResponseTemplate};

        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/chat/completions"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "choices": [{"message": {"content": "{\"allowed\":\"invalid\"}"}}]
            })))
            .expect(1)
            .mount(&server)
            .await;
        let raw_schema = serde_json::json!({
            "type": "object",
            "properties": {"allowed": {"type": "boolean"}},
            "required": ["allowed"]
        });
        let schema = CompiledSchema::compile(&raw_schema).expect("valid schema");
        let definition = LlmDef {
            operation: crate::config::LlmOperation::Classify,
            prompt: "classify".to_owned(),
            connection: "test".to_owned(),
            result_schema: Some(raw_schema),
        };
        let connection = crate::config::LlmConnection {
            name: "test".to_owned(),
            model: "test".to_owned(),
            url: server.uri(),
            api_key: String::new(),
        };
        let arguments = HashMap::new();
        let mcp = McpContext::new("tools/call", Some("test"), &arguments, &[], &[]);
        let state = EvaluatorState::new();
        let result = validate_and_retry(
            "test",
            crate::engines::llm::ResolvedLlm {
                def: &definition,
                connection: &connection,
            },
            &EvaluationContext {
                state: &state,
                mcp: &mcp,
                namespace: "default",
                arguments: &serde_json::json!({}),
                compiled_schema: Some(&schema),
                metrics: None,
            },
            "not JSON",
        )
        .await;
        assert_eq!(result, Err(EvaluationError::Schema));
    }
}
