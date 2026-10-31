use std::collections::HashMap;

use bytes::Bytes;
use http::{HeaderName, HeaderValue};
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::{trace, warn};
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::credentials::CredentialPurpose;
use wanaku_types::credentials::binding::UseScope;
use wanaku_types::registry::{ForwardRegistry, PromptRegistry};

crate::body_filter_boilerplate!(PromptGetFilter, "wanaku_prompt_get");

struct ParsedBody {
    id: serde_json::Value,
    name: Option<String>,
    arguments: serde_json::Map<String, serde_json::Value>,
}

fn parse_body(body: &Option<Bytes>, json_rpc_id: serde_json::Value) -> ParsedBody {
    let params = crate::json_rpc::JsonRpcParams::parse(body);
    ParsedBody {
        id: json_rpc_id,
        name: params.name,
        arguments: params.arguments,
    }
}

impl PromptGetFilter {
    #[expect(
        clippy::too_many_lines,
        reason = "MCP protocol handler with JSON-RPC response construction"
    )]
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let Some(method) = ctx.get_metadata(crate::MCP_METHOD_KEY) else {
            return Ok(FilterAction::Continue);
        };

        if method != crate::PROMPTS_GET {
            return Ok(FilterAction::Continue);
        }

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_types::registry::DEFAULT_NAMESPACE);

        let json_rpc_id =
            crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));
        let parsed = parse_body(body, json_rpc_id);

        let Some(prompt_name) = parsed.name.as_deref() else {
            return Ok(crate::response::json_rpc_error(
                &parsed.id,
                crate::response::JSONRPC_INVALID_PARAMS,
                "missing name in prompts/get",
            ));
        };

        trace!(prompt = %prompt_name, namespace = %namespace, "handling MCP prompts/get request");

        let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() else {
            tracing::error!("InMemoryRegistry not found in request extensions");
            return Ok(crate::response::json_rpc_error(
                &parsed.id,
                crate::response::JSONRPC_INTERNAL_ERROR,
                "internal error: registry unavailable",
            ));
        };

        let Some(prompt) = registry.get_prompt_in_namespace(namespace, prompt_name) else {
            warn!(prompt = %prompt_name, "prompt not found in registry");
            return Ok(crate::response::json_rpc_error(
                &parsed.id,
                crate::response::JSONRPC_INVALID_PARAMS,
                &format!("prompt not found: {prompt_name}"),
            ));
        };

        if prompt.messages.is_empty()
            && let Some(forward_id) = prompt.forward_id.as_deref()
        {
            // Resolve the current upstream address from the immutable forwardId
            // provenance. The address can change while the forwardId stays stable.
            let Some(forward) = registry.get_forward(forward_id) else {
                warn!(prompt = %prompt_name, forward_id = %forward_id, "forward not found for prompt provenance");
                return Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("forward not found for prompt: {prompt_name}"),
                ));
            };
            // A forward whose last discovery failed keeps its previous entries,
            // but it is unavailable: refuse the call until discovery succeeds.
            if !forward.available {
                warn!(prompt = %prompt_name, forward_id = %forward_id, "forward unavailable for prompt provenance");
                return Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("forward unavailable for prompt: {prompt_name}"),
                ));
            }
            let address = forward.address.clone();
            let invoker = match crate::invoker::invoker_from_extensions(ctx, &parsed.id) {
                Ok(invoker) => invoker,
                Err(action) => return Ok(action),
            };

            let mut forward_headers: HashMap<HeaderName, HeaderValue> = HashMap::new();
            let request = crate::credentials::ForwardCredentialRequest {
                forward: &forward,
                address: &address,
                purpose: CredentialPurpose::Invocation,
                scope: UseScope {
                    namespace: Some(namespace),
                    governed_item: Some(prompt_name),
                    operation: Some(crate::PROMPTS_GET),
                    identity: None,
                },
                json_rpc_id: &parsed.id,
            };
            let redactor = match crate::credentials::inject_forward_credentials(
                ctx,
                &request,
                &mut forward_headers,
            )
            .await
            {
                Ok(redactor) => redactor,
                Err(action) => return Ok(action),
            };
            let exchange = crate::credentials::ForwardExchange {
                invoker,
                headers: forward_headers,
                redactor,
            };

            return self
                .handle_forwarded_get(&address, prompt_name, &parsed, exchange)
                .await;
        }

        let messages: Vec<serde_json::Value> = prompt
            .messages
            .iter()
            .map(|m| {
                let mut text = match &m.content {
                    serde_json::Value::String(s) => s.clone(),
                    other => other.to_string(),
                };

                for (key, value) in &parsed.arguments {
                    let placeholder = format!("{{{key}}}");
                    let replacement = match value {
                        serde_json::Value::String(s) => s.clone(),
                        other => other.to_string(),
                    };
                    text = text.replace(&placeholder, &replacement);
                }

                serde_json::json!({
                    "role": m.role,
                    "content": {
                        "type": "text",
                        "text": text,
                    }
                })
            })
            .collect();

        let response = serde_json::json!({
            "jsonrpc": "2.0",
            "id": parsed.id,
            "result": {
                "description": prompt.description,
                "messages": messages,
            }
        });

        let response_body = Bytes::from(response.to_string());
        Ok(FilterAction::Reject(crate::response::json_response(
            response_body,
        )))
    }

    #[expect(
        clippy::too_many_lines,
        reason = "MCP forwarding handler with error paths"
    )]
    async fn handle_forwarded_get(
        &self,
        forward_address: &str,
        prompt_name: &str,
        parsed: &ParsedBody,
        exchange: crate::credentials::ForwardExchange,
    ) -> Result<FilterAction, FilterError> {
        let crate::credentials::ForwardExchange {
            invoker,
            headers: forward_headers,
            redactor,
        } = exchange;
        trace!(prompt = %prompt_name, forward = %forward_address, "forwarding prompts/get to remote MCP server");

        let arguments = if parsed.arguments.is_empty() {
            None
        } else {
            Some(parsed.arguments.clone())
        };

        match invoker
            .get_prompt(forward_address, prompt_name, arguments, forward_headers)
            .await
        {
            Ok(result) => {
                let response = build_get_response(&parsed.id, &result, &redactor);
                let response_body = Bytes::from(response.to_string());
                Ok(FilterAction::Reject(crate::response::json_response(
                    response_body,
                )))
            }
            Err(e) => {
                // Redact any injected secret the upstream may have echoed into its
                // error text before it reaches the logs or the agent.
                let message = redactor.redact(&e.to_string());
                warn!(prompt = %prompt_name, error = %message, "MCP forward prompt get failed");
                Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("forwarded prompt get failed: {message}"),
                ))
            }
        }
    }
}

/// Build the JSON-RPC response for a forwarded prompt get, redacting any injected
/// credential the upstream echoed back into the prompt result.
fn build_get_response(
    id: &serde_json::Value,
    result: &serde_json::Value,
    redactor: &crate::credentials::CredentialRedactor,
) -> serde_json::Value {
    serde_json::json!({
        "jsonrpc": "2.0",
        "id": id,
        "result": redactor.redact_json(result),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_body_valid_with_name_and_arguments() {
        let body = Some(Bytes::from(
            r#"{"jsonrpc":"2.0","id":1,"method":"prompts/get","params":{"name":"summarize","arguments":{"topic":"rust"}}}"#,
        ));
        let parsed = parse_body(&body, serde_json::Value::from(1));
        assert_eq!(parsed.id, serde_json::Value::from(1));
        assert_eq!(parsed.name.as_deref(), Some("summarize"));
        assert_eq!(parsed.arguments.len(), 1);
        assert_eq!(
            parsed.arguments.get("topic"),
            Some(&serde_json::Value::from("rust"))
        );
    }

    #[test]
    fn build_get_response_redacts_injected_credential() {
        // The upstream echoes the secret inside the prompt messages.
        let result = serde_json::json!({
            "description": "echoed Authorization: Bearer s3cr3t",
            "messages": [
                {"role": "user", "content": {"type": "text", "text": "token s3cr3t"}}
            ]
        });
        let redactor = crate::credentials::CredentialRedactor::from_patterns(vec![
            "s3cr3t".to_owned(),
            "Bearer s3cr3t".to_owned(),
        ]);

        let response = build_get_response(&serde_json::json!(1), &result, &redactor);

        assert_eq!(
            response.pointer("/result/description").unwrap(),
            "echoed Authorization: <redacted>"
        );
        assert_eq!(
            response.pointer("/result/messages/0/content/text").unwrap(),
            "token <redacted>"
        );
    }

    #[test]
    fn build_get_response_is_noop_without_binding() {
        let result = serde_json::json!({"description": "plain s3cr3t"});
        let redactor = crate::credentials::CredentialRedactor::default();

        let response = build_get_response(&serde_json::json!(1), &result, &redactor);

        assert_eq!(
            response.pointer("/result/description").unwrap(),
            "plain s3cr3t"
        );
    }

    #[test]
    fn parse_body_name_only_no_arguments() {
        let body = Some(Bytes::from(r#"{"id":2,"params":{"name":"greet"}}"#));
        let parsed = parse_body(&body, serde_json::Value::from(2));
        assert_eq!(parsed.id, serde_json::Value::from(2));
        assert_eq!(parsed.name.as_deref(), Some("greet"));
        assert!(parsed.arguments.is_empty());
    }

    #[test]
    fn parse_body_none() {
        let parsed = parse_body(&None, serde_json::Value::Null);
        assert!(parsed.id.is_null());
        assert!(parsed.name.is_none());
        assert!(parsed.arguments.is_empty());
    }

    #[test]
    fn parse_body_id_comes_from_parameter_not_body() {
        let body = Some(Bytes::from(
            r#"{"jsonrpc":"2.0","id":999,"params":{"name":"greet"}}"#,
        ));
        let parsed = parse_body(&body, serde_json::Value::from(1));
        assert_eq!(parsed.id, serde_json::Value::from(1));
    }
}

/// Whole-filter tests with the recording invocation adapter. No test opens a
/// network connection.
#[cfg(test)]
mod filter_tests {
    use std::sync::Arc;

    use praxis_filter::FilterAction;
    use serde_json::{Value, json};
    use wanaku_infra::registry::InMemoryRegistry;
    use wanaku_types::registry::{PromptEntry, PromptRegistry};

    use super::PromptGetFilter;
    use crate::invoker::testing::{
        ADDRESS, FORWARD_ID, RecordingInvoker, SECRET, filter_context, registry, request,
        response_json,
    };

    const BODY: &str = r#"{"jsonrpc":"2.0","id":7,"method":"prompts/get","params":{"name":"greet","arguments":{"who":"ada"}}}"#;

    fn prompt(forward_id: Option<&str>, enabled: bool) -> PromptEntry {
        serde_json::from_value(json!({
            "name": "greet",
            "forwardId": forward_id,
            "enabled": enabled
        }))
        .unwrap()
    }

    async fn run(registry: &InMemoryRegistry, invoker: &Arc<RecordingInvoker>) -> FilterAction {
        let req = request(&[]);
        let mut ctx = filter_context(&req, crate::PROMPTS_GET, registry, invoker);
        let filter = PromptGetFilter {
            max_body_bytes: 1024,
        };
        let mut body = Some(bytes::Bytes::from_static(BODY.as_bytes()));
        filter.handle_body(&mut ctx, &mut body).await.unwrap()
    }

    #[tokio::test]
    async fn success_invokes_once_with_resolved_get_and_credentials() {
        let registry = registry(Some("b1"), Some("fake:token"));
        registry.register_prompt(prompt(Some(FORWARD_ID), true));
        let invoker = RecordingInvoker::succeeding(json!({"messages": [
            {"role": "user", "content": {"type": "text", "text": format!("hi {SECRET}")}}
        ]}));

        let response = response_json(run(&registry, &invoker).await);

        let calls = invoker.calls();
        assert_eq!(calls.len(), 1);
        assert_eq!(calls[0].operation, crate::PROMPTS_GET);
        assert_eq!(calls[0].address, ADDRESS);
        assert_eq!(calls[0].target, "greet");
        assert_eq!(calls[0].arguments, Some(json!({"who": "ada"})));
        assert_eq!(calls[0].headers.len(), 1);
        assert_eq!(
            calls[0].headers[&http::header::AUTHORIZATION],
            format!("Bearer {SECRET}")
        );
        assert_eq!(response["id"], 7);
        assert_eq!(
            response["result"]["messages"][0]["content"]["text"],
            "hi <redacted>"
        );
    }

    #[tokio::test]
    async fn upstream_error_is_redacted_with_the_request_id() {
        let registry = registry(Some("b1"), Some("fake:token"));
        registry.register_prompt(prompt(Some(FORWARD_ID), true));
        let invoker = RecordingInvoker::failing("rejected s3cr3t");

        let response = response_json(run(&registry, &invoker).await);

        assert_eq!(invoker.calls().len(), 1);
        assert_eq!(response["id"], 7);
        let message = response["error"]["message"].as_str().unwrap();
        assert!(message.starts_with("forwarded prompt get failed"));
        assert!(!message.contains(SECRET), "leaked: {message}");
    }

    async fn assert_not_invoked(registry: &InMemoryRegistry, expected: &str) {
        let invoker = RecordingInvoker::succeeding(Value::Null);
        let response = response_json(run(registry, &invoker).await);
        assert!(
            invoker.calls().is_empty(),
            "adapter invoked for: {expected}"
        );
        assert_eq!(response["id"], 7);
        let message = response["error"]["message"].as_str().unwrap();
        assert!(message.contains(expected), "unexpected error: {message}");
    }

    #[tokio::test]
    async fn missing_provenance_does_not_invoke() {
        // A prompt without messages and without provenance renders locally.
        let registry = registry(None, None);
        registry.register_prompt(prompt(None, true));
        let invoker = RecordingInvoker::succeeding(Value::Null);

        let response = response_json(run(&registry, &invoker).await);

        assert!(invoker.calls().is_empty());
        assert_eq!(response["id"], 7);
        assert_eq!(response["result"]["messages"], json!([]));
    }

    #[tokio::test]
    async fn missing_forward_does_not_invoke() {
        let registry = registry(None, None);
        registry.register_prompt(prompt(Some("fwd-missing"), true));
        assert_not_invoked(&registry, "forward not found").await;
    }

    #[tokio::test]
    async fn unavailable_forward_does_not_invoke() {
        let registry = registry(None, None);
        registry.register_prompt(prompt(Some(FORWARD_ID), true));
        use wanaku_types::registry::ForwardRegistry as _;
        let mut forward = registry.get_forward(FORWARD_ID).unwrap();
        forward.available = false;
        registry.register_forward(forward);
        assert_not_invoked(&registry, "forward unavailable").await;
    }

    #[tokio::test]
    async fn disabled_or_unknown_prompt_does_not_invoke() {
        let registry = registry(None, None);
        assert_not_invoked(&registry, "prompt not found").await;
        registry.register_prompt(prompt(Some(FORWARD_ID), false));
        assert_not_invoked(&registry, "prompt not found").await;
    }

    #[tokio::test]
    async fn failed_credential_resolution_does_not_invoke() {
        let unresolved = registry(Some("b1"), Some("fake:absent"));
        unresolved.register_prompt(prompt(Some(FORWARD_ID), true));
        assert_not_invoked(&unresolved, "credential brokerage failed").await;
    }
}
