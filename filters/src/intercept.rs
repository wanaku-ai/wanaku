use std::sync::atomic::{AtomicU16, Ordering};
use std::time::{Instant, SystemTime, UNIX_EPOCH};

use async_trait::async_trait;
use bytes::Bytes;
use praxis_filter::{
    BodyAccess, BodyMode, FilterAction, FilterError, HttpFilter, HttpFilterContext,
};
use wanaku_praxis_apis::interactions::{InMemoryInteractionStore, Interaction, InteractionStore};

struct InterceptState {
    path: String,
    body: Bytes,
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

#[async_trait]
impl HttpFilter for InterceptFilter {
    fn name(&self) -> &'static str {
        "wanaku_intercept"
    }

    fn needs_request_context(&self) -> bool {
        true
    }

    fn request_body_access(&self) -> BodyAccess {
        BodyAccess::ReadOnly
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

        tracing::debug!(path = %path, body_len = body_bytes.len(), "intercepted request");

        ctx.insert_filter_state(InterceptState {
            path,
            body: body_bytes,
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

        let epoch_ms = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;

        let interaction = Interaction {
            epoch_ms,
            path: state.path.clone(),
            request_body,
            response_body,
            status_code,
            duration_ms,
        };

        tracing::debug!(
            path = %interaction.path,
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
