use std::sync::atomic::{AtomicU16, Ordering};
use std::time::{Instant, SystemTime, UNIX_EPOCH};

use async_trait::async_trait;
use bytes::Bytes;
use praxis_filter::{
    BodyAccess, BodyMode, FilterAction, FilterError, HttpFilter, HttpFilterContext,
};
use wanaku_praxis_apis::correlation::{self, REQUEST_ID_ARG};
use wanaku_praxis_apis::interactions::{InMemoryInteractionStore, Interaction, InteractionStore};

struct InterceptState {
    path: String,
    body: Bytes,
    conversation_id: String,
    start: Instant,
    status: AtomicU16,
}

pub struct InterceptFilter {
    max_body_bytes: usize,
}

impl InterceptFilter {
    pub fn from_config(config: &serde_yaml::Value) -> Result<Box<dyn HttpFilter>, FilterError> {
        let max_body_bytes = config
            .get("max_body_bytes")
            .and_then(serde_yaml::Value::as_u64)
            .unwrap_or(4_194_304) as usize;

        Ok(Box::new(Self { max_body_bytes }))
    }
}

fn parse_body(bytes: &[u8]) -> serde_json::Value {
    serde_json::from_slice(bytes).unwrap_or_else(|_| {
        serde_json::Value::String(String::from_utf8_lossy(bytes).into_owned())
    })
}

const ID_PREFIX: &str = "wk-";

fn find_existing_id(messages: &[serde_json::Value]) -> Option<String> {
    for msg in messages {
        if msg.get("role").and_then(|r| r.as_str()) != Some("system") {
            continue;
        }
        let content = msg.get("content").and_then(|c| c.as_str())?;
        if let Some(pos) = content.find(ID_PREFIX) {
            let start = pos;
            let id: String = content[start..]
                .chars()
                .take_while(|c| c.is_ascii_alphanumeric() || *c == '-')
                .collect();
            if id.len() > ID_PREFIX.len() {
                return Some(id);
            }
        }
    }
    None
}

fn inject_system_prompt(body_bytes: &[u8], conversation_id: &str) -> (Option<Bytes>, String) {
    let Ok(mut parsed) = serde_json::from_slice::<serde_json::Value>(body_bytes) else {
        return (None, conversation_id.to_owned());
    };

    let Some(messages) = parsed.get_mut("messages").and_then(|m| m.as_array_mut()) else {
        return (None, conversation_id.to_owned());
    };

    if let Some(existing) = find_existing_id(messages) {
        return (None, existing);
    }

    let system_msg = serde_json::json!({
        "role": "system",
        "content": format!(
            "For all tool calls, use '{conversation_id}' as the {REQUEST_ID_ARG} argument."
        )
    });

    messages.insert(0, system_msg);

    let bytes = serde_json::to_vec(&parsed).ok().map(Bytes::from);
    (bytes, conversation_id.to_owned())
}

#[async_trait]
impl HttpFilter for InterceptFilter {
    fn name(&self) -> &'static str {
        "wanaku_intercept"
    }

    fn needs_request_context(&self) -> bool {
        true
    }

    fn request_body_access(&self) -> BodyAccess {
        BodyAccess::ReadWrite
    }

    fn request_body_mode(&self) -> BodyMode {
        BodyMode::StreamBuffer {
            max_bytes: Some(self.max_body_bytes),
        }
    }

    fn response_body_access(&self) -> BodyAccess {
        BodyAccess::ReadOnly
    }

    fn response_body_mode(&self) -> BodyMode {
        BodyMode::StreamBuffer {
            max_bytes: Some(self.max_body_bytes),
        }
    }

    async fn on_request(
        &self,
        _ctx: &mut HttpFilterContext<'_>,
    ) -> Result<FilterAction, FilterError> {
        Ok(FilterAction::Continue)
    }

    async fn on_request_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
        end_of_stream: bool,
    ) -> Result<FilterAction, FilterError> {
        if !end_of_stream {
            return Ok(FilterAction::Continue);
        }

        let path = ctx.request.uri.path().to_owned();
        let body_bytes = body.clone().unwrap_or_default();
        let generated_id = correlation::generate_short_id();

        let (enriched, conversation_id) = inject_system_prompt(&body_bytes, &generated_id);
        if let Some(new_body) = enriched {
            *body = Some(new_body);
        }

        tracing::debug!(
            path = %path,
            conversation_id = %conversation_id,
            body_len = body_bytes.len(),
            "intercepted request"
        );

        ctx.insert_filter_state(InterceptState {
            path,
            body: body_bytes,
            conversation_id,
            start: Instant::now(),
            status: AtomicU16::new(0),
        });

        Ok(FilterAction::Continue)
    }

    async fn on_response(
        &self,
        ctx: &mut HttpFilterContext<'_>,
    ) -> Result<FilterAction, FilterError> {
        if let (Some(state), Some(resp)) = (
            ctx.get_filter_state::<InterceptState>(),
            &ctx.response_header,
        ) {
            state.status.store(resp.status.as_u16(), Ordering::Relaxed);
        }
        Ok(FilterAction::Continue)
    }

    fn on_response_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
        end_of_stream: bool,
    ) -> Result<FilterAction, FilterError> {
        if !end_of_stream {
            return Ok(FilterAction::Continue);
        }

        let state = match ctx.get_filter_state::<InterceptState>() {
            Some(s) => s,
            None => return Ok(FilterAction::Continue),
        };

        let status_code = state.status.load(Ordering::Relaxed);
        let duration_ms = state.start.elapsed().as_millis() as u64;

        let response_bytes = body.as_ref().map(|b| b.as_ref()).unwrap_or_default();

        let request_body = parse_body(&state.body);
        let response_body = parse_body(response_bytes);

        let completion_id = response_body
            .get("id")
            .and_then(serde_json::Value::as_str)
            .map(String::from);

        let model = response_body
            .get("model")
            .and_then(serde_json::Value::as_str)
            .map(String::from)
            .or_else(|| {
                request_body
                    .get("model")
                    .and_then(serde_json::Value::as_str)
                    .map(String::from)
            });

        let epoch_ms = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;

        let interaction = Interaction {
            epoch_ms,
            path: state.path.clone(),
            conversation_id: Some(state.conversation_id.clone()),
            completion_id,
            model,
            request_body,
            response_body,
            status_code,
            duration_ms,
        };

        tracing::debug!(
            path = %interaction.path,
            conversation_id = ?interaction.conversation_id,
            status = interaction.status_code,
            duration_ms = interaction.duration_ms,
            "recorded interaction"
        );

        if let Some(store) = ctx.extensions.get::<InMemoryInteractionStore>() {
            store.record(interaction);
        }

        Ok(FilterAction::Continue)
    }
}
