use std::collections::HashMap;

use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use wanaku_praxis_apis::interactions::InMemoryInteractionStore;
use wanaku_praxis_apis::interactions::InteractionStore;
use wanaku_praxis_apis::registry::{InMemoryRegistry, ToolRegistry};
use wanaku_praxis_apis::safety::{SafetyAction, SafetyLevel, CLASSIFIER};

crate::body_filter_boilerplate!(SafetyCheckFilter, "wanaku_safety_check");

struct ParsedArgs {
    id: serde_json::Value,
    arguments: HashMap<String, String>,
    conversation_id: String,
}

fn parse_for_safety(body: &Option<Bytes>) -> ParsedArgs {
    let Some(body_bytes) = body else {
        return ParsedArgs {
            id: serde_json::Value::Null,
            arguments: HashMap::new(),
            conversation_id: "-".to_owned(),
        };
    };

    let Ok(parsed) = serde_json::from_slice::<serde_json::Value>(body_bytes) else {
        return ParsedArgs {
            id: serde_json::Value::Null,
            arguments: HashMap::new(),
            conversation_id: "-".to_owned(),
        };
    };

    let id = parsed
        .get("id")
        .cloned()
        .unwrap_or(serde_json::Value::Null);

    let mut arguments: HashMap<String, String> = parsed
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
        .unwrap_or_default();

    let conversation_id = arguments
        .remove(wanaku_praxis_apis::correlation::REQUEST_ID_ARG)
        .unwrap_or_else(|| "-".to_owned());

    ParsedArgs {
        id,
        arguments,
        conversation_id,
    }
}

impl SafetyCheckFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let method = match ctx.get_metadata("mcp.method") {
            Some(m) => m,
            None => return Ok(FilterAction::Continue),
        };

        if method != "tools/call" {
            return Ok(FilterAction::Continue);
        }

        let classifier = match CLASSIFIER.as_ref() {
            Some(c) => c,
            None => {
                tracing::debug!("safety classifier not configured (WANAKU_SAFETY_LLM_URL not set), skipping");
                return Ok(FilterAction::Continue);
            }
        };

        let tool_name = match ctx.get_metadata("mcp.name") {
            Some(n) => n.to_owned(),
            None => return Ok(FilterAction::Continue),
        };

        if let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() {
            let namespace = ctx
                .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
                .unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);

            if let Some(tool) = registry.get_tool_in_namespace(namespace, &tool_name) {
                if tool.skip_safety_check {
                    tracing::debug!(tool = %tool_name, "safety check skipped (opt-out)");
                    return Ok(FilterAction::Continue);
                }
            }
        }

        tracing::info!(tool = %tool_name, "running safety classification");

        let parsed = parse_for_safety(body);

        let history = if parsed.conversation_id != "-" {
            ctx.extensions
                .get::<InMemoryInteractionStore>()
                .map(|store| store.get_by_conversation_id(&parsed.conversation_id))
                .unwrap_or_default()
        } else {
            Vec::new()
        };

        let level = classifier
            .classify(&tool_name, &parsed.arguments, &history)
            .await;

        let action = classifier.action_for(level);

        tracing::info!(
            tool = %tool_name,
            safety_level = level.as_str(),
            action = ?action,
            conversation_id = %parsed.conversation_id,
            "safety classification result"
        );

        match (level, action) {
            (SafetyLevel::Green, _) => {
                Ok(FilterAction::Continue)
            }
            (_, SafetyAction::Block) => {
                tracing::warn!(
                    tool = %tool_name,
                    safety_level = level.as_str(),
                    conversation_id = %parsed.conversation_id,
                    "tool call blocked by safety classification"
                );
                Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    -32001,
                    &format!(
                        "tool call blocked by safety classification: {}",
                        level.as_str()
                    ),
                ))
            }
            (_, SafetyAction::Warn) => {
                tracing::warn!(
                    tool = %tool_name,
                    safety_level = level.as_str(),
                    conversation_id = %parsed.conversation_id,
                    "safety classification warning"
                );
                ctx.set_metadata("wanaku.safety.level", level.as_str());
                Ok(FilterAction::Continue)
            }
            (_, SafetyAction::Log) => {
                tracing::warn!(
                    tool = %tool_name,
                    safety_level = level.as_str(),
                    conversation_id = %parsed.conversation_id,
                    "safety classification"
                );
                Ok(FilterAction::Continue)
            }
        }
    }
}
