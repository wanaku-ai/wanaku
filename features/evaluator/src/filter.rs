use crate::action::ActionResult;
use crate::config::EvaluationEngine;
use crate::state::EvaluatorState;
use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use std::collections::HashMap;
use wanaku_infra::metrics::{GovernanceOutcome, MetricsStore, SkipReason};
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::audit::{
    AuditCategory, AuditDecision, AuditEvent, AuditStore, InMemoryAuditStore,
};
use wanaku_types::governance::{
    AuditLevel, EnforcementMode, FailureBehavior, GovernanceConfig, GovernancePosture,
    NoMatchBehavior,
};
use wanaku_types::interactions::{InMemoryInteractionStore, InteractionStore};
use wanaku_types::mcp::McpContext;
use wanaku_types::registry::ToolRegistry;

wanaku_filters::body_filter_boilerplate!(EvaluatorFilter, "wanaku_evaluator");

impl EvaluatorFilter {
    #[expect(
        clippy::too_many_lines,
        reason = "request evaluation pipeline resolves one immutable configuration snapshot"
    )]
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let Some(method) = ctx
            .get_metadata(wanaku_filters::MCP_METHOD_KEY)
            .map(str::to_owned)
        else {
            if let Some(metrics) = ctx.extensions.get::<MetricsStore>() {
                metrics.record_skip(&SkipReason::MissingMethod);
            }
            return Ok(FilterAction::Continue);
        };
        let namespace = ctx
            .get_metadata(wanaku_types::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_types::registry::DEFAULT_NAMESPACE)
            .to_owned();
        let posture = ctx
            .extensions
            .get::<GovernanceConfig>()
            .cloned()
            .unwrap_or_default()
            .resolve(&namespace);
        let mut audit = RequestAudit {
            method: &method,
            namespace: &namespace,
            posture: &posture,
            evaluator: None,
            revision: None,
            start: std::time::Instant::now(),
        };
        if posture.mode == EnforcementMode::Disabled {
            tracing::warn!(namespace = %namespace, reason = ?posture.disabled_reason, "evaluator governance disabled");
            return finish(ctx, body, &audit, Outcome::Skipped("disabled"));
        }
        let state = ctx.extensions.get::<EvaluatorState>().cloned();
        let config = state
            .as_ref()
            .and_then(|state| state.try_active_config().ok());
        let evaluator = config
            .as_ref()
            .and_then(|config| config.find_matching(&method, &namespace));
        // Discovery and protocol traffic require an explicit evaluator. Only
        // action methods inherit the fail-closed governance baseline.
        if !is_governed_method(&method) && evaluator.is_none() {
            return Ok(FilterAction::Continue);
        }
        let (Some(state), Some(config)) = (state, config) else {
            return finish(ctx, body, &audit, Outcome::Failure("state_unavailable"));
        };
        audit.revision = config.revision_id;
        if let Some(reason) = config.invalid_reason {
            return finish(ctx, body, &audit, Outcome::Unavailable(reason));
        }
        let Some(evaluator) = evaluator else {
            if let Some(metrics) = ctx.extensions.get::<MetricsStore>() {
                metrics.record_trigger_match(false);
                metrics.record_skip(&SkipReason::Unmatched);
            }
            return finish(ctx, body, &audit, Outcome::NoMatch);
        };
        audit.evaluator = Some(&evaluator.name);
        if let Some(metrics) = ctx.extensions.get::<MetricsStore>() {
            metrics.record_trigger_match(true);
        }
        if skip_external(&posture, &evaluator.engine) {
            return finish(
                ctx,
                body,
                &audit,
                Outcome::Skipped("external_evaluation_disabled"),
            );
        }
        let Some(registry) = ctx.extensions.get::<InMemoryRegistry>().cloned() else {
            config.record_failure(&evaluator.name);
            return finish(ctx, body, &audit, Outcome::Failure("registry_unavailable"));
        };
        let Some(interactions) = ctx.extensions.get::<InMemoryInteractionStore>().cloned() else {
            config.record_failure(&evaluator.name);
            return finish(
                ctx,
                body,
                &audit,
                Outcome::Failure("interactions_unavailable"),
            );
        };
        let Some(compiled) = config.get_compiled(&evaluator.processor.path) else {
            config.record_failure(&evaluator.name);
            return finish(ctx, body, &audit, Outcome::Failure("processor_unavailable"));
        };
        let tool_name = ctx
            .get_metadata(wanaku_filters::MCP_NAME_KEY)
            .map(str::to_owned);
        let arguments = parse_arguments(body);
        let conversation_id = arguments
            .get(wanaku_types::correlation::REQUEST_ID_ARG)
            .cloned()
            .or_else(|| state.get_binding(&namespace));
        let history = conversation_id
            .as_ref()
            .map(|id| interactions.get_by_conversation_id(id))
            .unwrap_or_default();
        let tools = if crate::evaluation::requires_tools(&evaluator.engine) {
            registry
                .list_tools()
                .into_iter()
                .filter(|t| t.enabled)
                .collect()
        } else {
            Vec::new()
        };
        let mcp = McpContext::new(&method, tool_name.as_deref(), &arguments, &tools, &history);
        let schema = config.get_compiled_schema(&evaluator.name);
        let metrics = ctx.extensions.get::<MetricsStore>().cloned();
        let result = crate::evaluation::execute(
            &evaluator,
            &evaluator.engine,
            &crate::evaluation::EvaluationContext {
                state: &state,
                mcp: &mcp,
                compiled_schema: schema.as_deref(),
                metrics: metrics.as_ref(),
            },
        )
        .await;
        let llm_result = match result {
            Ok(result) => result,
            Err(error) => {
                config.record_failure(&evaluator.name);
                return finish(
                    ctx,
                    body,
                    &audit,
                    Outcome::Failure(
                        error
                            .reason_code()
                            .strip_prefix("evaluator_")
                            .unwrap_or(error.reason_code()),
                    ),
                );
            }
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
            crate::engine::ExecutionContext {
                registry,
                interactions,
                compiled_schema: schema,
                allow_side_effects: posture.mode == EnforcementMode::Enforce,
            },
            eval_ctx,
        );
        if let Some(metrics) = &metrics {
            metrics.record_wasm_execution(&evaluator.name, wasm_start.elapsed());
        }
        match result {
            Ok(result) => {
                config.record_success(&evaluator.name);
                finish(
                    ctx,
                    body,
                    &audit,
                    Outcome::Evaluated(result.action, result.side_effects_suppressed),
                )
            }
            Err(error) => {
                config.record_failure(&evaluator.name);
                let reason = match error {
                    crate::engine::EvaluatorExecutionError::Instantiation => {
                        "processor_instantiation_failed"
                    }
                    crate::engine::EvaluatorExecutionError::Execution => {
                        "processor_execution_failed"
                    }
                };
                finish(ctx, body, &audit, Outcome::Failure(reason))
            }
        }
    }
}

fn is_governed_method(method: &str) -> bool {
    matches!(
        method,
        wanaku_types::TOOLS_CALL | wanaku_types::RESOURCES_READ | wanaku_types::PROMPTS_GET
    )
}

const fn skip_external(posture: &GovernancePosture, engine: &EvaluationEngine) -> bool {
    matches!(posture.mode, EnforcementMode::Audit)
        && matches!(posture.audit_level, AuditLevel::Basic)
        && !matches!(engine, EvaluationEngine::Passthrough)
}

struct RequestAudit<'a> {
    method: &'a str,
    namespace: &'a str,
    posture: &'a GovernancePosture,
    evaluator: Option<&'a str>,
    revision: Option<u64>,
    start: std::time::Instant,
}

enum Outcome {
    NoMatch,
    Failure(&'static str),
    Unavailable(&'static str),
    Skipped(&'static str),
    Evaluated(ActionResult, bool),
}

impl Outcome {
    fn proposed_action(&self, posture: &GovernancePosture) -> Option<&'static str> {
        match self {
            Self::Unavailable(_) => Some("block"),
            Self::NoMatch => Some(if posture.no_match == NoMatchBehavior::Deny {
                "block"
            } else {
                "pass"
            }),
            Self::Failure(_) => Some(if posture.on_failure == FailureBehavior::Deny {
                "block"
            } else {
                "pass"
            }),
            Self::Skipped(_) => None,
            Self::Evaluated(action, _) => Some(action_label(action)),
        }
    }
    fn effective_action(&self, posture: &GovernancePosture) -> &'static str {
        if posture.mode == EnforcementMode::Enforce {
            self.proposed_action(posture).unwrap_or("pass")
        } else {
            "pass"
        }
    }
    const fn reason(&self) -> &'static str {
        match self {
            Self::NoMatch => "no_match",
            Self::Failure(reason) | Self::Unavailable(reason) | Self::Skipped(reason) => reason,
            Self::Evaluated(_, _) => "evaluated",
        }
    }
}

fn finish(
    ctx: &mut HttpFilterContext<'_>,
    body: &mut Option<Bytes>,
    audit: &RequestAudit<'_>,
    outcome: Outcome,
) -> Result<FilterAction, FilterError> {
    record_outcome(ctx, body.as_ref(), audit, &outcome);
    if audit.posture.mode != EnforcementMode::Enforce {
        return Ok(FilterAction::Continue);
    }
    match outcome {
        Outcome::Evaluated(action, _) => dispatch_action(
            ctx,
            body,
            action,
            audit.method,
            audit.evaluator.unwrap_or("evaluator"),
        ),
        Outcome::NoMatch | Outcome::Failure(_) | Outcome::Unavailable(_)
            if outcome.effective_action(audit.posture) == "block" =>
        {
            let mut id = wanaku_filters::response::json_rpc_id_from_metadata(
                ctx.get_metadata(wanaku_filters::MCP_ID_KEY),
            );
            if id.is_null() {
                id = wanaku_filters::response::extract_json_rpc_id(body);
            }
            Ok(wanaku_filters::response::json_rpc_error(
                &id,
                -32001,
                &format!("evaluator: {}", outcome.reason()),
            ))
        }
        _ => Ok(FilterAction::Continue),
    }
}

fn record_outcome(
    ctx: &HttpFilterContext<'_>,
    body: Option<&Bytes>,
    audit: &RequestAudit<'_>,
    outcome: &Outcome,
) {
    let effective = outcome.effective_action(audit.posture);
    if let Some(metrics) = ctx.extensions.get::<MetricsStore>() {
        metrics.record_governance(
            audit.posture.mode,
            governance_outcome(audit.posture, outcome),
        );
        if let Some(name) = audit.evaluator {
            metrics.record_evaluator_decision(name, effective);
            metrics.record_pipeline_duration(name, audit.start.elapsed());
        }
    }
    if let Some(store) = ctx.extensions.get::<InMemoryAuditStore>() {
        store.record(outcome_event(ctx, body, audit, outcome));
    }
}

fn governance_outcome(posture: &GovernancePosture, outcome: &Outcome) -> GovernanceOutcome {
    if posture.mode == EnforcementMode::Disabled {
        return GovernanceOutcome::Disabled;
    }
    let deny = matches!(
        outcome.proposed_action(posture),
        Some("block" | "reject_malformed")
    );
    match outcome {
        Outcome::Skipped(_) => GovernanceOutcome::AuditSkipped,
        Outcome::NoMatch => {
            if deny {
                GovernanceOutcome::NoMatchDeny
            } else {
                GovernanceOutcome::NoMatchAllow
            }
        }
        Outcome::Failure(_) | Outcome::Unavailable(_) => {
            if deny {
                GovernanceOutcome::FailClosed
            } else {
                GovernanceOutcome::FailOpen
            }
        }
        Outcome::Evaluated(_, _) => match (posture.mode, deny) {
            (EnforcementMode::Audit, true) => GovernanceOutcome::AuditDeny,
            (EnforcementMode::Audit, false) => GovernanceOutcome::AuditAllow,
            (_, true) => GovernanceOutcome::EnforcedDeny,
            (_, false) => GovernanceOutcome::EnforcedAllow,
        },
    }
}

fn outcome_event(
    ctx: &HttpFilterContext<'_>,
    body: Option<&Bytes>,
    audit: &RequestAudit<'_>,
    outcome: &Outcome,
) -> AuditEvent {
    let effective = outcome.effective_action(audit.posture);
    let decision = match effective {
        "block" => AuditDecision::Block,
        "reject_malformed" => AuditDecision::RejectMalformed,
        "warn" => AuditDecision::Warn,
        _ => AuditDecision::Allow,
    };
    let mut event = AuditEvent::new(
        AuditCategory::Decision,
        decision,
        audit.method,
        format!("evaluator_{}", outcome.reason()),
        "Evaluator governance decision.",
    );
    event.namespace = Some(audit.namespace.to_owned());
    event.evaluator = audit.evaluator.map(str::to_owned);
    event.filter = Some("wanaku_evaluator".to_owned());
    event.policy_revision = audit.revision.map(|revision| revision.to_string());
    event.target = ctx
        .get_metadata(wanaku_filters::MCP_NAME_KEY)
        .map(str::to_owned);
    event.target_type = target_type(audit.method).map(str::to_owned);
    event.duration_ms = u64::try_from(audit.start.elapsed().as_millis()).ok();
    add_outcome_attributes(&mut event, audit, outcome);
    wanaku_types::audit::add_mcp_request_context_with_id(
        &mut event,
        body.map(bytes::Bytes::as_ref),
        ctx.request_id(),
    );
    event
}

fn target_type(method: &str) -> Option<&'static str> {
    match method {
        wanaku_types::TOOLS_CALL => Some("tool"),
        wanaku_types::RESOURCES_READ => Some("resource"),
        wanaku_types::PROMPTS_GET => Some("prompt"),
        _ => None,
    }
}

fn suppressed_effects(outcome: &Outcome, posture: &GovernancePosture) -> Option<bool> {
    match outcome {
        Outcome::Failure("processor_execution_failed" | "processor_instantiation_failed")
            if posture.mode == EnforcementMode::Audit =>
        {
            None
        }
        Outcome::Evaluated(action, writes_suppressed) => Some(
            *writes_suppressed
                || (posture.mode == EnforcementMode::Audit
                    && !matches!(action, ActionResult::Pass)),
        ),
        _ => Some(false),
    }
}

fn add_outcome_attributes(event: &mut AuditEvent, audit: &RequestAudit<'_>, outcome: &Outcome) {
    let evaluation_status = match outcome {
        Outcome::Skipped(_) => "not_evaluated",
        Outcome::Failure(_) | Outcome::Unavailable(_) => "error",
        Outcome::NoMatch => "no_match",
        Outcome::Evaluated(_, _) => "evaluated",
    };
    if let serde_json::Value::Object(attributes) = serde_json::json!({
        "effective_action": outcome.effective_action(audit.posture),
        "would_be_action": outcome.proposed_action(audit.posture),
        "enforcement_mode": audit.posture.mode,
        "audit_level": audit.posture.audit_level,
        "skipped": matches!(outcome, Outcome::Skipped(_)),
        "failure": matches!(outcome, Outcome::Failure(_) | Outcome::Unavailable(_)),
        "side_effects_suppressed": suppressed_effects(outcome, audit.posture),
        "no_match": matches!(outcome, Outcome::NoMatch),
        "evaluation_status": evaluation_status,
    }) {
        event.attributes.extend(attributes);
    }
    if audit.posture.mode == EnforcementMode::Disabled {
        event.attributes.insert(
            "disabled_reason".to_owned(),
            serde_json::json!(audit.posture.disabled_reason),
        );
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
#[expect(
    clippy::too_many_lines,
    clippy::cognitive_complexity,
    reason = "action dispatch with multiple variants"
)]
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
            if method != wanaku_types::TOOLS_LIST {
                tracing::warn!(evaluator = %evaluator_name, "filter_tools called on non-tools/list, ignoring");
                return Ok(FilterAction::Continue);
            }

            let registry = ctx.extensions.get::<InMemoryRegistry>();
            let mcp_tools: Vec<serde_json::Value> = tool_names
                .iter()
                .filter_map(|name| {
                    let tool = registry
                        .and_then(|r| r.get_tool(name))
                        .filter(|t| t.enabled);
                    tool.map(|t| {
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
    use praxis_filter::Request;
    static TEST_ID_GENERATOR: std::sync::LazyLock<praxis_core::id::IdGenerator> =
        std::sync::LazyLock::new(|| praxis_core::id::IdGenerator::with_seed(0));
    #[expect(
        clippy::too_many_lines,
        reason = "Praxis test context requires every field explicitly"
    )]
    fn make_filter_context(req: &Request) -> HttpFilterContext<'_> {
        HttpFilterContext {
            buffered_request_body: None,
            body_done_indices: Vec::new(),
            branch_iterations: std::collections::HashMap::new(),
            client_addr: None,
            cluster: None,
            current_filter_id: None,
            downstream_tls: false,
            extensions: praxis_filter::RequestExtensions::default(),
            executed_filter_indices: Vec::new(),
            extra_request_headers: Vec::new(),
            request_headers_to_remove: Vec::new(),
            request_headers_to_set: Vec::new(),
            filter_metadata: std::collections::HashMap::new(),
            prior_pre_read_mutations: Vec::new(),
            pre_read_mutations: Vec::new(),
            structured_metadata: std::collections::HashMap::new(),
            filter_results: std::collections::HashMap::new(),
            filter_state: std::collections::HashMap::new(),
            health_registry: None,
            id_generator: &TEST_ID_GENERATOR,
            kv_stores: None,
            session_stores: None,
            metrics_route: None,
            peer_identity: None,
            subrequest_client: None,
            subrequest_response_mode: praxis_filter::SubRequestResponseMode::Buffered,
            request: req,
            request_body_bytes: 0,
            request_body_mode: praxis_filter::BodyMode::Stream,
            request_start: std::time::Instant::now(),
            response_body_bytes: 0,
            response_body_mode: praxis_filter::BodyMode::Stream,
            response_header: None,
            response_headers_modified: false,
            upstream_reached: false,
            rewritten_path: None,
            selected_endpoint_index: None,
            attempted_endpoints: Vec::new(),
            retry_policy: None,
            route_retry_policy: None,
            cluster_retry_state: None,
            cluster_retry_state_released: false,
            endpoint_reselector: None,
            pinned_endpoint_address: None,
            time_source: &praxis_core::time::SystemTimeSource,
            upstream: None,
        }
    }

    fn request() -> Request {
        Request {
            method: http::Method::POST,
            uri: "/mcp".parse().unwrap(),
            headers: http::HeaderMap::new(),
        }
    }
    fn audit(posture: &GovernancePosture) -> RequestAudit<'_> {
        RequestAudit {
            method: "tools/call",
            namespace: "default",
            posture,
            evaluator: Some("test"),
            revision: Some(42),
            start: std::time::Instant::now(),
        }
    }

    async fn discovery_result(
        state: Option<&EvaluatorState>,
        method: &str,
        namespace: &str,
    ) -> FilterAction {
        let request = request();
        let mut ctx = make_filter_context(&request);
        ctx.set_metadata(wanaku_filters::MCP_METHOD_KEY, method);
        ctx.set_metadata(wanaku_types::NAMESPACE_METADATA_KEY, namespace);
        if let Some(state) = state {
            ctx.extensions.insert(state.clone());
        }
        EvaluatorFilter {
            max_body_bytes: 1024,
        }
        .handle_body(&mut ctx, &mut None)
        .await
        .unwrap()
    }

    #[tokio::test]
    async fn implicit_baseline_governs_actions_but_preserves_protocol_and_discovery() {
        let empty = EvaluatorState::new();
        let invalid = EvaluatorState::new();
        invalid.mark_invalid("configuration_invalid");
        for state in [None, Some(empty), Some(invalid)] {
            for (method, allowed) in [
                (wanaku_types::INITIALIZE, true),
                (wanaku_types::NOTIFICATIONS_INITIALIZED, true),
                (wanaku_types::PING, true),
                (wanaku_types::TOOLS_LIST, true),
                (wanaku_types::RESOURCES_LIST, true),
                (wanaku_types::RESOURCES_TEMPLATES_LIST, true),
                (wanaku_types::PROMPTS_LIST, true),
                (wanaku_types::TOOLS_CALL, false),
                (wanaku_types::RESOURCES_READ, false),
                (wanaku_types::PROMPTS_GET, false),
            ] {
                let result = Box::pin(discovery_result(state.as_ref(), method, "default")).await;
                assert_eq!(
                    matches!(result, FilterAction::Continue),
                    allowed,
                    "method: {method}"
                );
            }
        }
    }

    #[tokio::test]
    async fn explicit_discovery_evaluator_remains_governed_in_its_namespace() {
        let state = EvaluatorState::new();
        state.load_evaluators(vec![
            serde_json::from_value(serde_json::json!({
                "name":"discovery", "trigger":{"method":"tools/list", "namespace":"protected"},
                "engine":{"type":"passthrough"}, "processor":{"path":"missing.wasm"}
            }))
            .unwrap(),
        ]);
        for namespace in ["protected", "default"] {
            let result = Box::pin(discovery_result(
                Some(&state),
                wanaku_types::TOOLS_LIST,
                namespace,
            ))
            .await;
            assert_eq!(
                matches!(result, FilterAction::Continue),
                namespace == "default"
            );
        }
    }

    #[tokio::test]
    async fn missing_state_and_empty_configuration_follow_distinct_policies() {
        let request = request();
        let filter = EvaluatorFilter {
            max_body_bytes: 1024,
        };
        for has_state in [false, true] {
            for allow in [false, true] {
                let mut ctx = make_filter_context(&request);
                ctx.set_metadata(wanaku_filters::MCP_METHOD_KEY, "tools/call");
                let mut governance = GovernanceConfig::default();
                if has_state {
                    governance.default.no_match = if allow {
                        NoMatchBehavior::Allow
                    } else {
                        NoMatchBehavior::Deny
                    };
                    ctx.extensions.insert(EvaluatorState::new());
                } else {
                    governance.default.on_failure = if allow {
                        FailureBehavior::Allow
                    } else {
                        FailureBehavior::Deny
                    };
                }
                ctx.extensions.insert(governance);
                let result = filter.handle_body(&mut ctx, &mut None).await.unwrap();
                assert_eq!(matches!(result, FilterAction::Continue), allow);
            }
        }
    }

    #[tokio::test]
    #[expect(
        clippy::too_many_lines,
        reason = "disabled bypass test includes traffic and audit assertions"
    )]
    async fn disabled_bypasses_missing_state_and_records_no_evaluation() {
        let request = request();
        let mut ctx = make_filter_context(&request);
        ctx.set_metadata(wanaku_filters::MCP_METHOD_KEY, "tools/call");
        let mut governance = GovernanceConfig::default();
        governance.default.mode = EnforcementMode::Disabled;
        governance.default.disabled_reason = Some("maintenance".into());
        ctx.extensions.insert(governance);
        let store = InMemoryAuditStore::new(10);
        ctx.extensions.insert(store.clone());
        assert!(matches!(
            EvaluatorFilter {
                max_body_bytes: 1024
            }
            .handle_body(&mut ctx, &mut None)
            .await
            .unwrap(),
            FilterAction::Continue
        ));
        let events = store.query(&wanaku_types::audit::AuditQuery::default());
        assert_eq!(events.events[0].reason_code, "evaluator_disabled");
        assert_eq!(
            events.events[0].attributes["evaluation_status"],
            "not_evaluated"
        );
        assert_eq!(
            events.events[0].attributes["disabled_reason"],
            "maintenance"
        );
        assert_eq!(
            events.events[0].attributes["would_be_action"],
            serde_json::Value::Null
        );
    }

    #[tokio::test]
    async fn namespace_override_applies_before_state_access() {
        let request = request();
        let filter = EvaluatorFilter {
            max_body_bytes: 1024,
        };
        let governance: GovernanceConfig =
            serde_yaml::from_str("namespaces:\n  sandbox:\n    mode: audit\n").unwrap();
        for namespace in ["sandbox", "production"] {
            let mut ctx = make_filter_context(&request);
            ctx.set_metadata(wanaku_filters::MCP_METHOD_KEY, "tools/call");
            ctx.set_metadata(wanaku_types::NAMESPACE_METADATA_KEY, namespace);
            ctx.extensions.insert(governance.clone());
            let result = filter.handle_body(&mut ctx, &mut None).await.unwrap();
            assert_eq!(
                matches!(result, FilterAction::Continue),
                namespace == "sandbox"
            );
        }
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "complete processor action matrix verifies traffic and audit outcomes"
    )]
    fn audit_suppresses_all_processor_actions_and_retains_revision() {
        let request = request();
        let posture = GovernancePosture {
            mode: EnforcementMode::Audit,
            audit_level: AuditLevel::Full,
            ..GovernancePosture::default()
        };
        for action in [
            ActionResult::Pass,
            ActionResult::Block("secret".into()),
            ActionResult::RejectMalformed("secret".into()),
            ActionResult::Warn("secret".into()),
            ActionResult::FilterTools(vec![]),
            ActionResult::SetMetadata("injected".into(), "secret".into()),
        ] {
            let mut ctx = make_filter_context(&request);
            let mut body = Some(Bytes::from_static(b"original"));
            let original_metadata = ctx.filter_metadata.clone();
            let suppressed = !matches!(action, ActionResult::Pass);
            let outcome = Outcome::Evaluated(action, false);
            let event = outcome_event(&ctx, body.as_ref(), &audit(&posture), &outcome);
            assert_eq!(event.decision, AuditDecision::Allow);
            assert_eq!(
                event.attributes["side_effects_suppressed"],
                serde_json::json!(suppressed)
            );
            assert_eq!(event.attributes["evaluation_status"], "evaluated");
            assert_eq!(event.policy_revision.as_deref(), Some("42"));
            assert!(!serde_json::to_string(&event).unwrap().contains("secret"));
            assert!(matches!(
                finish(&mut ctx, &mut body, &audit(&posture), outcome).unwrap(),
                FilterAction::Continue
            ));
            assert_eq!(body, Some(Bytes::from_static(b"original")));
            assert_eq!(ctx.filter_metadata, original_metadata);
        }
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "complete failure and enforcement mode matrix stays together"
    )]
    fn operational_failures_and_invalid_configuration_obey_mode_matrix() {
        let request = request();
        for mode in [EnforcementMode::Enforce, EnforcementMode::Audit] {
            for failure in [FailureBehavior::Allow, FailureBehavior::Deny] {
                let posture = GovernancePosture {
                    mode,
                    on_failure: failure,
                    ..GovernancePosture::default()
                };
                for reason in [
                    "state_unavailable",
                    "registry_unavailable",
                    "interactions_unavailable",
                    "engine_failed",
                    "schema_invalid",
                    "processor_unavailable",
                    "processor_execution_failed",
                ] {
                    let mut ctx = make_filter_context(&request);
                    let result = finish(
                        &mut ctx,
                        &mut None,
                        &audit(&posture),
                        Outcome::Failure(reason),
                    )
                    .unwrap();
                    assert_eq!(
                        matches!(result, FilterAction::Continue),
                        mode == EnforcementMode::Audit || failure == FailureBehavior::Allow
                    );
                }
                let mut ctx = make_filter_context(&request);
                let result = finish(
                    &mut ctx,
                    &mut None,
                    &audit(&posture),
                    Outcome::Unavailable("configuration_invalid"),
                )
                .unwrap();
                assert_eq!(
                    matches!(result, FilterAction::Continue),
                    mode == EnforcementMode::Audit
                );
            }
        }
    }

    #[tokio::test]
    #[expect(
        clippy::too_many_lines,
        reason = "end-to-end mode matrix includes remote server, WASM, and audit assertions"
    )]
    async fn full_audit_calls_remote_and_processor_without_enforcing_block() {
        let wasm = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../actions/dist/safety_review_action.wasm");
        assert!(wasm.exists(), "build WASM actions before evaluator tests");
        let request = request();
        for mode in [EnforcementMode::Enforce, EnforcementMode::Audit] {
            let server = wiremock::MockServer::start().await;
            wiremock::Mock::given(wiremock::matchers::method("POST"))
                .respond_with(wiremock::ResponseTemplate::new(200).set_body_json(serde_json::json!({"choices":[{"message":{"content":"{\"level\":\"red\",\"reason\":\"dangerous\"}"}}]})))
                .expect(1).mount(&server).await;
            let state = EvaluatorState::new();
            state
                .load_llm_connections(vec![crate::config::LlmConnection {
                    name: "remote".into(),
                    url: server.uri(),
                    model: "test".into(),
                    api_key: "secret".into(),
                }])
                .unwrap();
            state.load_evaluators(vec![serde_json::from_value(serde_json::json!({"name":"remote","trigger":{"method":"tools/call"},"engine":{"type":"llm","connection":"remote","operation":"classify","prompt":"test"},"processor":{"path":wasm}})).unwrap()]);
            let mut ctx = make_filter_context(&request);
            ctx.set_metadata(wanaku_filters::MCP_METHOD_KEY, "tools/call");
            let mut governance = GovernanceConfig::default();
            governance.default.mode = mode;
            governance.default.audit_level = AuditLevel::Full;
            ctx.extensions.insert(governance);
            ctx.extensions.insert(state);
            ctx.extensions.insert(InMemoryRegistry::new());
            ctx.extensions.insert(InMemoryInteractionStore::new(10));
            let store = InMemoryAuditStore::new(10);
            ctx.extensions.insert(store.clone());
            let metadata = ctx.filter_metadata.clone();
            let original = Bytes::from_static(
                br#"{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"arguments":{}}}"#,
            );
            let mut body = Some(original.clone());
            let result = EvaluatorFilter {
                max_body_bytes: 1024,
            }
            .handle_body(&mut ctx, &mut body)
            .await
            .unwrap();
            assert_eq!(
                matches!(result, FilterAction::Continue),
                mode == EnforcementMode::Audit
            );
            assert_eq!(body, Some(original));
            assert_eq!(ctx.filter_metadata, metadata);
            let events = store.query(&wanaku_types::audit::AuditQuery::default());
            assert_eq!(events.events[0].reason_code, "evaluator_evaluated");
            assert_eq!(events.events[0].attributes["would_be_action"], "block");
            assert_eq!(
                events.events[0].attributes["effective_action"],
                if mode == EnforcementMode::Audit {
                    "pass"
                } else {
                    "block"
                }
            );
            server.verify().await;
        }
    }

    fn looping_evaluator() -> EvaluatorState {
        let state = EvaluatorState::new();
        let processor = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("tests/fixtures/fuel-exhaustion.wat");
        state.load_evaluators(vec![
            serde_json::from_value(serde_json::json!({
                "name":"loop", "trigger":{"method":"tools/call"},
                "engine":{"type":"passthrough"}, "processor":{"path":processor}
            }))
            .unwrap(),
        ]);
        state
    }

    async fn fuel_failure_result(
        state: &EvaluatorState,
        posture: GovernancePosture,
    ) -> (FilterAction, AuditEvent) {
        let request = request();
        let mut ctx = make_filter_context(&request);
        ctx.set_metadata(wanaku_filters::MCP_METHOD_KEY, wanaku_types::TOOLS_CALL);
        ctx.extensions.insert(state.clone());
        ctx.extensions.insert(InMemoryRegistry::new());
        ctx.extensions.insert(InMemoryInteractionStore::new(10));
        ctx.extensions.insert(GovernanceConfig {
            default: posture,
            ..GovernanceConfig::default()
        });
        let store = InMemoryAuditStore::new(10);
        ctx.extensions.insert(store.clone());
        let result = EvaluatorFilter {
            max_body_bytes: 1024,
        }
        .handle_body(&mut ctx, &mut None)
        .await
        .unwrap();
        let event = store
            .query(&wanaku_types::audit::AuditQuery::default())
            .events
            .remove(0);
        (result, event)
    }

    #[tokio::test]
    async fn fuel_exhaustion_applies_failure_policy_without_blocking_audit() {
        let state = looping_evaluator();
        for (mode, failure, allowed) in [
            (EnforcementMode::Enforce, FailureBehavior::Deny, false),
            (EnforcementMode::Enforce, FailureBehavior::Allow, true),
            (EnforcementMode::Audit, FailureBehavior::Deny, true),
        ] {
            let posture = GovernancePosture {
                mode,
                on_failure: failure,
                ..GovernancePosture::default()
            };
            let (result, event) = Box::pin(fuel_failure_result(&state, posture)).await;
            assert_eq!(matches!(result, FilterAction::Continue), allowed);
            assert_eq!(event.reason_code, "evaluator_processor_execution_failed");
            assert_eq!(
                event.attributes["effective_action"],
                if allowed { "pass" } else { "block" }
            );
        }
    }

    #[tokio::test]
    #[expect(
        clippy::too_many_lines,
        reason = "both remote engine fixtures verify no upstream requests and skipped audit outcomes"
    )]
    async fn basic_audit_skips_both_remote_engines_before_dependencies() {
        let server = wiremock::MockServer::start().await;
        let request = request();
        for engine in [
            serde_json::json!({"type":"llm","connection":"remote","operation":"classify","prompt":"test"}),
            serde_json::json!({"type":"typesafe-system-one","connection":"remote","noul":{"id":"test","instructions":"test"}}),
        ] {
            let state = EvaluatorState::new();
            state
                .load_llm_connections(vec![crate::config::LlmConnection {
                    name: "remote".into(),
                    url: server.uri(),
                    model: "test".into(),
                    api_key: "secret".into(),
                }])
                .unwrap();
            state
                .load_system_one_connections(vec![crate::config::SystemOneConnection {
                    name: "remote".into(),
                    url: server.uri(),
                    model: "test".into(),
                    api_key: "secret".into(),
                }])
                .unwrap();
            state.load_evaluators(vec![serde_json::from_value(serde_json::json!({"name":"remote","trigger":{"method":"tools/call"},"engine":engine,"processor":{"path":"missing.wasm"}})).unwrap()]);
            let mut ctx = make_filter_context(&request);
            ctx.set_metadata(wanaku_filters::MCP_METHOD_KEY, "tools/call");
            let mut governance = GovernanceConfig::default();
            governance.default.mode = EnforcementMode::Audit;
            ctx.extensions.insert(governance);
            ctx.extensions.insert(state);
            let store = InMemoryAuditStore::new(10);
            ctx.extensions.insert(store.clone());
            assert!(matches!(
                EvaluatorFilter {
                    max_body_bytes: 1024
                }
                .handle_body(&mut ctx, &mut None)
                .await
                .unwrap(),
                FilterAction::Continue
            ));
            let events = store.query(&wanaku_types::audit::AuditQuery::default());
            assert_eq!(
                events.events[0].reason_code,
                "evaluator_external_evaluation_disabled"
            );
            assert_eq!(
                events.events[0].attributes["would_be_action"],
                serde_json::Value::Null
            );
        }
        assert!(server.received_requests().await.unwrap().is_empty());
    }
}
