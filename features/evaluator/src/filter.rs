use std::collections::HashMap;

use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use wanaku_praxis_apis::interactions::{InMemoryInteractionStore, InteractionStore};
use wanaku_praxis_apis::registry::{InMemoryRegistry, ToolRegistry};

use crate::action::ActionResult;
use crate::config::{ErrorPolicy, EvaluatorDef, LlmOperation};
use crate::state::EvaluatorState;

wanaku_praxis_filters::body_filter_boilerplate!(EvaluatorFilter, "wanaku_evaluator");

impl EvaluatorFilter {
    #[expect(clippy::too_many_lines, clippy::cognitive_complexity, clippy::large_stack_frames, reason = "evaluator pipeline with multiple validation steps")]
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let method = match ctx.get_metadata(wanaku_praxis_filters::MCP_METHOD_KEY) {
            Some(m) => m.to_owned(),
            None => return Ok(FilterAction::Continue),
        };

        let state = match ctx.extensions.get::<EvaluatorState>() {
            Some(s) => s.clone(),
            None => return Ok(FilterAction::Continue),
        };

        let namespace = ctx
            .get_metadata(wanaku_praxis_apis::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE)
            .to_owned();

        let Some(evaluator) = state.find_matching(&method, &namespace) else {
            return Ok(FilterAction::Continue);
        };

        tracing::info!(
            evaluator = %evaluator.name,
            method = %method,
            namespace = %namespace,
            "evaluator triggered"
        );

        let registry = match ctx.extensions.get::<InMemoryRegistry>() {
            Some(r) => r.clone(),
            None => return Ok(FilterAction::Continue),
        };

        let interactions = match ctx.extensions.get::<InMemoryInteractionStore>() {
            Some(s) => s.clone(),
            None => return Ok(FilterAction::Continue),
        };

        let tool_name = ctx
            .get_metadata(wanaku_praxis_filters::MCP_NAME_KEY)
            .map(std::borrow::ToOwned::to_owned);

        let arguments = parse_arguments(body);

        let conversation_id = arguments
            .get(wanaku_praxis_apis::correlation::REQUEST_ID_ARG)
            .cloned()
            .or_else(|| state.get_binding(&namespace));

        let history = conversation_id
            .as_ref()
            .map(|id| interactions.get_by_conversation_id(id))
            .unwrap_or_default();

        let tools = match evaluator.llm.operation {
            LlmOperation::Filter => registry.list_tools(),
            _ => Vec::new(),
        };

        let raw_llm_result = crate::llm_op::run_llm_operation(
            &evaluator.llm,
            &method,
            tool_name.as_deref(),
            &arguments,
            &tools,
            &history,
        )
        .await
        .unwrap_or_default();

        let compiled_schema = state.get_compiled_schema(&evaluator.name);

        let llm_result = validate_and_retry_if_needed(
            &raw_llm_result,
            &evaluator,
            compiled_schema.as_ref(),
            &method,
            tool_name.as_deref(),
            &arguments,
            &tools,
            &history,
        )
        .await;

        tracing::info!(
            evaluator = %evaluator.name,
            llm_result = %llm_result,
            "LLM operation result"
        );

        let Some(compiled) = state.get_compiled(&evaluator.processor.path) else {
            tracing::warn!(
                evaluator = %evaluator.name,
                path = %evaluator.processor.path.display(),
                "WASM processor not found or not compiled"
            );
            return match evaluator.on_error {
                ErrorPolicy::Continue => Ok(FilterAction::Continue),
                ErrorPolicy::Block => Ok(wanaku_praxis_filters::response::json_rpc_error(
                    &wanaku_praxis_filters::response::extract_json_rpc_id(body),
                    -32603,
                    "evaluator processor module not available",
                )),
            };
        };

        let eval_ctx = crate::host::types::EvaluationContext {
            method: method.clone(),
            namespace: namespace.clone(),
            tool_name,
            arguments: arguments.into_iter().collect(),
            llm_result,
            conversation_id,
        };

        let result = compiled.evaluate(
            registry,
            interactions,
            eval_ctx,
            compiled_schema,
        );

        tracing::info!(
            evaluator = %evaluator.name,
            action = ?result,
            "processor result"
        );

        dispatch_action(ctx, body, result, &method, &evaluator.name)
    }
}

#[expect(clippy::too_many_lines, clippy::cognitive_complexity, reason = "action dispatch with multiple variants")]
fn dispatch_action(
    ctx: &mut HttpFilterContext<'_>,
    body: &mut Option<Bytes>,
    result: ActionResult,
    method: &str,
    evaluator_name: &str,
) -> Result<FilterAction, FilterError> {
    let json_rpc_id = wanaku_praxis_filters::response::extract_json_rpc_id(body);

    match result {
        ActionResult::Pass => Ok(FilterAction::Continue),
        ActionResult::Block(reason) => {
            tracing::warn!(evaluator = %evaluator_name, reason = %reason, "request blocked");
            Ok(wanaku_praxis_filters::response::json_rpc_error(
                &json_rpc_id,
                -32001,
                &format!("blocked by evaluator {evaluator_name}: {reason}"),
            ))
        }
        ActionResult::RejectMalformed(reason) => {
            tracing::warn!(evaluator = %evaluator_name, reason = %reason, "rejected: malformed input");
            Ok(wanaku_praxis_filters::response::json_rpc_error(
                &json_rpc_id,
                -32002,
                &format!("evaluator {evaluator_name}: malformed input — {reason}"),
            ))
        }
        ActionResult::Warn(message) => {
            tracing::warn!(evaluator = %evaluator_name, message = %message, "evaluator warning");
            ctx.set_metadata(
                format!("wanaku.evaluator.{evaluator_name}.warning"),
                &message,
            );
            Ok(FilterAction::Continue)
        }
        ActionResult::FilterTools(tool_names) => {
            if method != "tools/list" {
                tracing::warn!(evaluator = %evaluator_name, "filter_tools called on non-tools/list, ignoring");
                return Ok(FilterAction::Continue);
            }

            let registry = ctx.extensions.get::<InMemoryRegistry>();
            let mcp_tools: Vec<serde_json::Value> = tool_names
                .iter()
                .filter_map(|name| {
                    registry.and_then(|r| r.get_tool(name)).map(|t| {
                        serde_json::json!({
                            "name": t.name,
                            "description": t.description,
                            "inputSchema": t.input_schema,
                        })
                    })
                })
                .collect();

            let response = serde_json::json!({
                "jsonrpc": "2.0",
                "id": json_rpc_id,
                "result": { "tools": mcp_tools }
            });
            Ok(FilterAction::Reject(
                wanaku_praxis_filters::response::json_response(Bytes::from(response.to_string())),
            ))
        }
        ActionResult::SetMetadata(key, value) => {
            ctx.set_metadata(&key, &value);
            Ok(FilterAction::Continue)
        }
    }
}

#[expect(clippy::too_many_arguments, reason = "retry requires original request context")]
#[expect(clippy::too_many_lines, clippy::cognitive_complexity, reason = "schema validation with retry logic")]
async fn validate_and_retry_if_needed(
    raw_result: &str,
    evaluator: &EvaluatorDef,
    compiled_schema: Option<&std::sync::Arc<crate::schema::CompiledSchema>>,
    method: &str,
    tool_name: Option<&str>,
    arguments: &HashMap<String, String>,
    tools: &[wanaku_praxis_apis::registry::ToolEntry],
    history: &[wanaku_praxis_apis::interactions::Interaction],
) -> String {
    let Some(schema) = compiled_schema else {
        return raw_result.to_owned();
    };

    let validation_error = match schema.validate(raw_result) {
        Ok(()) => return raw_result.to_owned(),
        Err(e) => e,
    };

    tracing::warn!(
        evaluator = %evaluator.name,
        error = %validation_error,
        "LLM result failed schema validation, retrying with correction"
    );

    let raw_schema = evaluator.llm.result_schema.as_ref();
    let retry_result = if let Some(raw_schema) = raw_schema {
        crate::llm_op::retry_with_schema_correction(
            &evaluator.llm,
            method,
            tool_name,
            arguments,
            tools,
            history,
            raw_result,
            raw_schema,
            &validation_error,
        )
        .await
    } else {
        None
    };

    match retry_result {
        Some(ref retried) if schema.validate(retried).is_ok() => {
            tracing::info!(evaluator = %evaluator.name, "retry produced valid result");
            retried.clone()
        }
        _ => {
            tracing::warn!(
                evaluator = %evaluator.name,
                "retry also failed schema validation, passing raw result to guest"
            );
            raw_result.to_owned()
        }
    }
}

fn parse_arguments(body: &Option<Bytes>) -> HashMap<String, String> {
    let Some(body_bytes) = body else {
        return HashMap::new();
    };
    let Ok(parsed) = serde_json::from_slice::<serde_json::Value>(body_bytes) else {
        return HashMap::new();
    };
    parsed
        .get("params")
        .and_then(|p| p.get("arguments"))
        .and_then(|a| a.as_object())
        .map(|args| {
            args.iter()
                .map(|(k, v)| {
                    let value_str = match v {
                        serde_json::Value::String(s) => s.clone(),
                        other => other.to_string(),
                    };
                    (k.clone(), value_str)
                })
                .collect()
        })
        .unwrap_or_default()
}
