#![deny(unsafe_code)]

pub mod filter;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};

use wanaku_praxis_apis::feature::Feature;

const WANAKU_AUTH_ISSUER: &str = "WANAKU_AUTH_ISSUER";

#[derive(Clone)]
pub struct IssuerConfig {
    pub issuer: String,
}

struct IssuerConfigExtension {
    config: IssuerConfig,
}

impl PipelineExtension for IssuerConfigExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.config.clone());
    }
}

pub struct McpMetadataFeature;

impl McpMetadataFeature {
    #[must_use]
    pub fn new() -> Self {
        Self
    }
}

#[async_trait::async_trait]
impl Feature for McpMetadataFeature {
    fn name(&self) -> &'static str {
        "mcp-metadata"
    }

    fn register_filters(&self, registry: &mut FilterRegistry) {
        praxis_filter::register_filters!(
            @register registry,
            http "wanaku_well_known" => crate::filter::WellKnownFilter::from_config
        );
    }

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        let issuer = std::env::var(WANAKU_AUTH_ISSUER).unwrap_or_default();
        vec![Box::new(IssuerConfigExtension {
            config: IssuerConfig { issuer },
        })]
    }

    async fn handle_route(
        &self,
        _method: &str,
        _path: &str,
        _query: Option<&str>,
        _body: Option<&str>,
    ) -> Option<Response<Vec<u8>>> {
        None
    }

    fn load_yaml_config(&self, _root: &serde_yaml::Value) {}

    fn load_env_config(&self) {
        if let Ok(issuer) = std::env::var(WANAKU_AUTH_ISSUER) {
            if !issuer.is_empty() {
                tracing::info!(issuer = %issuer, "MCP metadata configured with auth issuer");
            }
        }
    }
}
