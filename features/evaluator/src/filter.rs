use std::collections::HashMap;

use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use wanaku_apis::interactions::{InMemoryInteractionStore, InteractionStore};
use wanaku_apis::mcp::McpContext;
use wanaku_apis::metrics::{MetricsStore, SkipReason};
use wanaku_apis::registry::{InMemoryRegistry, ToolRegistry};

use crate::action::ActionResult;
use crate::config::{ErrorPolicy, EvaluatorDef, LlmConnection, LlmOperation};
use crate::state::EvaluatorState;

wanaku_filters::body_filter_boilerplate!(EvaluatorFilter, "wanaku_evaluator");

impl EvaluatorFilter {
    #[expect(clippy::too_many_lines, clippy::cognitive_complexity, clippy::large_stack_frames, reason = "evaluator pipeline with multiple validation steps")]
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let pipeline_start = std::time::Instant::now();
        let metrics = ctx.extensions.get::<MetricsStore>().cloned();

        let method = match ctx.get_metadata(wanaku_filters::MCP_METHOD_KEY) {
            Some(m) => m.to_owned(),
            None => {
                record_skip(&metrics, &SkipReason::MissingMethod);
                return Ok(FilterAction::Continue);
            }
        };

        let state = match ctx.extensions.get::<EvaluatorState>() {
            Some(s) => s.clone(),
            None => {
                record_skip(&metrics, &SkipReason::MissingState);
                return Ok(FilterAction::Continue);
            }
        };

        let namespace = ctx
            .get_metadata(wanaku_apis::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_apis::registry::DEFAULT_NAMESPACE)
            .to_owned();

        let Some(evaluator) = state.find_matching(&method, &namespace) else {
            record_skip(&metrics, &SkipReason::Unmatched);
            record_trigger(&metrics, false);
            return Ok(FilterAction::Continue);
        };
        record_trigger(&metrics, true);

        tracing::info!(
            evaluator = %evaluator.name,
            method = %method,
            namespace = %namespace,
            "evaluator triggered"
        );

        let registry = match ctx.extensions.get::<InMemoryRegistry>() {
            Some(r) => r.clone(),
            None => {
                record_skip(&metrics, &SkipReason::MissingRegistry);
                return Ok(FilterAction::Continue);
            }
        };

        let interactions = match ctx.extensions.get::<InMemoryInteractionStore>() {
            Some(s) => s.clone(),
            None => {
                record_skip(&metrics, &SkipReason::MissingInteractions);
                return Ok(FilterAction::Continue);
            }
        };

        let tool_name = ctx
            .get_metadata(wanaku_filters::MCP_NAME_KEY)
            .map(std::borrow::ToOwned::to_owned);

        let arguments = parse_arguments(body);

        let conversation_id = arguments
            .get(wanaku_apis::correlation::REQUEST_ID_ARG)
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

        let mcp_ctx = McpContext::new(
            &method,
            tool_name.as_deref(),
            &arguments,
            &tools,
            &history,
        );

        let Some(llm_connection) = state.get_llm_connection(&evaluator.llm.connection) else {
            tracing::error!(
                evaluator = %evaluator.name,
                connection = %evaluator.llm.connection,
                "llm connection not found at request time (should be unreachable: validated at activation)"
            );
            if let Some(ref store) = metrics {
                store.record_llm_call(&evaluator.name, false, std::time::Duration::ZERO);
            }
            return match evaluator.on_error {
                ErrorPolicy::Continue => Ok(FilterAction::Continue),
                ErrorPolicy::Block => Ok(wanaku_filters::response::json_rpc_error(
                    &wanaku_filters::response::extract_json_rpc_id(body),
                    -32603,
                    "evaluator llm connection not available",
                )),
            };
        };

        tracing::info!(
            "Invoking evaluator {} using llm {} on behalf of tracking ID {}",
            evaluator.name,
            evaluator.llm.connection,
            conversation_id.as_deref().unwrap_or("-")
        );

        let raw_llm_result = crate::llm_op::run_llm_operation(
            &evaluator.name,
            crate::llm_op::ResolvedLlm { def: &evaluator.llm, connection: &llm_connection },
            &mcp_ctx,
            metrics.as_ref(),
        )
        .await
        .unwrap_or_default();

        let resolved = ResolvedRuntime {
            connection: llm_connection,
            compiled_schema: state.get_compiled_schema(&evaluator.name),
        };

        let llm_result = validate_and_retry_if_needed(
            &raw_llm_result,
            &evaluator,
            &resolved,
            &mcp_ctx,
            metrics.as_ref(),
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
            if let Some(ref store) = metrics {
                store.record_wasm_not_found(&evaluator.name);
            }
            return match evaluator.on_error {
                ErrorPolicy::Continue => Ok(FilterAction::Continue),
                ErrorPolicy::Block => {
                    let json_rpc_id = wanaku_filters::response::json_rpc_id_from_metadata(
                        ctx.get_metadata(wanaku_filters::MCP_ID_KEY),
                    );
                    Ok(wanaku_filters::response::json_rpc_error(
                        &json_rpc_id,
                        -32603,
                        "evaluator processor module not available",
                    ))
                }
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

        let wasm_start = std::time::Instant::now();
        let result = compiled.evaluate(
            registry,
            interactions,
            eval_ctx,
            resolved.compiled_schema,
        );
        if let Some(ref store) = metrics {
            store.record_wasm_execution(&evaluator.name, wasm_start.elapsed());
        }

        tracing::info!(
            evaluator = %evaluator.name,
            action = ?result,
            "processor result"
        );

        if let Some(ref store) = metrics {
            store.record_evaluator_decision(&evaluator.name, action_label(&result));
            store.record_pipeline_duration(&evaluator.name, pipeline_start.elapsed());
        }

        dispatch_action(ctx, body, result, &method, &evaluator.name)
    }
}

const fn action_label(result: &ActionResult) -> &'static str {
    match result {
        ActionResult::Pass => "pass",
        ActionResult::Block(_) => "block",
        ActionResult::RejectMalformed(_) => "reject_malformed",
        ActionResult::Warn(_) => "warn",
        ActionResult::FilterTools(_) => "filter_tools",
        ActionResult::SetMetadata(_, _) => "set_metadata",
    }
}

fn record_skip(metrics: &Option<MetricsStore>, reason: &SkipReason) {
    if let Some(store) = metrics {
        store.record_skip(reason);
    }
}

fn record_trigger(metrics: &Option<MetricsStore>, matched: bool) {
    if let Some(store) = metrics {
        store.record_trigger_match(matched);
    }
}

#[expect(clippy::too_many_lines, clippy::cognitive_complexity, reason = "action dispatch with multiple variants")]
fn dispatch_action(
    ctx: &mut HttpFilterContext<'_>,
    _body: &mut Option<Bytes>,
    result: ActionResult,
    method: &str,
    evaluator_name: &str,
) -> Result<FilterAction, FilterError> {
    let json_rpc_id = wanaku_filters::response::json_rpc_id_from_metadata(
        ctx.get_metadata(wanaku_filters::MCP_ID_KEY),
    );

    match result {
        ActionResult::Pass => Ok(FilterAction::Continue),
        ActionResult::Block(reason) => {
            tracing::warn!(evaluator = %evaluator_name, reason = %reason, "request blocked");
            Ok(wanaku_filters::response::json_rpc_error(
                &json_rpc_id,
                -32001,
                &format!("blocked by evaluator {evaluator_name}: {reason}"),
            ))
        }
        ActionResult::RejectMalformed(reason) => {
            tracing::warn!(evaluator = %evaluator_name, reason = %reason, "rejected: malformed input");
            Ok(wanaku_filters::response::json_rpc_error(
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
                wanaku_filters::response::json_response(Bytes::from(response.to_string())),
            ))
        }
        ActionResult::SetMetadata(key, value) => {
            ctx.set_metadata(&key, &value);
            Ok(FilterAction::Continue)
        }
    }
}

/// Per-request state resolved from [`EvaluatorState`]: the evaluator's LLM
/// connection and its compiled result schema (if any). Grouped together to
/// keep `validate_and_retry_if_needed` under the workspace's argument-count
/// lint. By the time this is constructed, the connection has already been
/// resolved successfully — see the early return in `handle_body`.
struct ResolvedRuntime {
    connection: LlmConnection,
    compiled_schema: Option<std::sync::Arc<crate::schema::CompiledSchema>>,
}

#[expect(clippy::too_many_lines, clippy::cognitive_complexity, reason = "schema validation with retry logic")]
async fn validate_and_retry_if_needed(
    raw_result: &str,
    evaluator: &EvaluatorDef,
    resolved: &ResolvedRuntime,
    mcp: &McpContext<'_>,
    metrics: Option<&MetricsStore>,
) -> String {
    let Some(schema) = resolved.compiled_schema.as_ref() else {
        return raw_result.to_owned();
    };

    let validation_error = match schema.validate(raw_result) {
        Ok(()) => {
            if let Some(store) = metrics {
                store.record_schema_validation(&evaluator.name, true);
            }
            return raw_result.to_owned();
        }
        Err(e) => {
            if let Some(store) = metrics {
                store.record_schema_validation(&evaluator.name, false);
            }
            e
        }
    };

    tracing::warn!(
        evaluator = %evaluator.name,
        error = %validation_error,
        "LLM result failed schema validation, retrying with correction"
    );

    let raw_schema = evaluator.llm.result_schema.as_ref();
    let retry_result = match raw_schema {
        Some(raw_schema) => {
            crate::llm_op::retry_with_schema_correction(
                crate::llm_op::ResolvedLlm { def: &evaluator.llm, connection: &resolved.connection },
                mcp,
                raw_result,
                raw_schema,
                &validation_error,
            )
            .await
        }
        None => None,
    };

    match retry_result {
        Some(ref retried) if schema.validate(retried).is_ok() => {
            tracing::info!(evaluator = %evaluator.name, "retry produced valid result");
            if let Some(store) = metrics {
                store.record_schema_retry(&evaluator.name, true);
            }
            retried.clone()
        }
        _ => {
            tracing::warn!(
                evaluator = %evaluator.name,
                "retry also failed schema validation, passing raw result to guest"
            );
            if let Some(store) = metrics {
                store.record_schema_retry(&evaluator.name, false);
            }
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn action_label_all_variants() {
        assert_eq!(action_label(&ActionResult::Pass), "pass");
        assert_eq!(action_label(&ActionResult::Block("r".into())), "block");
        assert_eq!(action_label(&ActionResult::RejectMalformed("r".into())), "reject_malformed");
        assert_eq!(action_label(&ActionResult::Warn("m".into())), "warn");
        assert_eq!(action_label(&ActionResult::FilterTools(vec![])), "filter_tools");
        assert_eq!(action_label(&ActionResult::SetMetadata("k".into(), "v".into())), "set_metadata");
    }

    #[test]
    fn record_skip_without_store() {
        record_skip(&None, &SkipReason::MissingMethod);
    }

    #[test]
    fn record_skip_with_store() {
        let store = MetricsStore::new();
        record_skip(&Some(store.clone()), &SkipReason::MissingMethod);
        record_skip(&Some(store.clone()), &SkipReason::Unmatched);
        let snap = store.snapshot();
        assert_eq!(snap.pipeline.skipped_no_method, 1);
        assert_eq!(snap.pipeline.skipped_no_match, 1);
    }

    #[test]
    fn record_trigger_with_store() {
        let store = MetricsStore::new();
        record_trigger(&Some(store.clone()), true);
        record_trigger(&Some(store.clone()), false);
        let snap = store.snapshot();
        assert_eq!(snap.pipeline.trigger_matches, 1);
        assert_eq!(snap.pipeline.trigger_misses, 1);
    }
}
