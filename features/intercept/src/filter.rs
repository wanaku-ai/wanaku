use std::sync::atomic::{AtomicU16, Ordering};
use std::time::{Instant, SystemTime, UNIX_EPOCH};

use async_trait::async_trait;
use bytes::Bytes;
use praxis_filter::{
    BodyAccess, BodyMode, FilterAction, FilterError, HttpFilter, HttpFilterContext,
};
use wanaku_types::audit_redaction::AuditRedactor;
use wanaku_types::correlation::{self, REQUEST_ID_ARG};
use wanaku_types::interactions::{InMemoryInteractionStore, Interaction, InteractionStore};

use crate::config::InterceptConfig;

struct InterceptState {
    path: String,
    body: Bytes,
    conversation_id: String,
    start: Instant,
    status: AtomicU16,
}

pub struct InterceptFilter {
    max_body_bytes: usize,
    capture_payloads: bool,
    redactor: AuditRedactor,
}

impl InterceptFilter {
    pub fn from_config(config: &serde_yaml::Value) -> Result<Box<dyn HttpFilter>, FilterError> {
        let mut cfg = InterceptConfig::parse(config);
        cfg.apply_environment();
        cfg.normalize();
        Ok(Box::new(Self::new(cfg)))
    }

    fn new(cfg: InterceptConfig) -> Self {
        let max_body_bytes = cfg.max_body_bytes;
        let capture_payloads = cfg.capture_payloads;
        // #1964: log the effective capture setting so an operator can confirm the
        // privacy control. A mistyped `capture_payloads` key is ignored silently
        // and falls back to the capture-on default.
        tracing::info!(
            capture_payloads,
            max_body_bytes,
            "intercept filter initialized"
        );
        Self {
            max_body_bytes,
            capture_payloads,
            redactor: cfg.into_redactor(),
        }
    }

    /// Prepare the captured bodies for retention.
    ///
    /// #1964: redact sensitive fields and credential-shaped values before the
    /// bodies reach the interaction store. This uses the content redaction
    /// variant, so conversation text in `messages[].content` survives even when
    /// it contains common words such as "basic" or "bearer". Intent analysis
    /// keeps working. When capture is disabled, both bodies are dropped to null.
    fn redact_bodies(
        &self,
        request_body: &mut serde_json::Value,
        response_body: &mut serde_json::Value,
    ) {
        if !self.capture_payloads {
            *request_body = serde_json::Value::Null;
            *response_body = serde_json::Value::Null;
            return;
        }
        let request_meta = self.redactor.redact_content(request_body);
        let response_meta = self.redactor.redact_content(response_body);
        if request_meta.payload_truncated || response_meta.payload_truncated {
            tracing::debug!(
                request_truncated = request_meta.payload_truncated,
                response_truncated = response_meta.payload_truncated,
                "captured body exceeded payload_max_bytes and was replaced with a redaction marker"
            );
        }
    }
}

fn parse_body(bytes: &[u8]) -> serde_json::Value {
    serde_json::from_slice(bytes)
        .unwrap_or_else(|_| serde_json::Value::String(String::from_utf8_lossy(bytes).into_owned()))
}

const ID_PREFIX: &str = "wk-";

fn find_existing_id(messages: &[serde_json::Value]) -> Option<String> {
    for msg in messages {
        if msg.get("role").and_then(serde_json::Value::as_str) != Some("system") {
            continue;
        }
        let content = msg.get("content").and_then(serde_json::Value::as_str)?;
        if let Some(pos) = content.find(ID_PREFIX) {
            let id: String = content[pos..]
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

    let Some(messages) = parsed
        .get_mut("messages")
        .and_then(serde_json::Value::as_array_mut)
    else {
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

    #[expect(
        clippy::too_many_lines,
        reason = "interaction recording with multiple field extractions"
    )]
    #[expect(
        clippy::cast_possible_truncation,
        reason = "duration and epoch millis within u64 range"
    )]
    fn on_response_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
        end_of_stream: bool,
    ) -> Result<FilterAction, FilterError> {
        if !end_of_stream {
            return Ok(FilterAction::Continue);
        }

        let Some(state) = ctx.get_filter_state::<InterceptState>() else {
            return Ok(FilterAction::Continue);
        };

        let status_code = state.status.load(Ordering::Relaxed);
        let duration_ms = state.start.elapsed().as_millis() as u64;

        let response_bytes = body
            .as_ref()
            .map(std::convert::AsRef::as_ref)
            .unwrap_or_default();

        let mut request_body = parse_body(&state.body);
        let mut response_body = parse_body(response_bytes);

        // Extract envelope metadata from the raw bodies before redaction. The
        // completion id and model are routing metadata, not secrets, and are
        // retained even when payload capture is disabled.
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

        self.redact_bodies(&mut request_body, &mut response_body);

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

#[cfg(test)]
#[path = "filter_test.rs"]
mod tests;
