use std::collections::HashMap;

use bytes::Bytes;
use http::{HeaderName, HeaderValue};
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::{trace, warn};
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::config::ENV;
use wanaku_types::credentials::CredentialPurpose;
use wanaku_types::credentials::binding::UseScope;
use wanaku_types::registry::{ForwardRegistry, ToolEntry, ToolRegistry};

crate::body_filter_boilerplate!(ToolCallFilter, "wanaku_tool_call");

struct ParsedBody {
    id: serde_json::Value,
    arguments: HashMap<String, serde_json::Value>,
}

fn parse_body(body: &Option<Bytes>, json_rpc_id: serde_json::Value) -> ParsedBody {
    let arguments = crate::json_rpc::JsonRpcParams::parse(body)
        .arguments
        .into_iter()
        .collect();
    ParsedBody {
        id: json_rpc_id,
        arguments,
    }
}

impl ToolCallFilter {
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

        if method != crate::TOOLS_CALL {
            return Ok(FilterAction::Continue);
        }

        let json_rpc_id =
            crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));
        let mut parsed = parse_body(body, json_rpc_id);

        let tool_name = match ctx.get_metadata(crate::MCP_NAME_KEY) {
            Some(n) => n.to_owned(),
            None => {
                return Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INVALID_PARAMS,
                    "missing tool name in tools/call",
                ));
            }
        };

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_types::registry::DEFAULT_NAMESPACE);

        let conversation_id = parsed
            .arguments
            .remove(wanaku_types::correlation::REQUEST_ID_ARG)
            .map_or_else(
                || "-".to_owned(),
                |v| match v {
                    serde_json::Value::String(s) => s,
                    other => other.to_string(),
                },
            );

        let request_id = ctx
            .request
            .headers
            .get("x-request-id")
            .and_then(|v| v.to_str().ok())
            .unwrap_or("-");

        // Never trace header values: forwarded requests can carry credentials
        // (e.g. Authorization) and broker-managed credential headers. Only the
        // header names are safe to log. See issue #1874 (redaction boundary).
        for name in ctx.request.headers.keys() {
            tracing::trace!(header = %name, "tools/call request header present");
        }

        tracing::info!(
            tool = %tool_name,
            namespace = %namespace,
            conversation_id = %conversation_id,
            x_request_id = %request_id,
            "tools/call"
        );

        // #1964: never log argument values — a tools/call argument can carry an
        // injected credential or PII. Log only the argument key names so the
        // request shape stays debuggable without exposing secrets.
        tracing::debug!(
            tool = %tool_name,
            argument_keys = ?parsed.arguments.keys().collect::<Vec<_>>(),
            "parsed tools/call request body (x-request-id stripped, argument values not logged)"
        );

        let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() else {
            tracing::error!("InMemoryRegistry not found in request extensions");
            return Ok(crate::response::json_rpc_error(
                &parsed.id,
                crate::response::JSONRPC_INTERNAL_ERROR,
                "internal error: registry unavailable",
            ));
        };

        let tool = match registry.get_tool_in_namespace(namespace, &tool_name) {
            Some(t) => {
                tracing::debug!(
                    tool = %t.name,
                    uri = %t.uri,
                    type_ = %t.type_,
                    "resolved tool from registry"
                );
                t
            }
            None => {
                warn!(tool = %tool_name, "tool not found in registry");
                return Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INVALID_PARAMS,
                    &format!("tool not found: {tool_name}"),
                ));
            }
        };

        if tool.is_mcp_forward() {
            // Resolve the current upstream address from the immutable forwardId
            // provenance. Address is no longer identity: it can change while the
            // forwardId stays stable. Fail closed when a forwarded tool has no
            // provenance: without it the owning forward is unknown, so credential
            // enforcement cannot run. This matches the resources and prompts paths.
            let Some(forward_id) = tool.forward_id.as_deref() else {
                warn!(tool = %tool_name, "forwarded tool missing forwardId provenance");
                return Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    "forwarded tool has no forwardId provenance",
                ));
            };
            let Some(forward) = registry.get_forward(forward_id) else {
                warn!(tool = %tool_name, forward_id = %forward_id, "forward not found for tool provenance");
                return Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("forward not found for tool: {tool_name}"),
                ));
            };
            let address = forward.address.clone();

            let mut forward_headers = collect_forward_headers(&ctx.request.headers, &tool);
            if tool.inject_header_args() {
                inject_header_arguments(
                    &mut parsed.arguments,
                    &tool.input_schema,
                    &forward_headers,
                );
            }

            // Inject brokered credentials only after governance has allowed the
            // call and after client-header argument mapping, so managed
            // credentials never leak into tool arguments.
            let request = crate::credentials::ForwardCredentialRequest {
                forward: &forward,
                address: &address,
                purpose: CredentialPurpose::Invocation,
                scope: UseScope {
                    namespace: Some(namespace),
                    governed_item: Some(&tool_name),
                    operation: Some(crate::TOOLS_CALL),
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
                headers: forward_headers,
                redactor,
            };

            return self
                .handle_forwarded_call(&address, &tool_name, &parsed, exchange, &conversation_id)
                .await;
        }

        warn!(tool = %tool_name, tool_type = %tool.type_, "unsupported tool type — only MCP-forwarded tools are supported");
        Ok(crate::response::json_rpc_error(
            &parsed.id,
            crate::response::JSONRPC_INTERNAL_ERROR,
            &format!(
                "unsupported tool type '{}': only MCP-forwarded tools are supported",
                tool.type_
            ),
        ))
    }

    #[expect(
        clippy::too_many_lines,
        reason = "MCP forwarding handler with error paths"
    )]
    async fn handle_forwarded_call(
        &self,
        address: &str,
        tool_name: &str,
        parsed: &ParsedBody,
        exchange: crate::credentials::ForwardExchange,
        tracking_id: &str,
    ) -> Result<FilterAction, FilterError> {
        let crate::credentials::ForwardExchange {
            headers: forward_headers,
            redactor,
        } = exchange;
        trace!(
            tool = %tool_name,
            uri = %address,
            forwarded_header_count = forward_headers.len(),
            "forwarding tools/call to remote MCP server"
        );

        let arguments = serde_json::Value::Object(
            parsed
                .arguments
                .iter()
                .map(|(k, v)| (k.clone(), v.clone()))
                .collect(),
        );

        tracing::debug!("Invoking tool {tool_name} with a tracking id of {tracking_id}");

        // #1964: capture the exact forwarded header values before they move into
        // the MCP client. These are allow-listed request headers (for example,
        // injected auth credentials). If the upstream echoes one in an error, the
        // error path redacts it by exact match, even when it has no known shape.
        let forwarded_values = forwarded_header_values(&forward_headers);

        match wanaku_infra::mcp_client::call_tool(address, tool_name, arguments, forward_headers)
            .await
        {
            Ok(call_result) => {
                let response =
                    build_success_response(&parsed.id, &call_result, &redactor, &forwarded_values);
                let response_body = Bytes::from(response.to_string());
                Ok(FilterAction::Reject(crate::response::json_response(
                    response_body,
                )))
            }
            Err(e) => {
                // #1964: redact forwarded header values, their token segments, and
                // audit-shaped text from the upstream error.
                let detail = redacted_forward_error(&e, &forwarded_values);
                // #1874: also strip any brokered secret the upstream echoed back,
                // matched by the exact injected material as a backstop.
                let detail = redactor.redact(&detail);
                warn!(tool = %tool_name, error = %detail, "MCP forward call failed");
                Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &detail,
                ))
            }
        }
    }
}

/// Collect the readable forwarded header values so the error path can redact them.
///
/// #1964: a non-UTF-8 or empty value cannot appear as readable text in an error
/// string, so this skips those values.
fn forwarded_header_values(forward_headers: &HashMap<HeaderName, HeaderValue>) -> Vec<String> {
    forward_headers
        .values()
        .filter_map(|value| value.to_str().ok())
        .filter(|value| !value.is_empty())
        .map(str::to_owned)
        .collect()
}

/// Build the exact strings to remove from a failed forwarded-call error.
///
/// #1964: an upstream error can echo a forwarded credential verbatim, or it can
/// echo only the token part after the auth scheme (for example, `Bearer ` is
/// stripped). This returns each full value and each whitespace-separated segment,
/// sorted from longest to shortest. Every segment comes from a value that Wanaku
/// forwarded, so it is safe to redact all of them, including a short segment. The
/// only over-redaction is an auth-scheme keyword such as `Bearer`, which is
/// acceptable diagnostic noise. The longest-first order stops a short value from
/// mangling a longer value that contains it.
fn redaction_needles(forwarded_values: &[String]) -> Vec<String> {
    let mut needles: Vec<String> = Vec::new();
    for value in forwarded_values {
        needles.push(value.clone());
        for segment in value.split_whitespace() {
            needles.push(segment.to_owned());
        }
    }
    needles.sort_by(|a, b| b.len().cmp(&a.len()).then_with(|| a.cmp(b)));
    needles.dedup();
    needles
}

/// Build the client-facing and log-facing detail for a failed forwarded call.
///
/// #1964: the upstream error string can echo injected credentials (forwarded
/// auth headers, `x-mcp-header` arguments). First remove the forwarded header
/// values and their token segments, so an opaque credential is redacted
/// regardless of shape. Then apply the shared redactor as a backstop for any
/// other credential-shaped text. A credential-shaped detail collapses to
/// `[REDACTED]` in full; an ordinary error passes through unchanged.
fn redacted_forward_error(error: &impl std::fmt::Display, forwarded_values: &[String]) -> String {
    redact_forwarded_text(
        &format!("forwarded tool call failed: {error}"),
        forwarded_values,
    )
}

/// Apply the #1964 forwarded-value and shape-based redaction to arbitrary text.
///
/// #1964: remove each forwarded header value and its token segments, then apply
/// the shared `AuditRedactor` to collapse any remaining credential-shaped text.
/// Shared by the transport-error path and the tool-error content path so both
/// sanitize an echoed credential the same way.
fn redact_forwarded_text(text: &str, forwarded_values: &[String]) -> String {
    let mut detail = text.to_owned();
    for needle in redaction_needles(forwarded_values) {
        if detail.contains(needle.as_str()) {
            detail = detail.replace(needle.as_str(), "[REDACTED]");
        }
    }
    wanaku_types::audit_redaction::AuditRedactor::default().redact_string(&mut detail);
    detail
}

/// Build the JSON-RPC success payload for a forwarded tool call, redacting any
/// injected credential the upstream echoed back into its content.
///
/// #1874: always strip brokered secrets from the content. #1964: when the
/// upstream reports a tool-level failure (`isError`), also strip forwarded header
/// values and credential-shaped text, so a tool error that echoes a credential is
/// sanitized the same way a transport error is.
fn build_success_response(
    id: &serde_json::Value,
    call_result: &wanaku_infra::mcp_client::CallToolResponse,
    redactor: &crate::credentials::CredentialRedactor,
    forwarded_values: &[String],
) -> serde_json::Value {
    let mcp_content: Vec<serde_json::Value> = call_result
        .content
        .iter()
        .map(|text| {
            let redacted = redactor.redact(text);
            let redacted = if call_result.is_error {
                redact_forwarded_text(&redacted, forwarded_values)
            } else {
                redacted
            };
            serde_json::json!({"type": "text", "text": redacted})
        })
        .collect();

    serde_json::json!({
        "jsonrpc": "2.0",
        "id": id,
        "result": {"content": mcp_content, "isError": call_result.is_error}
    })
}

fn inject_header_arguments(
    arguments: &mut HashMap<String, serde_json::Value>,
    input_schema: &serde_json::Value,
    forward_headers: &HashMap<HeaderName, HeaderValue>,
) {
    if forward_headers.is_empty() {
        return;
    }

    let Some(properties) = input_schema.get("properties").and_then(|p| p.as_object()) else {
        return;
    };

    for (prop_name, prop_schema) in properties {
        let Some(header_name) = prop_schema.get("x-mcp-header").and_then(|h| h.as_str()) else {
            continue;
        };

        if arguments.contains_key(prop_name) {
            continue;
        }

        let header_key = HeaderName::from_bytes(header_name.as_bytes()).ok();
        let value = header_key.and_then(|k| forward_headers.get(&k));

        if let Some(val) = value.and_then(|v| v.to_str().ok()) {
            trace!(
                property = %prop_name,
                header = %header_name,
                "injecting forwarded header as tool argument (x-mcp-header)"
            );
            arguments.insert(prop_name.clone(), serde_json::Value::String(val.to_owned()));
        }
    }
}

fn collect_forward_headers(
    request_headers: &http::HeaderMap,
    tool: &ToolEntry,
) -> HashMap<HeaderName, HeaderValue> {
    let global = &ENV.forward_headers;
    let per_tool = tool.forward_headers();
    extract_allowed_headers(request_headers, global, &per_tool)
}

const DENIED_HEADERS: &[&str] = &[
    "accept",
    "mcp-session-id",
    "last-event-id",
    "host",
    "content-type",
    "content-length",
    "transfer-encoding",
    "connection",
];

fn extract_allowed_headers(
    request_headers: &http::HeaderMap,
    global_allowlist: &[String],
    tool_allowlist: &[String],
) -> HashMap<HeaderName, HeaderValue> {
    if global_allowlist.is_empty() && tool_allowlist.is_empty() {
        return HashMap::new();
    }

    let mut result = HashMap::new();

    for (name, value) in request_headers {
        let name_lower = name.as_str().to_lowercase();

        if DENIED_HEADERS.contains(&name_lower.as_str()) {
            if global_allowlist.contains(&name_lower) || tool_allowlist.contains(&name_lower) {
                warn!(header = %name, "header is in the denylist and cannot be forwarded");
            }
            continue;
        }

        if global_allowlist.contains(&name_lower) || tool_allowlist.contains(&name_lower) {
            if result.contains_key(name) {
                trace!(header = %name, "duplicate header value overwritten");
            }
            trace!(header = %name, "forwarding header to downstream MCP server");
            result.insert(name.clone(), value.clone());
        }
    }

    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_body_valid_with_arguments() {
        let body = Some(Bytes::from(
            r#"{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello"}}}"#,
        ));
        let parsed = parse_body(&body, serde_json::Value::from(1));
        assert_eq!(parsed.id, serde_json::Value::from(1));
        assert_eq!(parsed.arguments.len(), 1);
        assert_eq!(
            parsed.arguments.get("message"),
            Some(&serde_json::Value::String("hello".to_owned()))
        );
    }

    #[test]
    fn parse_body_none() {
        let parsed = parse_body(&None, serde_json::Value::Null);
        assert!(parsed.id.is_null());
        assert!(parsed.arguments.is_empty());
    }

    #[test]
    fn parse_body_id_comes_from_parameter_not_body() {
        let body = Some(Bytes::from(
            r#"{"jsonrpc":"2.0","id":999,"params":{"arguments":{}}}"#,
        ));
        let parsed = parse_body(&body, serde_json::Value::from(1));
        assert_eq!(parsed.id, serde_json::Value::from(1));
    }

    #[test]
    fn forwarded_response_includes_is_error_false() {
        let mcp_content: Vec<serde_json::Value> =
            vec![serde_json::json!({"type": "text", "text": "hello"})];
        let response = serde_json::json!({
            "jsonrpc": "2.0",
            "id": 1,
            "result": {"content": mcp_content, "isError": false}
        });
        let result = response.get("result").expect("result missing");
        assert_eq!(result.get("isError"), Some(&serde_json::Value::Bool(false)));
    }

    #[test]
    fn forwarded_response_includes_is_error_true() {
        let mcp_content: Vec<serde_json::Value> =
            vec![serde_json::json!({"type": "text", "text": "something went wrong"})];
        let response = serde_json::json!({
            "jsonrpc": "2.0",
            "id": 1,
            "result": {"content": mcp_content, "isError": true}
        });
        let result = response.get("result").expect("result missing");
        assert_eq!(result.get("isError"), Some(&serde_json::Value::Bool(true)));
    }

    #[test]
    fn build_success_response_redacts_injected_credential() {
        // The upstream echoes both the raw secret and the full header value.
        let call_result = wanaku_infra::mcp_client::CallToolResponse {
            content: vec![
                "your token is s3cr3t".to_owned(),
                "sent Authorization: Bearer s3cr3t".to_owned(),
            ],
            is_error: false,
        };
        let redactor = crate::credentials::CredentialRedactor::from_patterns(vec![
            "s3cr3t".to_owned(),
            "Bearer s3cr3t".to_owned(),
        ]);

        let response = build_success_response(&serde_json::json!(1), &call_result, &redactor, &[]);

        let content = response
            .pointer("/result/content")
            .and_then(|c| c.as_array())
            .expect("content array missing");
        assert_eq!(content[0]["text"], "your token is <redacted>");
        assert_eq!(content[1]["text"], "sent Authorization: <redacted>");
    }

    #[test]
    fn build_success_response_is_noop_without_binding() {
        let call_result = wanaku_infra::mcp_client::CallToolResponse {
            content: vec!["plain output".to_owned()],
            is_error: false,
        };
        let redactor = crate::credentials::CredentialRedactor::default();

        let response = build_success_response(&serde_json::json!(1), &call_result, &redactor, &[]);

        assert_eq!(
            response.pointer("/result/content/0/text").unwrap(),
            "plain output"
        );
    }

    #[test]
    fn build_success_response_redacts_forwarded_value_in_tool_error() {
        // #1964: a tool-level failure (isError) arrives on the success path. Its
        // content must still be stripped of forwarded header values and
        // credential-shaped text, not only brokered secrets.
        let call_result = wanaku_infra::mcp_client::CallToolResponse {
            content: vec!["upstream rejected token opaque-fwd-token-xyz".to_owned()],
            is_error: true,
        };
        // No brokered secret configured: the forwarded value must still be redacted.
        let redactor = crate::credentials::CredentialRedactor::default();
        let forwarded = vec!["opaque-fwd-token-xyz".to_owned()];

        let response =
            build_success_response(&serde_json::json!(1), &call_result, &redactor, &forwarded);

        let text = response
            .pointer("/result/content/0/text")
            .and_then(|t| t.as_str())
            .expect("content text missing");
        assert!(
            !text.contains("opaque-fwd-token-xyz"),
            "forwarded value leaked: {text}"
        );
        assert!(
            text.contains("[REDACTED]"),
            "expected redaction placeholder: {text}"
        );
    }

    #[test]
    fn build_success_response_skips_forwarded_redaction_on_success() {
        // A genuine success (isError false) must not run the #1964 forwarded-value
        // redaction, so ordinary output that happens to contain a forwarded value
        // stays intact for the agent that owns it.
        let call_result = wanaku_infra::mcp_client::CallToolResponse {
            content: vec!["result includes opaque-fwd-token-xyz".to_owned()],
            is_error: false,
        };
        let redactor = crate::credentials::CredentialRedactor::default();
        let forwarded = vec!["opaque-fwd-token-xyz".to_owned()];

        let response =
            build_success_response(&serde_json::json!(1), &call_result, &redactor, &forwarded);

        assert_eq!(
            response.pointer("/result/content/0/text").unwrap(),
            "result includes opaque-fwd-token-xyz"
        );
    }

    #[test]
    fn inject_header_arguments_from_schema() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "auth": {
                    "type": "string",
                    "x-mcp-header": "Authorization"
                },
                "message": {
                    "type": "string"
                }
            }
        });
        let mut args = HashMap::new();
        args.insert("message".to_owned(), serde_json::json!("hello"));

        let mut headers = HashMap::new();
        headers.insert(
            HeaderName::from_static("authorization"),
            HeaderValue::from_static("Bearer tok"),
        );

        inject_header_arguments(&mut args, &schema, &headers);
        assert_eq!(args.get("auth"), Some(&serde_json::json!("Bearer tok")));
        assert_eq!(args.get("message"), Some(&serde_json::json!("hello")));
    }

    #[test]
    fn inject_header_arguments_does_not_overwrite_existing() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "auth": {
                    "type": "string",
                    "x-mcp-header": "Authorization"
                }
            }
        });
        let mut args = HashMap::new();
        args.insert("auth".to_owned(), serde_json::json!("existing-value"));

        let mut headers = HashMap::new();
        headers.insert(
            HeaderName::from_static("authorization"),
            HeaderValue::from_static("Bearer tok"),
        );

        inject_header_arguments(&mut args, &schema, &headers);
        assert_eq!(args.get("auth"), Some(&serde_json::json!("existing-value")));
    }

    #[test]
    fn inject_header_arguments_no_annotation() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "message": {"type": "string"}
            }
        });
        let mut args = HashMap::new();
        let mut headers = HashMap::new();
        headers.insert(
            HeaderName::from_static("authorization"),
            HeaderValue::from_static("Bearer tok"),
        );

        inject_header_arguments(&mut args, &schema, &headers);
        assert!(args.is_empty());
    }

    #[test]
    fn inject_header_arguments_header_not_forwarded() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "auth": {
                    "type": "string",
                    "x-mcp-header": "Authorization"
                }
            }
        });
        let mut args = HashMap::new();
        let headers = HashMap::new();

        inject_header_arguments(&mut args, &schema, &headers);
        assert!(args.is_empty());
    }

    #[test]
    fn extract_allowed_headers_empty_allowlists() {
        let mut headers = http::HeaderMap::new();
        headers.insert("authorization", "Bearer tok".parse().unwrap());
        let result = extract_allowed_headers(&headers, &[], &[]);
        assert!(result.is_empty());
    }

    #[test]
    fn extract_allowed_headers_global_match() {
        let mut headers = http::HeaderMap::new();
        headers.insert("authorization", "Bearer tok".parse().unwrap());
        headers.insert("x-custom", "val".parse().unwrap());

        let global = vec!["authorization".to_owned()];
        let result = extract_allowed_headers(&headers, &global, &[]);

        assert_eq!(result.len(), 1);
        assert_eq!(
            result
                .get(&HeaderName::from_static("authorization"))
                .unwrap(),
            "Bearer tok"
        );
    }

    #[test]
    fn extract_allowed_headers_per_tool_match() {
        let mut headers = http::HeaderMap::new();
        headers.insert("dpop", "proof-jwt".parse().unwrap());
        headers.insert("x-unrelated", "val".parse().unwrap());

        let per_tool = vec!["dpop".to_owned()];
        let result = extract_allowed_headers(&headers, &[], &per_tool);

        assert_eq!(result.len(), 1);
        assert_eq!(
            result.get(&HeaderName::from_static("dpop")).unwrap(),
            "proof-jwt"
        );
    }

    #[test]
    fn extract_allowed_headers_combined_global_and_per_tool() {
        let mut headers = http::HeaderMap::new();
        headers.insert("authorization", "Bearer tok".parse().unwrap());
        headers.insert("dpop", "proof-jwt".parse().unwrap());
        headers.insert("x-unrelated", "val".parse().unwrap());

        let global = vec!["authorization".to_owned()];
        let per_tool = vec!["dpop".to_owned()];
        let result = extract_allowed_headers(&headers, &global, &per_tool);

        assert_eq!(result.len(), 2);
        assert!(result.contains_key(&HeaderName::from_static("authorization")));
        assert!(result.contains_key(&HeaderName::from_static("dpop")));
    }

    #[test]
    fn extract_allowed_headers_no_matching_headers() {
        let mut headers = http::HeaderMap::new();
        headers.insert("x-other", "val".parse().unwrap());

        let global = vec!["authorization".to_owned()];
        let result = extract_allowed_headers(&headers, &global, &[]);

        assert!(result.is_empty());
    }

    #[test]
    fn extract_allowed_headers_denied_header_blocked() {
        let mut headers = http::HeaderMap::new();
        headers.insert("content-type", "text/plain".parse().unwrap());
        headers.insert("authorization", "Bearer tok".parse().unwrap());

        let global = vec!["content-type".to_owned(), "authorization".to_owned()];
        let result = extract_allowed_headers(&headers, &global, &[]);

        assert_eq!(result.len(), 1);
        assert!(result.contains_key(&HeaderName::from_static("authorization")));
        assert!(!result.contains_key(&HeaderName::from_static("content-type")));
    }

    #[test]
    fn extract_allowed_headers_all_rmcp_reserved_blocked() {
        let mut headers = http::HeaderMap::new();
        headers.insert("accept", "application/json".parse().unwrap());
        headers.insert("mcp-session-id", "abc".parse().unwrap());
        headers.insert("host", "evil.com".parse().unwrap());
        headers.insert("transfer-encoding", "chunked".parse().unwrap());
        headers.insert("connection", "keep-alive".parse().unwrap());
        headers.insert("content-length", "42".parse().unwrap());

        let global = vec![
            "accept".to_owned(),
            "mcp-session-id".to_owned(),
            "host".to_owned(),
            "transfer-encoding".to_owned(),
            "connection".to_owned(),
            "content-length".to_owned(),
        ];
        let result = extract_allowed_headers(&headers, &global, &[]);

        assert!(result.is_empty());
    }

    // #1964: a failed forwarded call must not echo injected credentials to the
    // client response or the server log. The same redacted `detail` feeds both.
    #[test]
    fn redacted_forward_error_redacts_credential_shaped_detail() {
        let sentinel = "wanaku-sentinel-secret-1964";
        let upstream = format!("401 Unauthorized: upstream rejected Bearer {sentinel}");

        let detail = redacted_forward_error(&upstream, &[]);

        assert!(
            !detail.contains(sentinel),
            "redacted detail must not contain the forwarded credential: {detail}"
        );
        assert_eq!(detail, "[REDACTED]");
    }

    #[test]
    fn redacted_forward_error_redacts_opaque_forwarded_value() {
        // An opaque credential with no known shape must still be removed, because
        // Wanaku knows the exact value it forwarded upstream.
        let sentinel = "acme_live_9f3kwanakusentinel1964";
        let upstream = format!("403 Forbidden: rejected header value {sentinel}");
        let forwarded = vec![sentinel.to_owned()];

        let detail = redacted_forward_error(&upstream, &forwarded);

        assert!(
            !detail.contains(sentinel),
            "redacted detail must not contain the opaque forwarded value: {detail}"
        );
        assert_eq!(
            detail,
            "forwarded tool call failed: 403 Forbidden: rejected header value [REDACTED]"
        );
    }

    #[test]
    fn redacted_forward_error_preserves_ordinary_error() {
        let upstream = "connection refused (os error 61)".to_owned();

        let detail = redacted_forward_error(&upstream, &[]);

        assert_eq!(
            detail,
            "forwarded tool call failed: connection refused (os error 61)"
        );
    }

    #[test]
    fn redacted_forward_error_redacts_scheme_stripped_echo() {
        // #1964: the upstream echoes only the token part of the forwarded auth
        // header, without the "Bearer " scheme. The opaque token has no known
        // shape, so the token segment of the forwarded value must remove it.
        let sentinel = "acme_live_9f3kwanakusentinel1964";
        let forwarded = vec![format!("Bearer {sentinel}")];
        let upstream = format!("401 Unauthorized: invalid token {sentinel}");

        let detail = redacted_forward_error(&upstream, &forwarded);

        assert!(
            !detail.contains(sentinel),
            "redacted detail must not contain the scheme-stripped token: {detail}"
        );
    }

    #[test]
    fn redacted_forward_error_redacts_short_scheme_stripped_echo() {
        // #1964 review: a forwarded credential shorter than a normal token must
        // still be removed when the upstream echoes only the token part of the
        // auth header, without the "Bearer " scheme. A short segment has no known
        // shape, so only the forwarded token segment can remove it.
        let sentinel = "abc123";
        let forwarded = vec![format!("Bearer {sentinel}")];
        let upstream = format!("401 Unauthorized: invalid token {sentinel}");

        let detail = redacted_forward_error(&upstream, &forwarded);

        assert!(
            !detail.contains(sentinel),
            "redacted detail must not contain the short scheme-stripped token: {detail}"
        );
    }

    #[test]
    fn redacted_forward_error_redacts_substring_colliding_values() {
        // #1964: one forwarded value is a prefix of another, and the upstream
        // echoes both. The longest-first order must remove the longer value in
        // full. A shortest-first order would replace the shared prefix and leave
        // the trailing fragment of the longer secret in the detail.
        let short = "wanakucommonprefix";
        let leaked = "wanakusentinel1964";
        let long = format!("{short}{leaked}");
        let forwarded = vec![short.to_owned(), long.clone()];
        let upstream = format!("rejected {long} and standalone {short}");

        let detail = redacted_forward_error(&upstream, &forwarded);

        assert!(
            !detail.contains(leaked),
            "redacted detail must not leave a fragment of the longer secret: {detail}"
        );
    }
}
