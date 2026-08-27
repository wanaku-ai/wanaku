#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};

use wanaku_infra::metrics::MetricsStore;
use wanaku_types::feature::{Feature, HttpContext};

pub struct MetricsFeature {
    store: MetricsStore,
}

impl MetricsFeature {
    #[must_use]
    pub const fn new(store: MetricsStore) -> Self {
        Self { store }
    }
}

struct MetricsExtension {
    store: MetricsStore,
}

impl PipelineExtension for MetricsExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.store.clone());
    }
}

#[async_trait::async_trait]
impl Feature for MetricsFeature {
    fn name(&self) -> &'static str {
        "metrics"
    }

    fn register_filters(&self, _registry: &mut FilterRegistry) {}

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        vec![Box::new(MetricsExtension {
            store: self.store.clone(),
        })]
    }

    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        if ctx.method != "GET" || ctx.path != "/api/v1/metrics" {
            return None;
        }
        let snapshot = self.store.snapshot();
        Some(wanaku_types::http_response::json_ok(
            &serde_json::json!(snapshot),
        ))
    }

    fn load_yaml_config(&self, _root: &serde_yaml::Value) {}

    fn load_env_config(&self) {}
}
