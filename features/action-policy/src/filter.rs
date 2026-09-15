use std::collections::BTreeMap;

use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use wanaku_filters::json_rpc::{McpRequestView, RequestViewError};
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::audit::{
    AuditCategory, AuditDecision, AuditEvent, AuditStore, InMemoryAuditStore,
};
use wanaku_types::governance::{
    EnforcementMode, FailureBehavior, GovernanceConfig, GovernancePosture, NoMatchBehavior,
};
use wanaku_types::registry::{DEFAULT_NAMESPACE, ToolRegistry};
use wanaku_types::{PROMPTS_GET, RESOURCES_READ, TOOLS_CALL};

use crate::{
    ActionContext, ActionPolicyState, PolicyDecision, PolicyEngine, PolicySnapshot, PolicyState,
    TargetType,
};

const POLICY_DENIED_JSON_RPC_CODE: i32 = -32003;
const INVALID_ACTION_REASON_CODE: &str = "invalid_action_request";
const INVALID_ACTION_MESSAGE: &str = "The action request is invalid.";
const INVALID_POLICY_REASON_CODE: &str = "action_policy_invalid";
const INVALID_POLICY_MESSAGE: &str = "The action policy is unavailable.";
const NO_MATCH_REASON_CODE: &str = "governance_no_match";
const NO_MATCH_MESSAGE: &str = "No governance policy permits this action.";
const ACTION_POLICY_ALLOWED_REASON_CODE: &str = "action_policy_allowed";
const ACTION_POLICY_ALLOWED_MESSAGE: &str = "Action policy allowed the request.";

wanaku_filters::body_filter_boilerplate!(ActionPolicyFilter, "wanaku_action_policy");

impl ActionPolicyFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let Some(method) = ctx.get_metadata(wanaku_filters::MCP_METHOD_KEY) else {
            return Ok(FilterAction::Continue);
        };
        if !is_governed_method(method) {
            return Ok(FilterAction::Continue);
        }

        let id = wanaku_filters::response::json_rpc_id_from_metadata(
            ctx.get_metadata(wanaku_filters::MCP_ID_KEY),
        );
        let namespace = ctx
            .get_metadata(wanaku_types::NAMESPACE_METADATA_KEY)
            .unwrap_or(DEFAULT_NAMESPACE);
        let posture = ctx
            .extensions
            .get::<GovernanceConfig>()
            .cloned()
            .unwrap_or_default()
            .resolve(namespace);
        if posture.mode == EnforcementMode::Disabled {
            tracing::warn!(
                namespace,
                reason = posture.disabled_reason.as_deref().unwrap_or("unspecified"),
                "governance is disabled for namespace"
            );
            return Ok(FilterAction::Continue);
        }
        let Some(state) = ctx.extensions.get::<ActionPolicyState>() else {
            record_simple_decision(
                ctx,
                body.as_ref(),
                AuditDecision::Error,
                INVALID_POLICY_REASON_CODE,
                INVALID_POLICY_MESSAGE,
            );
            return Ok(apply_failure(&posture, &id));
        };
        let snapshot = state.snapshot();
        Ok(evaluate_snapshot(
            ctx,
            body.as_ref(),
            snapshot,
            &posture,
            &id,
        ))
    }
}

fn evaluate_snapshot(
    ctx: &HttpFilterContext<'_>,
    body: Option<&Bytes>,
    snapshot: PolicySnapshot,
    posture: &GovernancePosture,
    id: &serde_json::Value,
) -> FilterAction {
    let policy = match snapshot {
        PolicySnapshot::Valid(policy) => policy,
        PolicySnapshot::Unconfigured => {
            record_simple_decision(
                ctx,
                body,
                no_match_audit_decision(posture),
                NO_MATCH_REASON_CODE,
                NO_MATCH_MESSAGE,
            );
            return apply_no_match(posture, id);
        }
        PolicySnapshot::Invalid => {
            record_simple_decision(
                ctx,
                body,
                AuditDecision::Error,
                INVALID_POLICY_REASON_CODE,
                INVALID_POLICY_MESSAGE,
            );
            return apply_failure(posture, id);
        }
    };
    let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() else {
        record_simple_decision(
            ctx,
            body,
            AuditDecision::Error,
            INVALID_POLICY_REASON_CODE,
            INVALID_POLICY_MESSAGE,
        );
        return apply_failure(posture, id);
    };
    evaluate_request_audited(
        ctx,
        RequestEvaluation {
            method: ctx
                .get_metadata(wanaku_filters::MCP_METHOD_KEY)
                .unwrap_or_default(),
            namespace: ctx
                .get_metadata(wanaku_types::NAMESPACE_METADATA_KEY)
                .unwrap_or(DEFAULT_NAMESPACE),
            body,
            policy: &policy,
            posture,
            registry,
            id,
        },
    )
}

fn is_governed_method(method: &str) -> bool {
    matches!(method, TOOLS_CALL | RESOURCES_READ | PROMPTS_GET)
}

#[derive(Clone, Copy)]
struct RequestEvaluation<'a> {
    method: &'a str,
    namespace: &'a str,
    body: Option<&'a Bytes>,
    policy: &'a crate::CompiledPolicy,
    posture: &'a GovernancePosture,
    registry: &'a InMemoryRegistry,
    id: &'a serde_json::Value,
}

struct DecisionAuditContext<'a> {
    store: &'a InMemoryAuditStore,
    request_id: Option<&'a str>,
    target: Option<&'a str>,
    policy_revision: Option<String>,
}

impl<'a> DecisionAuditContext<'a> {
    fn from_http(ctx: &'a HttpFilterContext<'_>) -> Option<Self> {
        Some(Self {
            store: ctx.extensions.get::<InMemoryAuditStore>()?,
            request_id: ctx.request_id(),
            target: ctx.get_metadata(wanaku_filters::MCP_NAME_KEY),
            policy_revision: ctx
                .extensions
                .get::<ActionPolicyState>()
                .and_then(|state| state.revision_store().active_revision_id())
                .map(|revision| revision.to_string()),
        })
    }
}

#[cfg(test)]
fn evaluate_request(input: RequestEvaluation<'_>) -> FilterAction {
    evaluate_request_impl(None, input)
}

fn evaluate_request_audited(
    ctx: &HttpFilterContext<'_>,
    input: RequestEvaluation<'_>,
) -> FilterAction {
    let audit = DecisionAuditContext::from_http(ctx);
    evaluate_request_impl(audit.as_ref(), input)
}

fn evaluate_request_impl(
    audit_ctx: Option<&DecisionAuditContext<'_>>,
    input: RequestEvaluation<'_>,
) -> FilterAction {
    let RequestEvaluation {
        method,
        namespace,
        body,
        policy,
        posture,
        registry,
        id,
    } = input;
    let Ok(view) = McpRequestView::parse(body) else {
        record_malformed_decision(audit_ctx, body, namespace, method);
        return policy_error(id, INVALID_ACTION_REASON_CODE, INVALID_ACTION_MESSAGE);
    };
    let Ok(context) = action_context(method, namespace, &view, registry) else {
        record_malformed_decision(audit_ctx, body, namespace, method);
        return policy_error(id, INVALID_ACTION_REASON_CODE, INVALID_ACTION_MESSAGE);
    };
    let decision = PolicyEngine::evaluate(PolicyState::Available(policy), &context);
    if let Some(audit) = audit_ctx {
        record_policy_decision(audit, &input, &decision);
    }
    if posture.mode == EnforcementMode::Audit {
        tracing::info!(
            namespace,
            decision = ?decision,
            "action-policy decision recorded in audit mode"
        );
        return FilterAction::Continue;
    }
    match &decision {
        PolicyDecision::ExplicitDeny { .. } => policy_error(
            id,
            decision
                .deny_reason_code()
                .unwrap_or(crate::DEFAULT_DENY_REASON_CODE),
            decision
                .deny_message()
                .unwrap_or(crate::DEFAULT_DENY_MESSAGE),
        ),
        PolicyDecision::ExplicitAllow { .. } => FilterAction::Continue,
        PolicyDecision::NoMatch => apply_no_match(posture, id),
        PolicyDecision::PolicyUnavailable | PolicyDecision::PolicyInvalid => {
            apply_failure(posture, id)
        }
    }
}

fn record_malformed_decision(
    audit: Option<&DecisionAuditContext<'_>>,
    body: Option<&Bytes>,
    namespace: &str,
    method: &str,
) {
    if let Some(audit) = audit {
        record_event(
            audit,
            body,
            Some(namespace),
            AuditEvent::new(
                AuditCategory::Decision,
                AuditDecision::RejectMalformed,
                method,
                INVALID_ACTION_REASON_CODE,
                INVALID_ACTION_MESSAGE,
            ),
        );
    }
}

fn record_policy_decision(
    audit: &DecisionAuditContext<'_>,
    input: &RequestEvaluation<'_>,
    decision: &PolicyDecision,
) {
    let (outcome, reason_code, explanation) = match decision {
        PolicyDecision::ExplicitDeny { reason, .. } => {
            (AuditDecision::Block, reason.reason_code(), reason.message())
        }
        PolicyDecision::ExplicitAllow { .. } => (
            AuditDecision::Allow,
            ACTION_POLICY_ALLOWED_REASON_CODE,
            ACTION_POLICY_ALLOWED_MESSAGE,
        ),
        PolicyDecision::NoMatch => (
            no_match_audit_decision(input.posture),
            NO_MATCH_REASON_CODE,
            NO_MATCH_MESSAGE,
        ),
        PolicyDecision::PolicyUnavailable => (
            AuditDecision::Error,
            INVALID_POLICY_REASON_CODE,
            INVALID_POLICY_MESSAGE,
        ),
        PolicyDecision::PolicyInvalid => (
            AuditDecision::Error,
            INVALID_POLICY_REASON_CODE,
            INVALID_POLICY_MESSAGE,
        ),
    };
    let mut event = AuditEvent::new(
        AuditCategory::Decision,
        outcome,
        input.method,
        reason_code,
        explanation,
    );
    event.attributes.insert(
        "policy_decision".to_owned(),
        serde_json::json!(policy_decision_label(decision)),
    );
    event.attributes.insert(
        "enforcement_action".to_owned(),
        serde_json::json!(enforcement_action(input.posture, decision)),
    );
    if let Some(details) = decision.details() {
        event.attributes.insert(
            "matched_rule_ids".to_owned(),
            serde_json::json!(
                details
                    .matched_rules()
                    .iter()
                    .map(|rule| rule.rule_id())
                    .collect::<Vec<_>>()
            ),
        );
        event.attributes.insert(
            "deny_rule_ids".to_owned(),
            serde_json::json!(
                details
                    .matched_rules()
                    .iter()
                    .filter(|rule| rule.effect() == crate::Effect::Deny)
                    .map(|rule| rule.rule_id())
                    .collect::<Vec<_>>()
            ),
        );
        event.attributes.insert(
            "deny_reason_codes".to_owned(),
            serde_json::json!(
                details
                    .matched_rules()
                    .iter()
                    .filter(|rule| rule.effect() == crate::Effect::Deny)
                    .map(|rule| {
                        rule.reason_code()
                            .unwrap_or(crate::DEFAULT_DENY_REASON_CODE)
                    })
                    .collect::<Vec<_>>()
            ),
        );
    }
    record_event(audit, input.body, Some(input.namespace), event);
}

const fn policy_decision_label(decision: &PolicyDecision) -> &'static str {
    match decision {
        PolicyDecision::ExplicitAllow { .. } => "explicit_allow",
        PolicyDecision::ExplicitDeny { .. } => "explicit_deny",
        PolicyDecision::NoMatch => "no_match",
        PolicyDecision::PolicyUnavailable => "policy_unavailable",
        PolicyDecision::PolicyInvalid => "policy_invalid",
    }
}

const fn enforcement_action(
    posture: &GovernancePosture,
    decision: &PolicyDecision,
) -> &'static str {
    if matches!(
        posture.mode,
        EnforcementMode::Disabled | EnforcementMode::Audit
    ) {
        return "continue";
    }
    match decision {
        PolicyDecision::ExplicitAllow { .. } => "continue",
        PolicyDecision::ExplicitDeny { .. } => "reject",
        PolicyDecision::NoMatch => match posture.no_match {
            NoMatchBehavior::Allow => "continue",
            NoMatchBehavior::Deny => "reject",
        },
        PolicyDecision::PolicyUnavailable | PolicyDecision::PolicyInvalid => {
            match posture.on_failure {
                FailureBehavior::Allow => "continue",
                FailureBehavior::Deny => "reject",
            }
        }
    }
}

fn record_simple_decision(
    ctx: &HttpFilterContext<'_>,
    body: Option<&Bytes>,
    decision: AuditDecision,
    reason_code: &str,
    explanation: &str,
) {
    let Some(method) = ctx.get_metadata(wanaku_filters::MCP_METHOD_KEY) else {
        return;
    };
    let Some(audit) = DecisionAuditContext::from_http(ctx) else {
        return;
    };
    record_event(
        &audit,
        body,
        ctx.get_metadata(wanaku_types::NAMESPACE_METADATA_KEY),
        AuditEvent::new(
            AuditCategory::Decision,
            decision,
            method,
            reason_code,
            explanation,
        ),
    );
}

fn record_event(
    audit: &DecisionAuditContext<'_>,
    body: Option<&Bytes>,
    namespace: Option<&str>,
    mut event: AuditEvent,
) {
    event.namespace = namespace.map(str::to_owned);
    event.filter = Some("wanaku_action_policy".to_owned());
    event.target_type = target_type(&event.operation).map(str::to_owned);
    event.policy_revision.clone_from(&audit.policy_revision);
    event.target = audit.target.map(str::to_owned);
    wanaku_types::audit::add_mcp_request_context_with_id(
        &mut event,
        body.map(bytes::Bytes::as_ref),
        audit.request_id,
    );
    audit.store.record(event);
}

fn no_match_audit_decision(posture: &GovernancePosture) -> AuditDecision {
    if posture.mode == EnforcementMode::Audit || posture.no_match == NoMatchBehavior::Allow {
        AuditDecision::Allow
    } else {
        AuditDecision::Block
    }
}

fn target_type(method: &str) -> Option<&'static str> {
    match method {
        TOOLS_CALL => Some("tool"),
        RESOURCES_READ => Some("resource"),
        PROMPTS_GET => Some("prompt"),
        _ => None,
    }
}

fn apply_no_match(posture: &GovernancePosture, id: &serde_json::Value) -> FilterAction {
    if posture.mode == EnforcementMode::Audit {
        return FilterAction::Continue;
    }
    match posture.no_match {
        NoMatchBehavior::Allow => FilterAction::Continue,
        NoMatchBehavior::Deny => policy_error(id, NO_MATCH_REASON_CODE, NO_MATCH_MESSAGE),
    }
}

fn apply_failure(posture: &GovernancePosture, id: &serde_json::Value) -> FilterAction {
    if posture.mode == EnforcementMode::Audit {
        return FilterAction::Continue;
    }
    match posture.on_failure {
        FailureBehavior::Allow => FilterAction::Continue,
        FailureBehavior::Deny => {
            policy_error(id, INVALID_POLICY_REASON_CODE, INVALID_POLICY_MESSAGE)
        }
    }
}

fn action_context(
    method: &str,
    namespace: &str,
    view: &McpRequestView,
    registry: &InMemoryRegistry,
) -> Result<ActionContext, RequestViewError> {
    let input = serde_json::Value::Object(view.params()?.clone());
    match method {
        TOOLS_CALL => tool_context(namespace, view, registry, input),
        RESOURCES_READ => resource_context(namespace, view, registry, input),
        PROMPTS_GET => prompt_context(namespace, view, input),
        _ => Err(RequestViewError::UnexpectedMethod {
            expected: "governed MCP method",
            actual: method.to_owned(),
        }),
    }
}

fn tool_context(
    namespace: &str,
    view: &McpRequestView,
    registry: &InMemoryRegistry,
    input: serde_json::Value,
) -> Result<ActionContext, RequestViewError> {
    let request = view.tool_call()?;
    let tool = registry.get_tool_in_namespace(namespace, request.name());
    let context = ActionContext::new(namespace, TOOLS_CALL, TargetType::Tool, input)
        .with_target_name(request.name());
    Ok(match tool {
        Some(tool) => context.with_labels(to_labels(&tool.labels)),
        None => context,
    })
}

fn resource_context(
    namespace: &str,
    view: &McpRequestView,
    registry: &InMemoryRegistry,
    input: serde_json::Value,
) -> Result<ActionContext, RequestViewError> {
    let request = view.resource_read()?;
    let resource = wanaku_filters::resource_read::find_resource_in_namespace(
        registry,
        namespace,
        request.uri(),
    );
    let context = ActionContext::new(namespace, RESOURCES_READ, TargetType::Resource, input)
        .with_uri(request.uri());
    Ok(match resource {
        Some(resource) => context
            .with_target_name(resource.name)
            .with_labels(to_labels(&resource.labels)),
        None => context,
    })
}

fn prompt_context(
    namespace: &str,
    view: &McpRequestView,
    input: serde_json::Value,
) -> Result<ActionContext, RequestViewError> {
    let request = view.prompt_get()?;
    Ok(
        ActionContext::new(namespace, PROMPTS_GET, TargetType::Prompt, input)
            .with_target_name(request.name()),
    )
}

fn to_labels(labels: &std::collections::HashMap<String, String>) -> BTreeMap<String, String> {
    labels
        .iter()
        .map(|(key, value)| (key.clone(), value.clone()))
        .collect()
}

fn policy_error(id: &serde_json::Value, reason_code: &str, message: &str) -> FilterAction {
    let response = serde_json::json!({
        "jsonrpc": "2.0",
        "id": id,
        "error": {
            "code": POLICY_DENIED_JSON_RPC_CODE,
            "message": message,
            "data": { "reason_code": reason_code }
        }
    });
    FilterAction::Reject(wanaku_filters::response::json_response(Bytes::from(
        response.to_string(),
    )))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::DEFAULT_DENY_REASON_CODE;
    use wanaku_types::audit::AuditQuery;
    use wanaku_types::registry::{
        PromptEntry, PromptRegistry, ResourceEntry, ResourceRegistry, ToolEntry,
    };

    fn compile_policy(rule: &serde_json::Value) -> crate::CompiledPolicy {
        compile_policy_rules(std::slice::from_ref(rule))
    }

    fn compile_policy_rules(rules: &[serde_json::Value]) -> crate::CompiledPolicy {
        let policy: crate::ActionPolicy =
            serde_json::from_value(serde_json::json!({ "rules": rules }))
                .expect("valid test policy");
        policy.compile().expect("compilable test policy")
    }

    fn request(method: &str, params: &serde_json::Value) -> Bytes {
        Bytes::from(
            serde_json::json!({
                "jsonrpc": "2.0",
                "id": 7,
                "method": method,
                "params": params.clone()
            })
            .to_string(),
        )
    }

    fn assert_denied(action: FilterAction, expected_reason: &str) {
        assert!(matches!(action, FilterAction::Reject(_)));
        if let FilterAction::Reject(rejection) = action {
            assert!(rejection.body.is_some());
            if let Some(body) = rejection.body {
                let response = serde_json::from_slice::<serde_json::Value>(&body);
                assert!(response.is_ok());
                if let Ok(response) = response {
                    assert_eq!(response["id"], 7);
                    assert_eq!(response["error"]["data"]["reason_code"], expected_reason);
                }
            }
        }
    }

    #[test]
    #[expect(clippy::too_many_lines, reason = "three governed MCP method fixtures")]
    fn denies_tool_resource_and_prompt_actions() {
        let registry = InMemoryRegistry::new();
        let posture = GovernancePosture::default();
        registry.register_tool(ToolEntry {
            name: "delete".to_owned(),
            description: String::new(),
            uri: "tool://delete".to_owned(),
            type_: "mcp-forward".to_owned(),
            input_schema: serde_json::json!({}),
            labels: std::collections::HashMap::from([("risk".to_owned(), "high".to_owned())]),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        });
        registry.register_resource(ResourceEntry {
            name: "secrets".to_owned(),
            description: String::new(),
            location: "file:///secrets".to_owned(),
            type_: "mcp-forward".to_owned(),
            mime_type: String::new(),
            labels: std::collections::HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        });
        registry.register_prompt(PromptEntry {
            name: "admin".to_owned(),
            description: String::new(),
            arguments: Vec::new(),
            messages: Vec::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
        });

        let cases = [
            (
                TOOLS_CALL,
                serde_json::json!({"name": "delete", "arguments": {}}),
                serde_json::json!({
                    "id": "deny-tool", "effect": "deny", "reason_code": "tool_denied",
                    "selectors": {"operation": TOOLS_CALL, "target_name": {"matcher": "exact", "value": "delete"}, "labels": {"risk": "high"}}
                }),
                "tool_denied",
            ),
            (
                RESOURCES_READ,
                serde_json::json!({"uri": "file:///secrets"}),
                serde_json::json!({
                    "id": "deny-resource", "effect": "deny", "reason_code": "resource_denied",
                    "selectors": {"operation": RESOURCES_READ, "uri": {"matcher": "exact", "value": "file:///secrets"}}
                }),
                "resource_denied",
            ),
            (
                PROMPTS_GET,
                serde_json::json!({"name": "admin"}),
                serde_json::json!({
                    "id": "deny-prompt", "effect": "deny", "reason_code": "prompt_denied",
                    "selectors": {"operation": PROMPTS_GET, "target_name": {"matcher": "exact", "value": "admin"}}
                }),
                "prompt_denied",
            ),
        ];

        for (method, params, rule, reason) in cases {
            let policy = compile_policy(&rule);
            let body = request(method, &params);
            let id = serde_json::json!(7);
            let action = evaluate_request(RequestEvaluation {
                method,
                namespace: DEFAULT_NAMESPACE,
                body: Some(&body),
                policy: &policy,
                posture: &posture,
                registry: &registry,
                id: &id,
            });
            assert_denied(action, reason);
        }
    }

    #[test]
    fn explicit_allow_and_no_match_continue() {
        let registry = InMemoryRegistry::new();
        let posture = GovernancePosture {
            no_match: NoMatchBehavior::Allow,
            ..GovernancePosture::default()
        };
        let policy = compile_policy(&serde_json::json!({
            "id": "allow", "effect": "allow",
            "selectors": {"operation": TOOLS_CALL}
        }));
        let body = request(TOOLS_CALL, &serde_json::json!({"name": "safe"}));
        let id = serde_json::json!(7);
        assert!(matches!(
            evaluate_request(RequestEvaluation {
                method: TOOLS_CALL,
                namespace: DEFAULT_NAMESPACE,
                body: Some(&body),
                policy: &policy,
                posture: &posture,
                registry: &registry,
                id: &id,
            }),
            FilterAction::Continue
        ));

        let body = request(PROMPTS_GET, &serde_json::json!({"name": "safe"}));
        assert!(matches!(
            evaluate_request(RequestEvaluation {
                method: PROMPTS_GET,
                namespace: DEFAULT_NAMESPACE,
                body: Some(&body),
                policy: &policy,
                posture: &posture,
                registry: &registry,
                id: &id,
            }),
            FilterAction::Continue
        ));
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "allow and deny fixtures verify emitted audit evidence"
    )]
    fn allowed_and_blocked_mcp_requests_emit_correlated_audit_events() {
        let registry = InMemoryRegistry::new();
        let posture = GovernancePosture::default();
        let store = InMemoryAuditStore::new(10);
        let id = serde_json::json!(7);

        let allow_policy = compile_policy(&serde_json::json!({
            "id": "allow-safe", "effect": "allow",
            "selectors": {"operation": TOOLS_CALL, "target_name": {"matcher": "exact", "value": "safe"}}
        }));
        let allow_body = request(
            TOOLS_CALL,
            &serde_json::json!({
                "name": "safe",
                "arguments": {"x-request-id": "conversation-allow"}
            }),
        );
        let allow_audit = DecisionAuditContext {
            store: &store,
            request_id: Some("request-allow"),
            target: Some("safe"),
            policy_revision: Some("revision-1".to_owned()),
        };
        let allow_action = evaluate_request_impl(
            Some(&allow_audit),
            RequestEvaluation {
                method: TOOLS_CALL,
                namespace: DEFAULT_NAMESPACE,
                body: Some(&allow_body),
                policy: &allow_policy,
                posture: &posture,
                registry: &registry,
                id: &id,
            },
        );
        assert!(matches!(allow_action, FilterAction::Continue));

        let deny_policy = compile_policy_rules(&[
            serde_json::json!({
                "id": "deny-default", "effect": "deny",
                "selectors": {"operation": TOOLS_CALL, "target_name": {"matcher": "exact", "value": "unsafe"}}
            }),
            serde_json::json!({
                "id": "deny-explicit", "effect": "deny", "reason_code": "explicit_denial",
                "selectors": {"operation": TOOLS_CALL, "target_name": {"matcher": "exact", "value": "unsafe"}}
            }),
        ]);
        let deny_body = request(
            TOOLS_CALL,
            &serde_json::json!({
                "name": "unsafe",
                "arguments": {"x-request-id": "conversation-deny"}
            }),
        );
        let deny_audit = DecisionAuditContext {
            store: &store,
            request_id: Some("request-deny"),
            target: Some("unsafe"),
            policy_revision: Some("revision-1".to_owned()),
        };
        let deny_action = evaluate_request_impl(
            Some(&deny_audit),
            RequestEvaluation {
                method: TOOLS_CALL,
                namespace: DEFAULT_NAMESPACE,
                body: Some(&deny_body),
                policy: &deny_policy,
                posture: &posture,
                registry: &registry,
                id: &id,
            },
        );
        assert_denied(deny_action, DEFAULT_DENY_REASON_CODE);

        let events = store.query(&AuditQuery::default()).events;
        assert_eq!(events.len(), 2);
        let denied = &events[0];
        assert_eq!(denied.decision, AuditDecision::Block);
        assert_eq!(denied.request_id.as_deref(), Some("request-deny"));
        assert_eq!(denied.correlation_id, "request-deny");
        assert_eq!(denied.stream_id, "request-deny");
        assert_eq!(denied.conversation_id.as_deref(), Some("conversation-deny"));
        assert_eq!(
            denied.attributes["deny_rule_ids"],
            serde_json::json!(["deny-default", "deny-explicit"])
        );
        assert_eq!(
            denied.attributes["deny_reason_codes"],
            serde_json::json!([DEFAULT_DENY_REASON_CODE, "explicit_denial"])
        );
        assert_eq!(denied.attributes["policy_decision"], "explicit_deny");
        assert_eq!(denied.attributes["enforcement_action"], "reject");
        let allowed = &events[1];
        assert_eq!(allowed.decision, AuditDecision::Allow);
        assert_eq!(allowed.request_id.as_deref(), Some("request-allow"));
        assert_eq!(allowed.correlation_id, "request-allow");
        assert_eq!(allowed.stream_id, "request-allow");
        assert_eq!(allowed.reason_code, ACTION_POLICY_ALLOWED_REASON_CODE);
        assert_eq!(allowed.explanation, ACTION_POLICY_ALLOWED_MESSAGE);
        assert_eq!(
            allowed.conversation_id.as_deref(),
            Some("conversation-allow")
        );
        assert_eq!(allowed.attributes["policy_decision"], "explicit_allow");
        assert_eq!(allowed.attributes["enforcement_action"], "continue");
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "resource and tool registry fixtures verify URI isolation"
    )]
    fn resource_name_comes_from_registry_and_tool_uri_is_not_exposed() {
        let registry = InMemoryRegistry::new();
        let posture = GovernancePosture {
            no_match: NoMatchBehavior::Allow,
            ..GovernancePosture::default()
        };
        registry.register_resource(ResourceEntry {
            name: "registered-name".to_owned(),
            description: String::new(),
            location: "file:///requested-resource".to_owned(),
            type_: "mcp-forward".to_owned(),
            mime_type: String::new(),
            labels: std::collections::HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        });
        registry.register_tool(ToolEntry {
            name: "tool-with-uri".to_owned(),
            description: String::new(),
            uri: "file:///tool-location".to_owned(),
            type_: "mcp-forward".to_owned(),
            input_schema: serde_json::json!({}),
            labels: std::collections::HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        });
        let id = serde_json::json!(7);

        let resource_policy = compile_policy(&serde_json::json!({
            "id": "deny-resource-name", "effect": "deny",
            "selectors": {
                "operation": RESOURCES_READ,
                "target_name": {"matcher": "exact", "value": "registered-name"}
            }
        }));
        let resource_body = request(
            RESOURCES_READ,
            &serde_json::json!({"uri": "file:///requested-resource"}),
        );
        assert_denied(
            evaluate_request(RequestEvaluation {
                method: RESOURCES_READ,
                namespace: DEFAULT_NAMESPACE,
                body: Some(&resource_body),
                policy: &resource_policy,
                posture: &posture,
                registry: &registry,
                id: &id,
            }),
            DEFAULT_DENY_REASON_CODE,
        );

        let tool_uri_policy = compile_policy(&serde_json::json!({
            "id": "deny-tool-uri", "effect": "deny",
            "selectors": {
                "operation": TOOLS_CALL,
                "uri": {"matcher": "exact", "value": "file:///tool-location"}
            }
        }));
        let tool_body = request(
            TOOLS_CALL,
            &serde_json::json!({"name": "tool-with-uri", "arguments": {}}),
        );
        assert!(matches!(
            evaluate_request(RequestEvaluation {
                method: TOOLS_CALL,
                namespace: DEFAULT_NAMESPACE,
                body: Some(&tool_body),
                policy: &tool_uri_policy,
                posture: &posture,
                registry: &registry,
                id: &id,
            }),
            FilterAction::Continue
        ));
    }

    #[test]
    fn malformed_governed_request_is_safely_rejected() {
        let registry = InMemoryRegistry::new();
        let posture = GovernancePosture::default();
        let policy = compile_policy(&serde_json::json!({
            "id": "deny", "effect": "deny", "selectors": {"operation": TOOLS_CALL}
        }));
        let malformed = Bytes::from("not JSON");
        let id = serde_json::json!(7);
        let action = evaluate_request(RequestEvaluation {
            method: TOOLS_CALL,
            namespace: DEFAULT_NAMESPACE,
            body: Some(&malformed),
            policy: &policy,
            posture: &posture,
            registry: &registry,
            id: &id,
        });
        assert_denied(action, INVALID_ACTION_REASON_CODE);
    }

    #[test]
    fn posture_behaviors_are_applied() {
        let id = serde_json::json!(7);
        let permissive = GovernancePosture {
            no_match: NoMatchBehavior::Allow,
            on_failure: FailureBehavior::Allow,
            ..GovernancePosture::default()
        };

        assert!(matches!(
            apply_no_match(&permissive, &id),
            FilterAction::Continue
        ));
        assert!(matches!(
            apply_failure(&permissive, &id),
            FilterAction::Continue
        ));
        assert_denied(
            apply_no_match(&GovernancePosture::default(), &id),
            NO_MATCH_REASON_CODE,
        );
        assert_denied(
            apply_failure(&GovernancePosture::default(), &id),
            INVALID_POLICY_REASON_CODE,
        );

        let audit = GovernancePosture {
            mode: EnforcementMode::Audit,
            ..GovernancePosture::default()
        };
        assert!(matches!(
            apply_no_match(&audit, &id),
            FilterAction::Continue
        ));
        assert!(matches!(apply_failure(&audit, &id), FilterAction::Continue));

        assert_eq!(
            no_match_audit_decision(&GovernancePosture::default()),
            AuditDecision::Block
        );
        assert_eq!(no_match_audit_decision(&permissive), AuditDecision::Allow);
        assert_eq!(no_match_audit_decision(&audit), AuditDecision::Allow);
    }

    #[test]
    fn audit_mode_does_not_enforce_policy_denial() {
        let registry = InMemoryRegistry::new();
        let store = InMemoryAuditStore::new(1);
        let policy = compile_policy(&serde_json::json!({
            "id": "deny", "effect": "deny", "selectors": {"operation": TOOLS_CALL}
        }));
        let posture = GovernancePosture {
            mode: EnforcementMode::Audit,
            ..GovernancePosture::default()
        };
        let body = request(TOOLS_CALL, &serde_json::json!({"name": "unsafe"}));
        let id = serde_json::json!(7);
        let audit = DecisionAuditContext {
            store: &store,
            request_id: Some("audit-mode-request"),
            target: Some("unsafe"),
            policy_revision: None,
        };

        assert!(matches!(
            evaluate_request_impl(
                Some(&audit),
                RequestEvaluation {
                    method: TOOLS_CALL,
                    namespace: DEFAULT_NAMESPACE,
                    body: Some(&body),
                    policy: &policy,
                    posture: &posture,
                    registry: &registry,
                    id: &id,
                }
            ),
            FilterAction::Continue
        ));
        let events = store.query(&AuditQuery::default()).events;
        assert_eq!(events.len(), 1);
        assert_eq!(events[0].decision, AuditDecision::Block);
        assert_eq!(events[0].attributes["policy_decision"], "explicit_deny");
        assert_eq!(events[0].attributes["enforcement_action"], "continue");
    }

    #[test]
    fn list_and_unrelated_methods_are_not_governed() {
        for method in ["tools/list", "resources/list", "prompts/list", "initialize"] {
            assert!(!is_governed_method(method));
        }
    }
}
