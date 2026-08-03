use std::collections::HashMap;
use std::sync::Arc;

use praxis_core::config::Config;
use praxis_filter::{FilterPipeline, FilterRegistry, PipelineExtension, RequestExtensions};
use praxis_protocol::ListenerPipelines;
use tracing::info;

use wanaku_praxis_apis::grpc::GrpcPool;
use wanaku_praxis_apis::interactions::InMemoryInteractionStore;
use wanaku_praxis_apis::registry::InMemoryRegistry;

/// Pipeline extension that injects the tool/service registry into each request.
struct RegistryExtension {
    registry: InMemoryRegistry,
}

impl PipelineExtension for RegistryExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.registry.clone());
    }
}

/// Pipeline extension that injects the gRPC connection pool into each request.
struct GrpcPoolExtension {
    pool: GrpcPool,
}

impl PipelineExtension for GrpcPoolExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.pool.clone());
    }
}

/// Pipeline extension that injects the interaction store into each request.
struct InteractionStoreExtension {
    store: InMemoryInteractionStore,
}

impl PipelineExtension for InteractionStoreExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.store.clone());
    }
}

/// Build filter pipelines for all listeners, injecting wanaku extensions.
///
/// # Errors
///
/// Returns an error if pipeline construction fails.
pub fn resolve_pipelines(
    config: &Config,
    registry: &FilterRegistry,
    health_registry: &praxis_core::health::HealthRegistry,
    kv_stores: &praxis_core::kv::KvStoreRegistry,
    wanaku_registry: InMemoryRegistry,
    grpc_pool: GrpcPool,
    interaction_store: InMemoryInteractionStore,
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

        let mut pipeline = FilterPipeline::build_with_chains(&mut entries, registry, &chains)?;

        pipeline.apply_body_limits(
            config.body_limits.max_request_bytes,
            config.body_limits.max_response_bytes,
            config.insecure_options.allow_unbounded_body,
        )?;

        if !health_registry.is_empty() {
            pipeline.set_health_registry(Arc::clone(health_registry));
        }

        if !kv_stores.is_empty() {
            pipeline.set_kv_stores(kv_stores.clone());
        }

        pipeline.add_pipeline_extension(Box::new(RegistryExtension {
            registry: wanaku_registry.clone(),
        }));
        pipeline.add_pipeline_extension(Box::new(GrpcPoolExtension {
            pool: grpc_pool.clone(),
        }));
        pipeline.add_pipeline_extension(Box::new(InteractionStoreExtension {
            store: interaction_store.clone(),
        }));
        pipeline.apply_insecure_options(&config.insecure_options);

        info!(listener = %listener.name, "built wanaku pipeline");
        pipelines.insert(listener.name.clone(), Arc::new(pipeline));
    }

    Ok(ListenerPipelines::new(pipelines))
}
