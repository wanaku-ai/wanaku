//! Versioned WASM host capabilities and runtime-bound MCP tool implementations.
#![deny(unsafe_code)]

pub mod config;
mod engine;
mod handlers;
mod host;
mod routes;
pub mod state;

pub use host::types as wit_types;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};
use state::HandlerState;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::feature::{Feature, HttpContext};
use wanaku_types::wasm::ToolHandler;

/// Integration point for server and embedded Rust applications.
pub struct ToolHandlerFeature {
    state: HandlerState,
    startup_error: Mutex<Option<String>>,
}

impl ToolHandlerFeature {
    #[must_use]
    pub fn new(registry: InMemoryRegistry) -> Self {
        Self {
            state: HandlerState::new(registry),
            startup_error: Mutex::new(None),
        }
    }

    /// Restore bindings from a local file; invalid bindings fail startup closed.
    pub fn with_persistence(mut self, path: impl Into<PathBuf>) -> Result<Self, String> {
        self.state = self.state.with_persistence(path)?;
        Ok(self)
    }

    #[must_use]
    pub fn state(&self) -> HandlerState {
        self.state.clone()
    }
}

struct HandlerExtension(HandlerState);

impl PipelineExtension for HandlerExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert::<Arc<dyn ToolHandler>>(Arc::new(self.0.clone()));
    }
}

#[async_trait::async_trait]
impl Feature for ToolHandlerFeature {
    fn name(&self) -> &'static str {
        "tool-handler"
    }

    fn register_filters(&self, _registry: &mut FilterRegistry) {}

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        vec![Box::new(HandlerExtension(self.state.clone()))]
    }

    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        handlers::dispatch(routes::resolve(ctx.method, ctx.path), &self.state, ctx.body).await
    }

    fn load_yaml_config(&self, root: &serde_yaml::Value) {
        let Some(value) = root.get("tool_handlers") else {
            return;
        };
        let loaded = serde_yaml::from_value(value.clone())
            .map_err(|error| error.to_string())
            .and_then(|definitions| self.state.replace(definitions));
        if let Err(error) = loaded {
            tracing::error!(%error, "tool handler configuration rejected");
            if let Ok(mut stored) = self.startup_error.lock() {
                *stored = Some(error);
            }
        }
    }

    fn load_env_config(&self) {}

    fn validate_startup(&self) -> Result<(), String> {
        match self.startup_error.lock() {
            Ok(error) => error.as_ref().map_or(Ok(()), |error| Err(error.clone())),
            Err(_) => Err("handler runtime unavailable".into()),
        }
    }
}
