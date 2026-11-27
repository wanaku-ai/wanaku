use std::collections::BTreeMap;

use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use wanaku_filters::json_rpc::{McpRequestView, RequestViewError};
use wanaku_infra::registry::RuntimeRegistry;
use wanaku_types::audit::{AuditCategory, AuditDecision, AuditEvent, AuditHandle, AuditStore};
use wanaku_types::governance::{
    EnforcementMode, FailureBehavior, GovernanceConfig, GovernancePosture, NoMatchBehavior,
};
use wanaku_types::metadata::VIRTUAL_NAMESPACE_METADATA_KEY;
use wanaku_types::registry::{DEFAULT_NAMESPACE, ToolRegistry};
use wanaku_types::{PROMPTS_GET, RESOURCES_READ, TOOLS_CALL};

use crate::revision::RevisionId;
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
        let Some(method) = governed_method(ctx) else {
            return Ok(FilterAction::Continue);
        };
        if !is_governed_method(method) {
            return Ok(FilterAction::Continue);
        }

        let id = wanaku_filters::response::json_rpc_id_from_metadata(
            ctx.get_metadata(wanaku_filters::MCP_ID_KEY),
        );
        let concrete = ctx
            .get_metadata(wanaku_types::NAMESPACE_METADATA_KEY)
            .unwrap_or(DEFAULT_NAMESPACE);
        let virtual_namespace = ctx.get_metadata(VIRTUAL_NAMESPACE_METADATA_KEY);
        let governance = ctx
            .extensions
            .get::<GovernanceConfig>()
            .cloned()
            .unwrap_or_default();
        let state = ctx.extensions.get::<ActionPolicyState>();
        let snapshot = state.map_or(PolicySnapshot::Invalid, ActionPolicyState::snapshot);
        let mut outcome = FilterAction::Continue;
        for namespace in std::iter::once(concrete).chain(virtual_namespace) {
            let posture = governance.resolve(namespace);
            if posture.mode == EnforcementMode::Disabled {
                tracing::warn!(namespace, "governance is disabled for namespace");
                continue;
            }
            let action = evaluate_snapshot_in_scope(
                ctx,
                body.as_ref(),
                snapshot.clone(),
                (&posture, namespace),
                &id,
            );
            if !matches!(action, FilterAction::Continue) {
                outcome = action;
            }
        }
        Ok(outcome)
    }
}

#[cfg(test)]
fn evaluate_snapshot(
    ctx: &HttpFilterContext<'_>,
    body: Option<&Bytes>,
    snapshot: PolicySnapshot,
    posture: &GovernancePosture,
    id: &serde_json::Value,
) -> FilterAction {
    let namespace = ctx
        .get_metadata(wanaku_types::NAMESPACE_METADATA_KEY)
        .unwrap_or(DEFAULT_NAMESPACE);
    evaluate_snapshot_in_scope(ctx, body, snapshot, (posture, namespace), id)
}

fn evaluate_snapshot_in_scope(
    ctx: &HttpFilterContext<'_>,
    body: Option<&Bytes>,
    snapshot: PolicySnapshot,
    scope: (&GovernancePosture, &str),
    id: &serde_json::Value,
) -> FilterAction {
    let (posture, namespace) = scope;
    let mut audit = DecisionAuditContext::from_http(ctx, snapshot.revision_id());
    if let Some(audit) = audit.as_mut() {
        audit.namespace = Some(namespace);
    }
    let active = match snapshot {
        PolicySnapshot::Valid(active) => active,
        PolicySnapshot::Unconfigured => {
            record_simple_decision(
                audit.as_ref(),
                body,
                no_match_audit_decision(posture),
                NO_MATCH_REASON_CODE,
                NO_MATCH_MESSAGE,
            );
            return apply_no_match(posture, id);
        }
        PolicySnapshot::Invalid => {
            record_simple_decision(
                audit.as_ref(),
                body,
                AuditDecision::Error,
                INVALID_POLICY_REASON_CODE,
                INVALID_POLICY_MESSAGE,
            );
            return apply_failure(posture, id);
        }
    };
    let Some(registry) = ctx.extensions.get::<RuntimeRegistry>() else {
        record_simple_decision(
            audit.as_ref(),
            body,
            AuditDecision::Error,
            INVALID_POLICY_REASON_CODE,
            INVALID_POLICY_MESSAGE,
        );
        return apply_failure(posture, id);
    };
    evaluate_request_impl(
        audit.as_ref(),
        RequestEvaluation {
            method: ctx
                .get_metadata("a2a.method")
                .or_else(|| ctx.get_metadata(wanaku_filters::MCP_METHOD_KEY))
                .unwrap_or_default(),
            namespace,
            concrete_namespace: ctx.get_metadata(wanaku_types::NAMESPACE_METADATA_KEY),
            selected_tool: ctx
                .extensions
                .get::<wanaku_types::wasm::ResolvedTool>()
                .map(|selected| &selected.0),
            body,
            policy: active.policy(),
            posture,
            registry,
            id,
            agent: ctx.get_metadata("wanaku.a2a.agent"),
        },
    )
}

fn governed_method<'a>(ctx: &'a HttpFilterContext<'_>) -> Option<&'a str> {
    ctx.get_metadata("a2a.method")
        .or_else(|| ctx.get_metadata(wanaku_filters::MCP_METHOD_KEY))
}

fn is_governed_method(method: &str) -> bool {
    matches!(method, TOOLS_CALL | RESOURCES_READ | PROMPTS_GET)
        || crate::a2a::is_supported_method(method)
}

#[derive(Clone, Copy)]
struct RequestEvaluation<'a> {
    method: &'a str,
    namespace: &'a str,
    body: Option<&'a Bytes>,
    policy: &'a crate::CompiledPolicy,
    posture: &'a GovernancePosture,
    registry: &'a RuntimeRegistry,
    id: &'a serde_json::Value,
    agent: Option<&'a str>,
    concrete_namespace: Option<&'a str>,
    selected_tool: Option<&'a wanaku_types::registry::ToolEntry>,
}

struct DecisionAuditContext<'a> {
    store: &'a dyn AuditStore,
    request_id: Option<&'a str>,
    operation: Option<&'a str>,
    namespace: Option<&'a str>,
    target: Option<&'a str>,
    a2a_version: Option<&'a str>,
    policy_revision: Option<String>,
    virtual_namespace: Option<&'a str>,
    concrete_namespace: Option<&'a str>,
    invocation: Option<&'a wanaku_types::wasm::InvocationContext>,
}

impl<'a> DecisionAuditContext<'a> {
    /// Build the audit context for one request. `policy_revision` must come from the
    /// snapshot that the request captured, never from the current active revision.
    fn from_http(
        ctx: &'a HttpFilterContext<'_>,
        policy_revision: Option<RevisionId>,
    ) -> Option<Self> {
        Some(Self {
            store: ctx.extensions.get::<AuditHandle>()?,
            request_id: ctx.request_id(),
            operation: governed_method(ctx),
            namespace: ctx.get_metadata(wanaku_types::NAMESPACE_METADATA_KEY),
            a2a_version: ctx.get_metadata("wanaku.a2a.version"),
            target: ctx
                .get_metadata("wanaku.a2a.agent")
                .or_else(|| ctx.get_metadata(wanaku_filters::MCP_NAME_KEY)),
            policy_revision: policy_revision.map(|revision| revision.to_string()),
            virtual_namespace: ctx.get_metadata(VIRTUAL_NAMESPACE_METADATA_KEY),
            concrete_namespace: ctx.get_metadata(wanaku_types::NAMESPACE_METADATA_KEY),
            invocation: ctx
                .extensions
                .get::<wanaku_types::wasm::InvocationContext>(),
        })
    }
}

#[cfg(test)]
fn evaluate_request(input: RequestEvaluation<'_>) -> FilterAction {
    evaluate_request_impl(None, input)
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
        id,
        ..
    } = input;
    let Ok(view) = McpRequestView::parse(body) else {
        record_malformed_decision(audit_ctx, body, namespace, method);
        return policy_error(id, INVALID_ACTION_REASON_CODE, INVALID_ACTION_MESSAGE);
    };
    let Ok(context) = scoped_action_context(&input, &view) else {
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

fn scoped_action_context(
    input: &RequestEvaluation<'_>,
    view: &McpRequestView,
) -> Result<ActionContext, RequestViewError> {
    let concrete = input.concrete_namespace.unwrap_or(input.namespace);
    let mut context = action_context(input.method, concrete, view, input.registry)?;
    match (input.method, input.selected_tool) {
        (TOOLS_CALL, Some(tool)) => context = context.with_labels(to_labels(&tool.labels)),
        _ => {}
    }
    if let Some(agent) = input
        .agent
        .filter(|_| crate::a2a::is_supported_method(input.method))
    {
        context = context.with_target_name(agent);
    }
    if input.namespace != concrete {
        if let Some(target) = context.target_name().map(str::to_owned) {
            context = context.with_target_name(format!("{concrete}/{target}"));
        }
        if input.method == RESOURCES_READ {
            context = context.with_uri(format!(
                "wanaku://{concrete}/{}",
                view.resource_read()?.uri()
            ));
        }
        context = context.with_namespace(input.namespace);
    }
    Ok(context)
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

/// Resolve traffic behavior from a decision and the production posture.
pub const fn enforcement_action(
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
    audit: Option<&DecisionAuditContext<'_>>,
    body: Option<&Bytes>,
    decision: AuditDecision,
    reason_code: &str,
    explanation: &str,
) {
    let Some(audit) = audit else {
        return;
    };
    let Some(method) = audit.operation else {
        return;
    };
    record_event(
        audit,
        body,
        audit.namespace,
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
    if let Some(invocation) = audit
        .invocation
        .filter(|context| !context.ancestry().is_empty())
    {
        event.attributes.insert(
            "handler_ancestry".to_owned(),
            serde_json::json!(invocation.ancestry()),
        );
    }
    if let Some(virtual_namespace) = audit.virtual_namespace {
        event.attributes.insert(
            "virtual_namespace".to_owned(),
            serde_json::json!(virtual_namespace),
        );
        event.attributes.insert(
            "concrete_namespace".to_owned(),
            serde_json::json!(audit.concrete_namespace),
        );
    }
    event.filter = Some("wanaku_action_policy".to_owned());
    event.target_type = target_type(&event.operation).map(str::to_owned);
    event.policy_revision.clone_from(&audit.policy_revision);
    event.target = audit.target.map(str::to_owned);
    wanaku_types::audit::add_mcp_request_context_with_id(
        &mut event,
        body.map(bytes::Bytes::as_ref),
        audit.request_id,
    );
    if crate::a2a::is_supported_method(&event.operation) {
        event.protocol = "a2a".to_owned();
        if let Some(version) = audit.a2a_version {
            event
                .attributes
                .insert("a2a_version".to_owned(), serde_json::json!(version));
        }
        add_a2a_task_context(&mut event, body);
    }
    audit.store.record(event);
}

fn add_a2a_task_context(event: &mut AuditEvent, body: Option<&Bytes>) {
    if let Some(payload) =
        body.and_then(|body| serde_json::from_slice::<serde_json::Value>(body).ok())
    {
        for (key, pointer) in [
            ("task_id", "/params/id"),
            ("context_id", "/params/contextId"),
            ("message_task_id", "/params/message/taskId"),
            ("message_context_id", "/params/message/contextId"),
        ] {
            if let Some(value) = payload.pointer(pointer).and_then(serde_json::Value::as_str) {
                event
                    .attributes
                    .insert(key.to_owned(), serde_json::json!(value));
            }
        }
    }
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
        method if crate::a2a::is_supported_method(method) => Some("agent"),
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

/// Build the same read-only action view for production and policy simulation.
pub fn action_context(
    method: &str,
    namespace: &str,
    view: &McpRequestView,
    registry: &RuntimeRegistry,
) -> Result<ActionContext, RequestViewError> {
    let input = serde_json::Value::Object(view.params()?.clone());
    match method {
        TOOLS_CALL => tool_context(namespace, view, registry, input),
        RESOURCES_READ => resource_context(namespace, view, registry, input),
        PROMPTS_GET => prompt_context(namespace, view, input),
        method if crate::a2a::is_supported_method(method) => Ok(ActionContext::new(
            namespace,
            method,
            TargetType::Agent,
            input,
        )),
        _ => Err(RequestViewError::UnexpectedMethod {
            expected: "governed MCP method",
            actual: method.to_owned(),
        }),
    }
}

fn tool_context(
    namespace: &str,
    view: &McpRequestView,
    registry: &RuntimeRegistry,
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
    registry: &RuntimeRegistry,
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
    use wanaku_infra::audit::InMemoryAuditStore;
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

    #[tokio::test]
    async fn virtual_actions_evaluate_and_audit_both_scopes() -> Result<(), FilterError> {
        for denied_scope in [Some("kafka"), Some("integration"), None] {
            let state = ActionPolicyState::new();
            let mut rules = vec![
                serde_json::json!({"id":"allow", "effect":"allow", "selectors":{"operation":TOOLS_CALL}}),
            ];
            if let Some(namespace) = denied_scope {
                rules.push(serde_json::json!({"id":"deny", "effect":"deny", "selectors":{"namespace":namespace,"operation":TOOLS_CALL}}));
            }
            let policy = serde_json::from_value(serde_json::json!({"rules":rules}))?;
            state
                .try_activate(
                    policy,
                    crate::state::ActivatePolicyParams {
                        origin: crate::revision::RevisionOrigin::Api,
                        actor: None,
                        expected_revision: None,
                    },
                )
                .map_err(|error| format!("{error:?}"))?;
            let recorder = std::sync::Arc::new(RecordingAuditStore::default());
            let filters = praxis_filter::FilterRegistry::with_builtins();
            let pipeline = praxis_filter::FilterPipeline::build(&mut [], &filters)?;
            let request = praxis_filter::Request {
                method: http::Method::POST,
                uri: http::Uri::from_static("/virtual-ns/integration/mcp"),
                headers: http::HeaderMap::new(),
            };
            let mut protocol =
                praxis_protocol::http::pingora::context::PingoraRequestCtx::default();
            let mut ctx = protocol.build_filter_context(&pipeline, &request, None);
            ctx.extensions.insert(state);
            ctx.extensions.insert(RuntimeRegistry::new());
            ctx.extensions.insert(AuditHandle::new(recorder.clone()));
            ctx.set_metadata(wanaku_types::NAMESPACE_METADATA_KEY, "kafka");
            ctx.set_metadata(VIRTUAL_NAMESPACE_METADATA_KEY, "integration");
            ctx.set_metadata(wanaku_filters::MCP_METHOD_KEY, TOOLS_CALL);
            ctx.set_metadata(wanaku_filters::MCP_NAME_KEY, "health-check");
            let mut body = Some(super::tests::request(
                TOOLS_CALL,
                &serde_json::json!({"name":"health-check"}),
            ));
            let filter = ActionPolicyFilter::from_config(&serde_yaml::Value::Null)?;
            let action = filter.on_request_body(&mut ctx, &mut body, true).await?;
            assert_eq!(
                matches!(action, FilterAction::Reject(_)),
                denied_scope.is_some()
            );
            let page = recorder.query(&AuditQuery::default());
            assert_eq!(page.events.len(), 2);
            for event in &page.events {
                assert_eq!(event.attributes["virtual_namespace"], "integration");
                assert_eq!(event.attributes["concrete_namespace"], "kafka");
            }
            assert!(
                page.events
                    .iter()
                    .any(|event| event.namespace.as_deref() == Some("kafka"))
            );
            assert!(
                page.events
                    .iter()
                    .any(|event| event.namespace.as_deref() == Some("integration"))
            );
        }
        Ok(())
    }

    #[test]
    fn policy_uses_the_tool_definition_selected_before_replacement() {
        let registry = RuntimeRegistry::new();
        let selected: ToolEntry = serde_json::from_value(serde_json::json!({
            "name":"delete", "description":"", "uri":"", "type":"wasm",
            "namespace":"default", "inputSchema":{}, "labels":{"risk":"high"}
        }))
        .unwrap();
        let mut replacement = selected.clone();
        replacement.labels.clear();
        registry.register_tool(replacement);
        let policy = compile_policy(&serde_json::json!({
            "id":"deny-risk", "effect":"deny",
            "selectors":{"operation":TOOLS_CALL,"labels":{"risk":"high"}}
        }));
        let posture = GovernancePosture::default();
        let body = request(TOOLS_CALL, &serde_json::json!({"name":"delete"}));
        let action = evaluate_request(RequestEvaluation {
            method: TOOLS_CALL,
            namespace: "default",
            concrete_namespace: Some("default"),
            body: Some(&body),
            policy: &policy,
            posture: &posture,
            registry: &registry,
            id: &serde_json::json!(7),
            agent: None,
            selected_tool: Some(&selected),
        });
        assert_denied(action, DEFAULT_DENY_REASON_CODE);
    }

    #[test]
    fn virtual_policy_targets_are_qualified() -> Result<(), FilterError> {
        let registry = RuntimeRegistry::new();
        let posture = GovernancePosture::default();
        let policy = compile_policy(
            &serde_json::json!({"id":"allow", "effect":"allow", "selectors":{
                "namespace":"integration", "target_name":{"matcher":"exact", "value":"kafka/health-check"}
            }}),
        );
        let body = request(TOOLS_CALL, &serde_json::json!({"name":"health-check"}));
        let action = evaluate_request(RequestEvaluation {
            method: TOOLS_CALL,
            namespace: "integration",
            concrete_namespace: Some("kafka"),
            body: Some(&body),
            policy: &policy,
            posture: &posture,
            registry: &registry,
            id: &serde_json::json!(7),
            agent: None,
            selected_tool: None,
        });
        assert!(matches!(action, FilterAction::Continue));
        Ok(())
    }

    #[test]
    #[expect(clippy::too_many_lines, reason = "three governed MCP method fixtures")]
    fn denies_tool_resource_and_prompt_actions() {
        let registry = RuntimeRegistry::new();
        let posture = GovernancePosture::default();
        registry.register_tool(ToolEntry {
            name: "delete".to_owned(),
            description: String::new(),
            uri: "tool://delete".to_owned(),
            type_: "mcp-forward".to_owned(),
            mcp: Default::default(),
            input_schema: serde_json::json!({}),
            labels: std::collections::HashMap::from([("risk".to_owned(), "high".to_owned())]),
            id: None,
            namespace: None,
            forward_id: None,
            enabled: true,
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
            forward_id: None,
            enabled: true,
        });
        registry.register_prompt(PromptEntry {
            name: "admin".to_owned(),
            description: String::new(),
            arguments: Vec::new(),
            messages: Vec::new(),
            id: None,
            namespace: None,
            forward_id: None,
            enabled: true,
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
                agent: None,
                concrete_namespace: None,
                selected_tool: None,
            });
            assert_denied(action, reason);
        }
    }

    #[test]
    fn explicit_allow_and_no_match_continue() {
        let registry = RuntimeRegistry::new();
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
                agent: None,
                concrete_namespace: None,
                selected_tool: None,
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
                agent: None,
                concrete_namespace: None,
                selected_tool: None,
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
        let registry = RuntimeRegistry::new();
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
            operation: None,
            namespace: None,
            a2a_version: None,
            store: &store,
            request_id: Some("request-allow"),
            target: Some("safe"),
            policy_revision: Some("revision-1".to_owned()),
            virtual_namespace: None,
            concrete_namespace: None,
            invocation: None,
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
                agent: None,
                concrete_namespace: None,
                selected_tool: None,
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
            operation: None,
            namespace: None,
            a2a_version: None,
            store: &store,
            request_id: Some("request-deny"),
            target: Some("unsafe"),
            policy_revision: Some("revision-1".to_owned()),
            virtual_namespace: None,
            concrete_namespace: None,
            invocation: None,
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
                agent: None,
                concrete_namespace: None,
                selected_tool: None,
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
        let registry = RuntimeRegistry::new();
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
            forward_id: None,
            enabled: true,
        });
        registry.register_tool(ToolEntry {
            name: "tool-with-uri".to_owned(),
            description: String::new(),
            uri: "file:///tool-location".to_owned(),
            type_: "mcp-forward".to_owned(),
            mcp: Default::default(),
            input_schema: serde_json::json!({}),
            labels: std::collections::HashMap::new(),
            id: None,
            namespace: None,
            forward_id: None,
            enabled: true,
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
                agent: None,
                concrete_namespace: None,
                selected_tool: None,
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
                agent: None,
                concrete_namespace: None,
                selected_tool: None,
            }),
            FilterAction::Continue
        ));
    }

    #[test]
    fn malformed_governed_request_is_safely_rejected() {
        let registry = RuntimeRegistry::new();
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
            agent: None,
            concrete_namespace: None,
            selected_tool: None,
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
        let registry = RuntimeRegistry::new();
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
            operation: None,
            namespace: None,
            a2a_version: None,
            store: &store,
            request_id: Some("audit-mode-request"),
            target: Some("unsafe"),
            policy_revision: None,
            virtual_namespace: None,
            concrete_namespace: None,
            invocation: None,
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
                    agent: None,
                    concrete_namespace: None,
                    selected_tool: None,
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

    #[test]
    fn a2a_agent_rules_apply_without_an_audit_store_or_actor() {
        let registry = RuntimeRegistry::new();
        let policy = compile_policy(&serde_json::json!({
            "id": "deny-agent", "effect": "deny", "reason_code": "agent_denied",
            "selectors": {"target_type": "agent", "target_name": {"matcher": "exact", "value": "worker"}, "operation": "SendMessage"},
            "predicates": [{"operator": "equals", "pointer": "/message/messageId", "value": "blocked"}]
        }));
        let posture = GovernancePosture::default();
        let id = serde_json::json!(7);
        let body = request(
            "message/send",
            &serde_json::json!({"message": {"messageId": "blocked"}}),
        );
        let view = McpRequestView::parse(Some(&body)).expect("valid request");
        let context = action_context("SendMessage", DEFAULT_NAMESPACE, &view, &registry)
            .expect("valid action");
        assert!(context.actor().is_none());
        assert_denied(
            evaluate_request(RequestEvaluation {
                method: "SendMessage",
                namespace: DEFAULT_NAMESPACE,
                body: Some(&body),
                policy: &policy,
                posture: &posture,
                registry: &registry,
                id: &id,
                agent: Some("worker"),
                concrete_namespace: None,
                selected_tool: None,
            }),
            "agent_denied",
        );
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "A2A posture and correlated audit fixtures"
    )]
    fn a2a_decisions_use_posture_and_emit_protocol_specific_audit() {
        let registry = RuntimeRegistry::new();
        let store = InMemoryAuditStore::new(10);
        let policy = compile_policy(&serde_json::json!({
            "id": "deny-task", "effect": "deny", "selectors": {"target_type": "agent", "operation": "GetTask"}
        }));
        let body = request("GetTask", &serde_json::json!({"id": "t1"}));
        let id = serde_json::json!(7);
        let audit = DecisionAuditContext {
            operation: None,
            namespace: None,
            a2a_version: Some("1.0"),
            store: &store,
            request_id: Some("http-request"),
            target: Some("worker"),
            policy_revision: None,
            virtual_namespace: None,
            concrete_namespace: None,
            invocation: None,
        };
        let audit_posture = GovernancePosture {
            mode: EnforcementMode::Audit,
            ..GovernancePosture::default()
        };
        let input = RequestEvaluation {
            method: "GetTask",
            namespace: DEFAULT_NAMESPACE,
            body: Some(&body),
            policy: &policy,
            posture: &audit_posture,
            registry: &registry,
            id: &id,
            agent: Some("worker"),
            concrete_namespace: None,
            selected_tool: None,
        };
        assert!(matches!(
            evaluate_request_impl(Some(&audit), input),
            FilterAction::Continue
        ));
        let events = store.query(&AuditQuery::default()).events;
        assert_eq!(events[0].protocol, "a2a");
        assert_eq!(events[0].attributes["a2a_version"], "1.0");
        assert_eq!(events[0].target_type.as_deref(), Some("agent"));
        assert_eq!(events[0].target.as_deref(), Some("worker"));
        assert_eq!(events[0].decision, AuditDecision::Block);
        assert_eq!(events[0].attributes["task_id"], "t1");
        assert_eq!(events[0].correlation_id, "http-request");
        assert_eq!(events[0].attributes["enforcement_action"], "continue");

        let enforce = GovernancePosture::default();
        assert_denied(
            evaluate_request(RequestEvaluation {
                posture: &enforce,
                ..input
            }),
            DEFAULT_DENY_REASON_CODE,
        );
        assert_denied(
            evaluate_request(RequestEvaluation {
                method: "CancelTask",
                posture: &enforce,
                ..input
            }),
            NO_MATCH_REASON_CODE,
        );
        let allow_unmatched = GovernancePosture {
            no_match: NoMatchBehavior::Allow,
            ..enforce
        };
        assert!(matches!(
            evaluate_request(RequestEvaluation {
                method: "CancelTask",
                posture: &allow_unmatched,
                ..input
            }),
            FilterAction::Continue
        ));
    }

    /// Records events in memory. It has no retention and no persistence worker.
    #[derive(Default)]
    struct RecordingAuditStore {
        events: std::sync::Mutex<Vec<AuditEvent>>,
    }

    impl AuditStore for RecordingAuditStore {
        fn record(&self, event: AuditEvent) {
            self.events.lock().expect("recorded events").push(event);
        }
        fn query(&self, _query: &AuditQuery) -> wanaku_types::audit::AuditPage {
            let events = self.events.lock().expect("recorded events").clone();
            wanaku_types::audit::AuditPage {
                total: events.len(),
                limit: events.len(),
                offset: 0,
                events,
            }
        }
        fn get(&self, event_id: &str) -> Option<AuditEvent> {
            self.events
                .lock()
                .expect("recorded events")
                .iter()
                .find(|event| event.event_id == event_id)
                .cloned()
        }
        fn health(&self) -> wanaku_types::audit::AuditHealth {
            wanaku_types::audit::AuditHealth {
                healthy: true,
                dropped_events: 0,
                retained_events: self.events.lock().expect("recorded events").len(),
                capacity: usize::MAX,
                last_error: None,
            }
        }
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "fixture builds a real request context with a recording audit handle"
    )]
    fn audited_requests_record_through_extension_handle_without_store_runtime() {
        let recorder = std::sync::Arc::new(RecordingAuditStore::default());
        let filters = praxis_filter::FilterRegistry::with_builtins();
        let pipeline = praxis_filter::FilterPipeline::build(&mut [], &filters).expect("pipeline");
        let mut headers = http::HeaderMap::new();
        headers.insert("x-request-id", http::HeaderValue::from_static("request-9"));
        let http_request = praxis_filter::Request {
            method: http::Method::POST,
            uri: http::Uri::from_static("/mcp"),
            headers,
        };
        let mut protocol = praxis_protocol::http::pingora::context::PingoraRequestCtx::default();
        let mut ctx = protocol.build_filter_context(&pipeline, &http_request, None);
        ctx.extensions.insert(AuditHandle::new(recorder.clone()));
        ctx.set_metadata(wanaku_filters::MCP_NAME_KEY, "unsafe");

        let registry = RuntimeRegistry::new();
        let posture = GovernancePosture::default();
        let id = serde_json::json!(7);
        let policy = compile_policy(&serde_json::json!({
            "id": "deny-unsafe", "effect": "deny",
            "selectors": {"operation": TOOLS_CALL, "target_name": {"matcher": "exact", "value": "unsafe"}}
        }));
        let body = request(
            TOOLS_CALL,
            &serde_json::json!({"name": "unsafe", "arguments": {}}),
        );
        let input = RequestEvaluation {
            method: TOOLS_CALL,
            namespace: DEFAULT_NAMESPACE,
            body: Some(&body),
            policy: &policy,
            posture: &posture,
            registry: &registry,
            id: &id,
            agent: None,
            concrete_namespace: None,
            selected_tool: None,
        };
        let audit = DecisionAuditContext::from_http(&ctx, Some(1));
        assert_denied(
            evaluate_request_impl(audit.as_ref(), input),
            DEFAULT_DENY_REASON_CODE,
        );
        let malformed = Bytes::from_static(b"not json");
        let malformed_action = evaluate_request_impl(
            audit.as_ref(),
            RequestEvaluation {
                body: Some(&malformed),
                ..input
            },
        );
        assert!(matches!(malformed_action, FilterAction::Reject(_)));

        let events = recorder.events.lock().expect("recorded events");
        assert_eq!(events.len(), 2);
        let denied = &events[0];
        assert_eq!(denied.category, AuditCategory::Decision);
        assert_eq!(denied.decision, AuditDecision::Block);
        assert_eq!(denied.operation, TOOLS_CALL);
        assert_eq!(denied.reason_code, DEFAULT_DENY_REASON_CODE);
        assert_eq!(denied.namespace.as_deref(), Some(DEFAULT_NAMESPACE));
        assert_eq!(denied.filter.as_deref(), Some("wanaku_action_policy"));
        assert_eq!(denied.target.as_deref(), Some("unsafe"));
        assert_eq!(denied.target_type.as_deref(), Some("tool"));
        assert_eq!(denied.request_id.as_deref(), Some("request-9"));
        assert_eq!(denied.correlation_id, "request-9");
        assert_eq!(
            denied.attributes["deny_rule_ids"],
            serde_json::json!(["deny-unsafe"])
        );
        assert_eq!(denied.policy_revision.as_deref(), Some("1"));
        assert_eq!(events[1].decision, AuditDecision::RejectMalformed);
        assert_eq!(events[1].reason_code, INVALID_ACTION_REASON_CODE);
        assert_eq!(events[1].policy_revision.as_deref(), Some("1"));
    }

    fn activate(state: &ActionPolicyState, rule: &serde_json::Value, expected: Option<u64>) -> u64 {
        let policy: crate::ActionPolicy =
            serde_json::from_value(serde_json::json!({ "rules": [rule] })).expect("valid policy");
        state
            .try_activate(
                policy,
                crate::state::ActivatePolicyParams {
                    origin: crate::revision::RevisionOrigin::Api,
                    actor: None,
                    expected_revision: expected,
                },
            )
            .expect("activate")
            .metadata
            .id
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "fixture builds a real request context and checks each snapshot state"
    )]
    fn captured_snapshot_labels_audit_with_its_own_revision() {
        let state = ActionPolicyState::new();
        let deny_a = activate(
            &state,
            &serde_json::json!({"id": "deny-a", "effect": "deny", "selectors": {"operation": TOOLS_CALL}}),
            None,
        );
        let captured = state.snapshot();
        let allow_b = activate(
            &state,
            &serde_json::json!({"id": "allow-b", "effect": "allow", "selectors": {"operation": TOOLS_CALL}}),
            Some(deny_a),
        );
        assert_eq!(state.revision_store().active_revision_id(), Some(allow_b));

        let recorder = std::sync::Arc::new(RecordingAuditStore::default());
        let filters = praxis_filter::FilterRegistry::with_builtins();
        let pipeline = praxis_filter::FilterPipeline::build(&mut [], &filters).expect("pipeline");
        let http_request = praxis_filter::Request {
            method: http::Method::POST,
            uri: http::Uri::from_static("/mcp"),
            headers: http::HeaderMap::new(),
        };
        let mut protocol = praxis_protocol::http::pingora::context::PingoraRequestCtx::default();
        let mut ctx = protocol.build_filter_context(&pipeline, &http_request, None);
        ctx.extensions.insert(AuditHandle::new(recorder.clone()));
        ctx.extensions.insert(state.clone());
        ctx.extensions.insert(RuntimeRegistry::new());
        ctx.set_metadata(wanaku_filters::MCP_METHOD_KEY, TOOLS_CALL);
        ctx.set_metadata(wanaku_filters::MCP_NAME_KEY, "safe");
        let posture = GovernancePosture::default();
        let id = serde_json::json!(7);
        let body = request(
            TOOLS_CALL,
            &serde_json::json!({"name": "safe", "arguments": {}}),
        );

        assert_denied(
            evaluate_snapshot(&ctx, Some(&body), captured, &posture, &id),
            DEFAULT_DENY_REASON_CODE,
        );
        assert!(matches!(
            evaluate_snapshot(&ctx, Some(&body), state.snapshot(), &posture, &id),
            FilterAction::Continue
        ));
        assert_denied(
            evaluate_snapshot(
                &ctx,
                Some(&body),
                PolicySnapshot::Unconfigured,
                &posture,
                &id,
            ),
            NO_MATCH_REASON_CODE,
        );
        assert_denied(
            evaluate_snapshot(&ctx, Some(&body), PolicySnapshot::Invalid, &posture, &id),
            INVALID_POLICY_REASON_CODE,
        );

        let events = recorder.events.lock().expect("recorded events");
        assert_eq!(events.len(), 4);
        let deny_a = deny_a.to_string();
        let allow_b = allow_b.to_string();
        assert_eq!(events[0].policy_revision.as_deref(), Some(deny_a.as_str()));
        assert_eq!(
            events[0].attributes["deny_rule_ids"],
            serde_json::json!(["deny-a"])
        );
        assert_eq!(events[1].policy_revision.as_deref(), Some(allow_b.as_str()));
        assert_eq!(
            events[1].attributes["matched_rule_ids"],
            serde_json::json!(["allow-b"])
        );
        assert_eq!(events[2].reason_code, NO_MATCH_REASON_CODE);
        assert_eq!(events[2].policy_revision, None);
        assert_eq!(events[3].reason_code, INVALID_POLICY_REASON_CODE);
        assert_eq!(events[3].policy_revision, None);
    }
}
