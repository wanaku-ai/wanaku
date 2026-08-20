#![deny(unsafe_code)]

pub mod filter;
mod routes;

use std::sync::RwLock;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};

use wanaku_apis::feature::{Feature, HttpContext};
use wanaku_apis::interactions::InMemoryInteractionStore;

use crate::routes::{
    InteractionRoute, handle_interaction_clear, handle_interaction_list,
    resolve_interaction_route,
};

const DEFAULT_CAPACITY: usize = 1000;

pub struct InterceptFeature {
    store: RwLock<InMemoryInteractionStore>,
}

impl InterceptFeature {
    #[must_use]
    pub fn new() -> Self {
        Self {
            store: RwLock::new(InMemoryInteractionStore::new(DEFAULT_CAPACITY)),
        }
    }
}

impl Default for InterceptFeature {
    fn default() -> Self {
        Self::new()
    }
}

struct InteractionStoreExtension {
    store: InMemoryInteractionStore,
}

impl PipelineExtension for InteractionStoreExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.store.clone());
    }
}

#[async_trait::async_trait]
impl Feature for InterceptFeature {
    fn name(&self) -> &'static str {
        "intercept"
    }

    fn register_filters(&self, registry: &mut FilterRegistry) {
        praxis_filter::register_filters!(
            @register registry,
            http "wanaku_intercept" => crate::filter::InterceptFilter::from_config
        );
    }

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        let store = match self.store.read() {
            Ok(guard) => guard.clone(),
            Err(e) => {
                tracing::warn!(error = %e, "interaction store lock poisoned, using default");
                InMemoryInteractionStore::new(DEFAULT_CAPACITY)
            }
        };
        vec![Box::new(InteractionStoreExtension { store })]
    }

    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        let route = resolve_interaction_route(ctx.method, ctx.path);
        if route == InteractionRoute::NotFound {
            return None;
        }
        let store = match self.store.read() {
            Ok(guard) => guard.clone(),
            Err(e) => {
                tracing::warn!(error = %e, "interaction store lock poisoned");
                return None;
            }
        };
        Some(match route {
            InteractionRoute::List => handle_interaction_list(&store),
            InteractionRoute::Clear => handle_interaction_clear(&store),
            InteractionRoute::NotFound => return None,
        })
    }

    fn load_yaml_config(&self, _root: &serde_yaml::Value) {}

    fn load_env_config(&self) {
        if let Some(capacity) = std::env::var("WANAKU_INTERACTION_CAPACITY")
            .ok()
            .and_then(|s| s.parse::<usize>().ok())
        {
            match self.store.write() {
                Ok(mut guard) => {
                    *guard = InMemoryInteractionStore::new(capacity);
                    tracing::info!(capacity, "interaction store capacity configured from env");
                }
                Err(e) => {
                    tracing::warn!(error = %e, "failed to update interaction store capacity");
                }
            }
        }
    }
}
