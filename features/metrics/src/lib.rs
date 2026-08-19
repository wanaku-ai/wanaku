#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};

use wanaku_apis::feature::Feature;
use wanaku_apis::metrics::MetricsStore;

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

    async fn handle_route(
        &self,
        method: &str,
        path: &str,
        _query: Option<&str>,
        _body: Option<&str>,
        _headers: &http::HeaderMap,
    ) -> Option<Response<Vec<u8>>> {
        if method != "GET" || path != "/api/v1/metrics" {
            return None;
        }
        let snapshot = self.store.snapshot();
        Some(wanaku_apis::http_response::json_ok(
            &serde_json::json!(snapshot),
        ))
    }

    fn load_yaml_config(&self, _root: &serde_yaml::Value) {}

    fn load_env_config(&self) {}
}
