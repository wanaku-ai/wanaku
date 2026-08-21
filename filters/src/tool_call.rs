use std::collections::HashMap;

use bytes::Bytes;
use http::{HeaderName, HeaderValue};
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::{trace, warn};
use wanaku_apis::config::ENV;
use wanaku_apis::registry::{InMemoryRegistry, ToolEntry, ToolRegistry};

crate::body_filter_boilerplate!(ToolCallFilter, "wanaku_tool_call");

struct ParsedBody {
    id: serde_json::Value,
    arguments: HashMap<String, serde_json::Value>,
}

fn parse_body(body: &Option<Bytes>) -> ParsedBody {
    let Some(body_bytes) = body else {
        return ParsedBody {
            id: serde_json::Value::Null,
            arguments: HashMap::new(),
        };
    };

    let Ok(parsed) = serde_json::from_slice::<serde_json::Value>(body_bytes) else {
        return ParsedBody {
            id: serde_json::Value::Null,
            arguments: HashMap::new(),
        };
    };

    let id = parsed
        .get("id")
        .cloned()
        .unwrap_or(serde_json::Value::Null);

    let arguments = parsed
        .get("params")
        .and_then(|p| p.get("arguments"))
        .and_then(|a| a.as_object())
        .map(|args| {
            args.iter()
                .map(|(k, v)| (k.clone(), v.clone()))
                .collect()
        })
        .unwrap_or_default();

    ParsedBody { id, arguments }
}

impl ToolCallFilter {
    #[expect(clippy::too_many_lines, clippy::cognitive_complexity, reason = "MCP protocol handler with JSON-RPC response construction")]
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let Some(method) = ctx.get_metadata(crate::MCP_METHOD_KEY) else {
            return Ok(FilterAction::Continue);
        };

        if method != "tools/call" {
            return Ok(FilterAction::Continue);
        }

        let mut parsed = parse_body(body);

        let tool_name = match ctx.get_metadata(crate::MCP_NAME_KEY) {
            Some(n) => n.to_owned(),
            None => {
                return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INVALID_PARAMS, "missing tool name in tools/call"));
            }
        };

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_apis::registry::DEFAULT_NAMESPACE);

        let conversation_id = parsed.arguments
            .remove(wanaku_apis::correlation::REQUEST_ID_ARG)
            .map_or_else(|| "-".to_owned(), |v| match v {
                serde_json::Value::String(s) => s,
                other => other.to_string(),
            });

        let request_id = ctx.request.headers.get("x-request-id")
            .and_then(|v| v.to_str().ok())
            .unwrap_or("-");

        for (name, value) in &ctx.request.headers {
            tracing::trace!(header = %name, value = ?value, "tools/call request header");
        }

        tracing::info!(
            tool = %tool_name,
            namespace = %namespace,
            conversation_id = %conversation_id,
            x_request_id = %request_id,
            "tools/call"
        );

        tracing::debug!(
            tool = %tool_name,
            arguments = ?parsed.arguments,
            "parsed tools/call request body (x-request-id stripped)"
        );

        let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() else {
            tracing::error!("InMemoryRegistry not found in request extensions");
            return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INTERNAL_ERROR, "internal error: registry unavailable"));
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
            let forward_headers = collect_forward_headers(&ctx.request.headers, &tool);
            inject_header_arguments(&mut parsed.arguments, &tool.input_schema, &forward_headers);
            return self
                .handle_forwarded_call(&tool, &tool_name, &parsed, forward_headers)
                .await;
        }

        warn!(tool = %tool_name, tool_type = %tool.type_, "unsupported tool type — only MCP-forwarded tools are supported");
        Ok(crate::response::json_rpc_error(
            &parsed.id,
            crate::response::JSONRPC_INTERNAL_ERROR,
            &format!("unsupported tool type '{}': only MCP-forwarded tools are supported", tool.type_),
        ))
    }

    #[expect(clippy::too_many_lines, reason = "MCP forwarding handler with error paths")]
    async fn handle_forwarded_call(
        &self,
        tool: &ToolEntry,
        tool_name: &str,
        parsed: &ParsedBody,
        forward_headers: HashMap<HeaderName, HeaderValue>,
    ) -> Result<FilterAction, FilterError> {
        trace!(
            tool = %tool_name,
            uri = %tool.uri,
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

        match wanaku_apis::mcp_client::call_tool(&tool.uri, tool_name, arguments, forward_headers)
            .await
        {
            Ok(call_result) => {
                let mcp_content: Vec<serde_json::Value> = call_result.content
                    .iter()
                    .map(|text| serde_json::json!({"type": "text", "text": text}))
                    .collect();

                let response = serde_json::json!({
                    "jsonrpc": "2.0",
                    "id": parsed.id,
                    "result": {"content": mcp_content, "isError": call_result.is_error}
                });

                let response_body = Bytes::from(response.to_string());
                Ok(FilterAction::Reject(crate::response::json_response(response_body)))
            }
            Err(e) => {
                warn!(tool = %tool_name, error = %e, "MCP forward call failed");
                Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("forwarded tool call failed: {e}"),
                ))
            }
        }
    }
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
        let parsed = parse_body(&body);
        assert_eq!(parsed.id, serde_json::Value::from(1));
        assert_eq!(parsed.arguments.len(), 1);
        assert_eq!(parsed.arguments.get("message"), Some(&serde_json::Value::String("hello".to_owned())));
    }

    #[test]
    fn parse_body_arguments_with_non_string_values() {
        let body = Some(Bytes::from(
            r#"{"id":2,"params":{"arguments":{"count":42,"flag":true,"nested":{"a":1}}}}"#,
        ));
        let parsed = parse_body(&body);
        assert_eq!(parsed.id, serde_json::Value::from(2));
        assert_eq!(parsed.arguments.get("count"), Some(&serde_json::json!(42)));
        assert_eq!(parsed.arguments.get("flag"), Some(&serde_json::json!(true)));
        assert_eq!(parsed.arguments.get("nested"), Some(&serde_json::json!({"a": 1})));
    }

    #[test]
    fn parse_body_missing_arguments() {
        let body = Some(Bytes::from(
            r#"{"id":3,"params":{"name":"echo"}}"#,
        ));
        let parsed = parse_body(&body);
        assert_eq!(parsed.id, serde_json::Value::from(3));
        assert!(parsed.arguments.is_empty());
    }

    #[test]
    fn parse_body_missing_params() {
        let body = Some(Bytes::from(r#"{"id":4}"#));
        let parsed = parse_body(&body);
        assert_eq!(parsed.id, serde_json::Value::from(4));
        assert!(parsed.arguments.is_empty());
    }

    #[test]
    fn parse_body_none() {
        let parsed = parse_body(&None);
        assert!(parsed.id.is_null());
        assert!(parsed.arguments.is_empty());
    }

    #[test]
    fn parse_body_malformed_json() {
        let body = Some(Bytes::from("not json"));
        let parsed = parse_body(&body);
        assert!(parsed.id.is_null());
        assert!(parsed.arguments.is_empty());
    }

    #[test]
    fn parse_body_empty_bytes() {
        let body = Some(Bytes::new());
        let parsed = parse_body(&body);
        assert!(parsed.id.is_null());
        assert!(parsed.arguments.is_empty());
    }

    #[test]
    fn parse_body_no_id_field() {
        let body = Some(Bytes::from(
            r#"{"params":{"arguments":{"key":"val"}}}"#,
        ));
        let parsed = parse_body(&body);
        assert!(parsed.id.is_null());
        assert_eq!(parsed.arguments.get("key"), Some(&serde_json::Value::String("val".to_owned())));
    }

    #[test]
    fn parse_body_arguments_is_not_object() {
        let body = Some(Bytes::from(
            r#"{"id":5,"params":{"arguments":"not-an-object"}}"#,
        ));
        let parsed = parse_body(&body);
        assert_eq!(parsed.id, serde_json::Value::from(5));
        assert!(parsed.arguments.is_empty());
    }

    #[test]
    fn forwarded_response_includes_is_error_false() {
        let mcp_content: Vec<serde_json::Value> = vec![
            serde_json::json!({"type": "text", "text": "hello"}),
        ];
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
        let mcp_content: Vec<serde_json::Value> = vec![
            serde_json::json!({"type": "text", "text": "something went wrong"}),
        ];
        let response = serde_json::json!({
            "jsonrpc": "2.0",
            "id": 1,
            "result": {"content": mcp_content, "isError": true}
        });
        let result = response.get("result").expect("result missing");
        assert_eq!(result.get("isError"), Some(&serde_json::Value::Bool(true)));
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
        assert_eq!(result.get(&HeaderName::from_static("authorization")).unwrap(), "Bearer tok");
    }

    #[test]
    fn extract_allowed_headers_per_tool_match() {
        let mut headers = http::HeaderMap::new();
        headers.insert("dpop", "proof-jwt".parse().unwrap());
        headers.insert("x-unrelated", "val".parse().unwrap());

        let per_tool = vec!["dpop".to_owned()];
        let result = extract_allowed_headers(&headers, &[], &per_tool);

        assert_eq!(result.len(), 1);
        assert_eq!(result.get(&HeaderName::from_static("dpop")).unwrap(), "proof-jwt");
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
}
