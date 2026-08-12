#![deny(unsafe_code)]

pub mod filter;
mod routes;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};

use wanaku_praxis_apis::feature::Feature;
use wanaku_praxis_apis::interactions::InMemoryInteractionStore;

use crate::routes::{
    InteractionRoute, handle_interaction_clear, handle_interaction_list,
    resolve_interaction_route,
};

const DEFAULT_CAPACITY: usize = 1000;

pub struct InterceptFeature {
    store: InMemoryInteractionStore,
}

impl InterceptFeature {
    #[must_use]
    pub fn new() -> Self {
        let capacity = std::env::var("WANAKU_INTERACTION_CAPACITY")
            .ok()
            .and_then(|s| s.parse::<usize>().ok())
            .unwrap_or(DEFAULT_CAPACITY);
        Self {
            store: InMemoryInteractionStore::new(capacity),
        }
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
        vec![Box::new(InteractionStoreExtension {
            store: self.store.clone(),
        })]
    }

    async fn handle_route(
        &self,
        method: &str,
        path: &str,
        _query: Option<&str>,
        _body: Option<&str>,
    ) -> Option<Response<Vec<u8>>> {
        let route = resolve_interaction_route(method, path);
        if route == InteractionRoute::NotFound {
            return None;
        }
        Some(match route {
            InteractionRoute::List => handle_interaction_list(&self.store),
            InteractionRoute::Clear => handle_interaction_clear(&self.store),
            InteractionRoute::NotFound => return None,
        })
    }

    fn load_yaml_config(&self, _root: &serde_yaml::Value) {}

    fn load_env_config(&self) {}
}
