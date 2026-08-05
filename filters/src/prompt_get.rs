use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::{trace, warn};
use wanaku_praxis_apis::registry::{InMemoryRegistry, PromptRegistry};

crate::body_filter_boilerplate!(PromptGetFilter, "wanaku_prompt_get");

struct ParsedBody {
    id: serde_json::Value,
    name: Option<String>,
    arguments: serde_json::Map<String, serde_json::Value>,
}

fn parse_body(body: &Option<Bytes>) -> ParsedBody {
    let Some(body_bytes) = body else {
        return ParsedBody {
            id: serde_json::Value::Null,
            name: None,
            arguments: serde_json::Map::new(),
        };
    };

    let Ok(parsed) = serde_json::from_slice::<serde_json::Value>(body_bytes) else {
        return ParsedBody {
            id: serde_json::Value::Null,
            name: None,
            arguments: serde_json::Map::new(),
        };
    };

    let id = parsed
        .get("id")
        .cloned()
        .unwrap_or(serde_json::Value::Null);

    let params = parsed.get("params");

    let name = params
        .and_then(|p| p.get("name"))
        .and_then(|n| n.as_str())
        .map(str::to_owned);

    let arguments = params
        .and_then(|p| p.get("arguments"))
        .and_then(|a| a.as_object())
        .cloned()
        .unwrap_or_default();

    ParsedBody { id, name, arguments }
}

impl PromptGetFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let method = match ctx.get_metadata("mcp.method") {
            Some(m) => m,
            None => return Ok(FilterAction::Continue),
        };

        if method != "prompts/get" {
            return Ok(FilterAction::Continue);
        }

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);

        let parsed = parse_body(body);

        let prompt_name = match &parsed.name {
            Some(n) => n.clone(),
            None => {
                return Ok(crate::response::json_rpc_error(&parsed.id, -32602, "missing name in prompts/get"));
            }
        };

        trace!(prompt = %prompt_name, namespace = %namespace, "handling MCP prompts/get request");

        let registry = match ctx.extensions.get::<InMemoryRegistry>() {
            Some(r) => r,
            None => {
                tracing::error!("InMemoryRegistry not found in request extensions");
                return Ok(crate::response::json_rpc_error(&parsed.id, -32603, "internal error: registry unavailable"));
            }
        };

        let prompt = match registry.get_prompt_in_namespace(namespace, &prompt_name) {
            Some(p) => p,
            None => {
                warn!(prompt = %prompt_name, "prompt not found in registry");
                return Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    -32602,
                    &format!("prompt not found: {prompt_name}"),
                ));
            }
        };

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
}
