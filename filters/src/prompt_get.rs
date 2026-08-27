use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::{trace, warn};
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::registry::PromptRegistry;

crate::body_filter_boilerplate!(PromptGetFilter, "wanaku_prompt_get");

struct ParsedBody {
    id: serde_json::Value,
    name: Option<String>,
    arguments: serde_json::Map<String, serde_json::Value>,
}

fn parse_body(body: &Option<Bytes>, json_rpc_id: serde_json::Value) -> ParsedBody {
    let params = crate::json_rpc::JsonRpcParams::parse(body);
    ParsedBody { id: json_rpc_id, name: params.name, arguments: params.arguments }
}

impl PromptGetFilter {
    #[expect(clippy::too_many_lines, reason = "MCP protocol handler with JSON-RPC response construction")]
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let Some(method) = ctx.get_metadata(crate::MCP_METHOD_KEY) else {
            return Ok(FilterAction::Continue);
        };

        if method != "prompts/get" {
            return Ok(FilterAction::Continue);
        }

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_types::registry::DEFAULT_NAMESPACE);

        let json_rpc_id = crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));
        let parsed = parse_body(body, json_rpc_id);

        let prompt_name = match &parsed.name {
            Some(n) => n.clone(),
            None => {
                return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INVALID_PARAMS, "missing name in prompts/get"));
            }
        };

        trace!(prompt = %prompt_name, namespace = %namespace, "handling MCP prompts/get request");

        let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() else {
            tracing::error!("InMemoryRegistry not found in request extensions");
            return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INTERNAL_ERROR, "internal error: registry unavailable"));
        };

        let Some(prompt) = registry.get_prompt_in_namespace(namespace, &prompt_name) else {
            warn!(prompt = %prompt_name, "prompt not found in registry");
            return Ok(crate::response::json_rpc_error(
                &parsed.id,
                crate::response::JSONRPC_INVALID_PARAMS,
                &format!("prompt not found: {prompt_name}"),
            ));
        };

        if prompt.messages.is_empty()
            && let Some(ref uri) = prompt.configuration_uri {
                return self.handle_forwarded_get(uri, &prompt_name, &parsed).await;
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
        Ok(FilterAction::Reject(crate::response::json_response(response_body)))
    }

    async fn handle_forwarded_get(
        &self,
        forward_address: &str,
        prompt_name: &str,
        parsed: &ParsedBody,
    ) -> Result<FilterAction, FilterError> {
        trace!(prompt = %prompt_name, forward = %forward_address, "forwarding prompts/get to remote MCP server");

        let arguments = if parsed.arguments.is_empty() {
            None
        } else {
            Some(parsed.arguments.clone())
        };

        match wanaku_infra::mcp_client::get_prompt(forward_address, prompt_name, arguments).await {
            Ok(result) => {
                let response = serde_json::json!({
                    "jsonrpc": "2.0",
                    "id": parsed.id,
                    "result": result,
                });

                let response_body = Bytes::from(response.to_string());
                Ok(FilterAction::Reject(crate::response::json_response(response_body)))
            }
            Err(e) => {
                warn!(prompt = %prompt_name, error = %e, "MCP forward prompt get failed");
                Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("forwarded prompt get failed: {e}"),
                ))
            }
        }
    }
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
    fn parse_body_name_only_no_arguments() {
        let body = Some(Bytes::from(
            r#"{"id":2,"params":{"name":"greet"}}"#,
        ));
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
