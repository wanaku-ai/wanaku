use std::collections::HashMap;
use std::sync::Arc;

use praxis_core::config::Config;
use praxis_filter::{FilterPipeline, FilterRegistry, PipelineExtension, RequestExtensions};
use praxis_protocol::ListenerPipelines;
use tracing::info;

use wanaku_apis::registry::InMemoryRegistry;
use wanaku_types::feature::Feature;

/// Dependencies needed to build filter pipelines for all listeners.
///
/// Groups the Praxis infrastructure registries and Wanaku-specific
/// extensions so that [`resolve_pipelines`] receives a single context
/// instead of many individual references.
pub struct PipelineDeps<'a> {
    /// Praxis filter registry (all registered HTTP filters).
    pub filter_registry: &'a FilterRegistry,
    /// Health-check registry for liveness/readiness probes.
    pub health_registry: &'a praxis_core::health::HealthRegistry,
    /// Key-value store registry for filter state.
    pub kv_stores: &'a praxis_core::kv::KvStoreRegistry,
    /// Wanaku in-memory registry (tools, resources, prompts, namespaces, forwards).
    pub wanaku_registry: &'a InMemoryRegistry,
    /// Wanaku feature crates that provide pipeline extensions and filters.
    pub features: &'a [Box<dyn Feature>],
}

impl<'a> PipelineDeps<'a> {
    /// Creates pipeline dependencies from all required registries and features.
    #[must_use]
    pub fn new(
        filter_registry: &'a FilterRegistry,
        health_registry: &'a praxis_core::health::HealthRegistry,
        kv_stores: &'a praxis_core::kv::KvStoreRegistry,
        wanaku_registry: &'a InMemoryRegistry,
        features: &'a [Box<dyn Feature>],
    ) -> Self {
        Self { filter_registry, health_registry, kv_stores, wanaku_registry, features }
    }
}

struct RegistryExtension {
    registry: InMemoryRegistry,
}

impl PipelineExtension for RegistryExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.registry.clone());
    }
}

/// Build filter pipelines for all listeners, injecting wanaku extensions.
///
/// # Errors
///
/// Returns an error if pipeline construction fails.
#[expect(clippy::too_many_lines, reason = "pipeline construction requires all dependencies")]
pub fn resolve_pipelines(
    config: &Config,
    deps: &PipelineDeps<'_>,
) -> Result<ListenerPipelines, Box<dyn std::error::Error + Send + Sync>> {
    let chains: HashMap<&str, &[_]> = config
        .filter_chains
        .iter()
        .map(|c| (c.name.as_str(), c.filters.as_slice()))
        .collect();

    let mut pipelines = HashMap::with_capacity(config.listeners.len());

    for listener in &config.listeners {
        let mut entries = Vec::new();
        for chain_name in &listener.filter_chains {
            let chain_filters = chains.get(chain_name.as_str()).ok_or_else(|| {
                format!(
                    "unknown chain '{}' for listener '{}'",
                    chain_name, listener.name
                )
            })?;
            entries.extend_from_slice(chain_filters);
        }

        let mut pipeline = FilterPipeline::build_with_chains(&mut entries, deps.filter_registry, &chains)?;

        pipeline.apply_body_limits(
            config.body_limits.max_request_bytes,
            config.body_limits.max_response_bytes,
            config.insecure_options.allow_unbounded_body,
        )?;

        if !deps.health_registry.is_empty() {
            pipeline.set_health_registry(Arc::clone(deps.health_registry));
        }

        if !deps.kv_stores.is_empty() {
            pipeline.set_kv_stores(deps.kv_stores.clone());
        }

        pipeline.add_pipeline_extension(Box::new(RegistryExtension {
            registry: deps.wanaku_registry.clone(),
        }));

        for feature in deps.features {
            for ext in feature.pipeline_extensions() {
                pipeline.add_pipeline_extension(ext);
            }
        }

        pipeline.apply_insecure_options(&config.insecure_options);

        info!(listener = %listener.name, "built wanaku pipeline");
        pipelines.insert(listener.name.clone(), Arc::new(pipeline));
    }

    Ok(ListenerPipelines::new(pipelines))
}
