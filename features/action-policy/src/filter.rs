use std::collections::BTreeMap;

use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use wanaku_filters::json_rpc::{McpRequestView, RequestViewError};
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::registry::{DEFAULT_NAMESPACE, ToolRegistry};

use crate::{
    ActionContext, ActionPolicyState, PolicyDecision, PolicyEngine, PolicySnapshot, PolicyState,
    TargetType,
};

const POLICY_DENIED_JSON_RPC_CODE: i32 = -32003;
const INVALID_ACTION_REASON_CODE: &str = "invalid_action_request";
const INVALID_ACTION_MESSAGE: &str = "The action request is invalid.";
const INVALID_POLICY_REASON_CODE: &str = "action_policy_invalid";
const INVALID_POLICY_MESSAGE: &str = "The action policy is unavailable.";

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
        let Some(state) = ctx.extensions.get::<ActionPolicyState>() else {
            return Ok(policy_error(
                &id,
                INVALID_POLICY_REASON_CODE,
                INVALID_POLICY_MESSAGE,
            ));
        };
        let snapshot = state.snapshot();
        Ok(evaluate_snapshot(ctx, body.as_ref(), snapshot, &id))
    }
}

fn evaluate_snapshot(
    ctx: &HttpFilterContext<'_>,
    body: Option<&Bytes>,
    snapshot: PolicySnapshot,
    id: &serde_json::Value,
) -> FilterAction {
    let policy = match snapshot {
        PolicySnapshot::Valid(policy) => policy,
        PolicySnapshot::Unconfigured => return FilterAction::Continue,
        PolicySnapshot::Invalid => {
            return policy_error(id, INVALID_POLICY_REASON_CODE, INVALID_POLICY_MESSAGE);
        }
    };
    let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() else {
        return policy_error(id, INVALID_POLICY_REASON_CODE, INVALID_POLICY_MESSAGE);
    };
    evaluate_request(RequestEvaluation {
        method: ctx
            .get_metadata(wanaku_filters::MCP_METHOD_KEY)
            .unwrap_or_default(),
        namespace: ctx
            .get_metadata(wanaku_types::NAMESPACE_METADATA_KEY)
            .unwrap_or(DEFAULT_NAMESPACE),
        body,
        policy: &policy,
        registry,
        id,
    })
}

fn is_governed_method(method: &str) -> bool {
    matches!(method, "tools/call" | "resources/read" | "prompts/get")
}

#[derive(Clone, Copy)]
struct RequestEvaluation<'a> {
    method: &'a str,
    namespace: &'a str,
    body: Option<&'a Bytes>,
    policy: &'a crate::CompiledPolicy,
    registry: &'a InMemoryRegistry,
    id: &'a serde_json::Value,
}

fn evaluate_request(input: RequestEvaluation<'_>) -> FilterAction {
    let RequestEvaluation {
        method,
        namespace,
        body,
        policy,
        registry,
        id,
    } = input;
    let Ok(view) = McpRequestView::parse(body) else {
        return policy_error(id, INVALID_ACTION_REASON_CODE, INVALID_ACTION_MESSAGE);
    };
    let Ok(context) = action_context(method, namespace, &view, registry) else {
        return policy_error(id, INVALID_ACTION_REASON_CODE, INVALID_ACTION_MESSAGE);
    };
    let decision = PolicyEngine::evaluate(PolicyState::Available(policy), &context);
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
        PolicyDecision::ExplicitAllow { .. } | PolicyDecision::NoMatch => FilterAction::Continue,
        PolicyDecision::PolicyUnavailable | PolicyDecision::PolicyInvalid => {
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
        "tools/call" => tool_context(namespace, view, registry, input),
        "resources/read" => resource_context(namespace, view, registry, input),
        "prompts/get" => prompt_context(namespace, view, input),
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
    let context = ActionContext::new(namespace, "tools/call", TargetType::Tool, input)
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
    let context = ActionContext::new(namespace, "resources/read", TargetType::Resource, input)
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
        ActionContext::new(namespace, "prompts/get", TargetType::Prompt, input)
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
    use wanaku_types::registry::{
        PromptEntry, PromptRegistry, ResourceEntry, ResourceRegistry, ToolEntry,
    };

    fn compile_policy(rule: &serde_json::Value) -> crate::CompiledPolicy {
        let policy: crate::ActionPolicy = serde_json::from_value(serde_json::json!({
            "rules": [rule.clone()]
        }))
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
                "tools/call",
                serde_json::json!({"name": "delete", "arguments": {}}),
                serde_json::json!({
                    "id": "deny-tool", "effect": "deny", "reason_code": "tool_denied",
                    "selectors": {"operation": "tools/call", "target_name": {"matcher": "exact", "value": "delete"}, "labels": {"risk": "high"}}
                }),
                "tool_denied",
            ),
            (
                "resources/read",
                serde_json::json!({"uri": "file:///secrets"}),
                serde_json::json!({
                    "id": "deny-resource", "effect": "deny", "reason_code": "resource_denied",
                    "selectors": {"operation": "resources/read", "uri": {"matcher": "exact", "value": "file:///secrets"}}
                }),
                "resource_denied",
            ),
            (
                "prompts/get",
                serde_json::json!({"name": "admin"}),
                serde_json::json!({
                    "id": "deny-prompt", "effect": "deny", "reason_code": "prompt_denied",
                    "selectors": {"operation": "prompts/get", "target_name": {"matcher": "exact", "value": "admin"}}
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
                registry: &registry,
                id: &id,
            });
            assert_denied(action, reason);
        }
    }

    #[test]
    fn explicit_allow_and_no_match_continue() {
        let registry = InMemoryRegistry::new();
        let policy = compile_policy(&serde_json::json!({
            "id": "allow", "effect": "allow",
            "selectors": {"operation": "tools/call"}
        }));
        let body = request("tools/call", &serde_json::json!({"name": "safe"}));
        let id = serde_json::json!(7);
        assert!(matches!(
            evaluate_request(RequestEvaluation {
                method: "tools/call",
                namespace: DEFAULT_NAMESPACE,
                body: Some(&body),
                policy: &policy,
                registry: &registry,
                id: &id,
            }),
            FilterAction::Continue
        ));

        let body = request("prompts/get", &serde_json::json!({"name": "safe"}));
        assert!(matches!(
            evaluate_request(RequestEvaluation {
                method: "prompts/get",
                namespace: DEFAULT_NAMESPACE,
                body: Some(&body),
                policy: &policy,
                registry: &registry,
                id: &id,
            }),
            FilterAction::Continue
        ));
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "resource and tool registry fixtures verify URI isolation"
    )]
    fn resource_name_comes_from_registry_and_tool_uri_is_not_exposed() {
        let registry = InMemoryRegistry::new();
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
                "operation": "resources/read",
                "target_name": {"matcher": "exact", "value": "registered-name"}
            }
        }));
        let resource_body = request(
            "resources/read",
            &serde_json::json!({"uri": "file:///requested-resource"}),
        );
        assert_denied(
            evaluate_request(RequestEvaluation {
                method: "resources/read",
                namespace: DEFAULT_NAMESPACE,
                body: Some(&resource_body),
                policy: &resource_policy,
                registry: &registry,
                id: &id,
            }),
            DEFAULT_DENY_REASON_CODE,
        );

        let tool_uri_policy = compile_policy(&serde_json::json!({
            "id": "deny-tool-uri", "effect": "deny",
            "selectors": {
                "operation": "tools/call",
                "uri": {"matcher": "exact", "value": "file:///tool-location"}
            }
        }));
        let tool_body = request(
            "tools/call",
            &serde_json::json!({"name": "tool-with-uri", "arguments": {}}),
        );
        assert!(matches!(
            evaluate_request(RequestEvaluation {
                method: "tools/call",
                namespace: DEFAULT_NAMESPACE,
                body: Some(&tool_body),
                policy: &tool_uri_policy,
                registry: &registry,
                id: &id,
            }),
            FilterAction::Continue
        ));
    }

    #[test]
    fn malformed_governed_request_is_safely_rejected() {
        let registry = InMemoryRegistry::new();
        let policy = compile_policy(&serde_json::json!({
            "id": "deny", "effect": "deny", "selectors": {"operation": "tools/call"}
        }));
        let malformed = Bytes::from("not JSON");
        let id = serde_json::json!(7);
        let action = evaluate_request(RequestEvaluation {
            method: "tools/call",
            namespace: DEFAULT_NAMESPACE,
            body: Some(&malformed),
            policy: &policy,
            registry: &registry,
            id: &id,
        });
        assert_denied(action, INVALID_ACTION_REASON_CODE);
    }

    #[test]
    fn list_and_unrelated_methods_are_not_governed() {
        for method in ["tools/list", "resources/list", "prompts/list", "initialize"] {
            assert!(!is_governed_method(method));
        }
    }
}
