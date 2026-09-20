use wanaku_infra::metrics::MetricsStore;
use wanaku_types::mcp::McpContext;

use crate::config::{EvaluationEngine, EvaluatorDef, LlmDef};
use crate::schema::CompiledSchema;
use crate::state::EvaluatorState;

/// Execute the selected engine and return the normalized processor input.
pub async fn execute(
    evaluator: &EvaluatorDef,
    engine: &EvaluationEngine,
    state: &EvaluatorState,
    mcp: &McpContext<'_>,
    compiled_schema: Option<&CompiledSchema>,
    metrics: Option<&MetricsStore>,
) -> Result<String, String> {
    let start = std::time::Instant::now();
    let (engine_name, result) = match engine {
        EvaluationEngine::Llm(def) => (
            "llm",
            execute_llm(evaluator, def, state, mcp, compiled_schema, metrics).await,
        ),
        EvaluationEngine::TypesafeSystemOne(def) => (
            "typesafe-system-one",
            crate::engines::system_one::execute(def, state, mcp).await,
        ),
        EvaluationEngine::Passthrough => (
            "passthrough",
            serde_json::to_string(&serde_json::json!({
                "method": mcp.method,
                "tool_name": mcp.tool_name,
                "arguments": mcp.arguments,
                "tools": mcp.tools,
                "history": mcp.history,
            }))
            .map_err(|error| format!("failed to serialize passthrough context: {error}")),
        ),
    };
    if let Some(store) = metrics {
        store.record_evaluation_engine(
            &evaluator.name,
            engine_name,
            result.is_ok(),
            start.elapsed(),
        );
    }
    result
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
    evaluator: &EvaluatorDef,
    definition: &LlmDef,
    state: &EvaluatorState,
    mcp: &McpContext<'_>,
    compiled_schema: Option<&CompiledSchema>,
    metrics: Option<&MetricsStore>,
) -> Result<String, String> {
    let connection = state
        .get_llm_connection(&definition.connection)
        .ok_or_else(|| {
            format!(
                "evaluator LLM connection '{}' not available",
                definition.connection
            )
        })?;
    let result = crate::engines::llm::run_llm_operation(
        &evaluator.name,
        crate::engines::llm::ResolvedLlm {
            def: definition,
            connection: &connection,
        },
        mcp,
        metrics,
    )
    .await;
    let result = result
        .filter(|value| !value.is_empty())
        .ok_or_else(|| "evaluator LLM operation failed".to_owned())?;

    validate_and_retry(
        &evaluator.name,
        definition,
        &connection,
        mcp,
        &result,
        compiled_schema,
        metrics,
    )
    .await
}

async fn validate_and_retry(
    evaluator_name: &str,
    definition: &LlmDef,
    connection: &crate::config::LlmConnection,
    mcp: &McpContext<'_>,
    result: &str,
    compiled_schema: Option<&CompiledSchema>,
    metrics: Option<&MetricsStore>,
) -> Result<String, String> {
    let Some(schema) = compiled_schema else {
        return Ok(result.to_owned());
    };
    let validation_error = match schema.validate(result) {
        Ok(()) => {
            if let Some(store) = metrics {
                store.record_schema_validation(evaluator_name, true);
            }
            return Ok(result.to_owned());
        }
        Err(error) => error,
    };
    if let Some(store) = metrics {
        store.record_schema_validation(evaluator_name, false);
    }
    let Some(raw_schema) = definition.result_schema.as_ref() else {
        return Ok(result.to_owned());
    };
    let retry = crate::engines::llm::retry_with_schema_correction(
        crate::engines::llm::ResolvedLlm {
            def: definition,
            connection,
        },
        mcp,
        result,
        raw_schema,
        &validation_error,
    )
    .await;
    match retry {
        Some(retried) if schema.validate(&retried).is_ok() => {
            if let Some(store) = metrics {
                store.record_schema_retry(evaluator_name, true);
            }
            Ok(retried)
        }
        _ => {
            if let Some(store) = metrics {
                store.record_schema_retry(evaluator_name, false);
            }
            Ok(result.to_owned())
        }
    }
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
            on_error: crate::config::ErrorPolicy::Continue,
        };
        let result = execute(
            &evaluator,
            &EvaluationEngine::Passthrough,
            &EvaluatorState::new(),
            &context,
            None,
            None,
        )
        .await
        .expect("pass-through result");
        assert_eq!(
            serde_json::from_str::<serde_json::Value>(&result).expect("JSON")["arguments"]["city"],
            "Berlin"
        );
    }
}
