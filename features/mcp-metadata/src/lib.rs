#![deny(unsafe_code)]

pub mod filter;

use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};

use wanaku_types::feature::{Feature, HttpContext};

const WANAKU_AUTH_ISSUER: &str = "WANAKU_AUTH_ISSUER";
const WANAKU_AUTH_UPSTREAM_ISSUER: &str = "WANAKU_AUTH_UPSTREAM_ISSUER";

#[derive(Clone)]
pub struct IssuerConfig {
    pub issuer: String,
    pub upstream_issuer: String,
}

impl IssuerConfig {
    fn new(issuer: String, upstream_issuer: Option<String>) -> Self {
        let upstream_issuer = upstream_issuer
            .filter(|value| !value.is_empty())
            .unwrap_or_else(|| issuer.clone());
        Self {
            issuer,
            upstream_issuer,
        }
    }
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
    pub const fn new() -> Self {
        Self
    }
}

impl Default for McpMetadataFeature {
    fn default() -> Self {
        Self::new()
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
            config: IssuerConfig::new(issuer, std::env::var(WANAKU_AUTH_UPSTREAM_ISSUER).ok()),
        })]
    }

    async fn handle_route(&self, _ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        None
    }

    fn load_yaml_config(&self, _root: &serde_yaml::Value) {}

    fn load_env_config(&self) {
        if let Ok(issuer) = std::env::var(WANAKU_AUTH_ISSUER)
            && !issuer.is_empty()
        {
            tracing::info!(issuer = %issuer, "MCP metadata configured with auth issuer");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::IssuerConfig;

    #[test]
    fn upstream_defaults_to_public_issuer() {
        for upstream in [None, Some(String::new())] {
            let config = IssuerConfig::new("http://localhost:8543/realms/wanaku".into(), upstream);
            assert_eq!(config.upstream_issuer, config.issuer);
        }
    }

    #[test]
    fn upstream_does_not_change_public_issuer() {
        let config = IssuerConfig::new(
            "http://localhost:8543/realms/wanaku".into(),
            Some("http://keycloak:8080/realms/wanaku".into()),
        );
        assert_eq!(config.issuer, "http://localhost:8543/realms/wanaku");
        assert_eq!(config.upstream_issuer, "http://keycloak:8080/realms/wanaku");
    }
}
