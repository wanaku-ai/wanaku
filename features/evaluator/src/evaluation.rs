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
    Internal,
}

impl EvaluationError {
    pub const fn reason_code(self) -> &'static str {
        match self {
            Self::MissingConnection => "evaluator_connection_unavailable",
            Self::Llm => "evaluator_llm_failed",
            Self::Schema => "evaluator_schema_invalid",
            Self::Remote => "evaluator_remote_engine_failed",
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
    let result =
        crate::engines::llm::run_llm_operation(evaluator_name, llm, context.mcp, context.metrics)
            .await;
    let result = nonempty_llm_result(result)?;
    validate_and_retry(evaluator_name, llm, context, &result).await
}

fn nonempty_llm_result(result: Option<String>) -> Result<String, EvaluationError> {
    result
        .filter(|value| !value.trim().is_empty())
        .ok_or(EvaluationError::Llm)
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
    let validated = retry
        .filter(|retried| schema.validate(retried).is_ok())
        .ok_or(EvaluationError::Schema);
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
    fn empty_and_whitespace_llm_responses_are_failures() {
        for response in [None, Some(String::new()), Some(" \n\t".to_owned())] {
            assert_eq!(nonempty_llm_result(response), Err(EvaluationError::Llm));
        }
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
                compiled_schema: Some(&schema),
                metrics: None,
            },
            "not JSON",
        )
        .await;
        assert_eq!(result, Err(EvaluationError::Schema));
    }
}
