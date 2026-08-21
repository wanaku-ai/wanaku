
pub mod http_response;
pub mod management;
pub mod openapi;
pub mod pipelines;

use serde_yaml::Value;

const DEFAULT_CONFIG: &str = include_str!("default.yaml");

fn find_named_entry_mut<'a>(
    sequence: &'a mut [Value],
    key: &str,
    name: &str,
) -> Option<&'a mut Value> {
    sequence.iter_mut().find(|entry| {
        entry
            .get(key)
            .and_then(Value::as_str)
            .is_some_and(|n| n == name)
    })
}

fn find_inference_cluster(yaml: &mut Value) -> Option<&mut Value> {
    let chains = yaml.get_mut("filter_chains").and_then(Value::as_sequence_mut)?;
    let chain = find_named_entry_mut(chains, "name", "inference_proxy")?;
    let filters = chain.get_mut("filters").and_then(Value::as_sequence_mut)?;
    let lb = find_named_entry_mut(filters, "filter", "load_balancer")?;
    let clusters = lb.get_mut("clusters").and_then(Value::as_sequence_mut)?;
    find_named_entry_mut(clusters, "name", "inference")
}

fn apply_inference_config(yaml: &mut Value, env: &wanaku_apis::config::WanakuEnv) {
    let Some(cluster) = find_inference_cluster(yaml) else {
        tracing::warn!("could not locate inference cluster in pipeline config — env overrides skipped");
        return;
    };

    if let Some(endpoint) = cluster
        .get_mut("endpoints")
        .and_then(Value::as_sequence_mut)
        .and_then(|eps| eps.first_mut())
    {
        *endpoint = Value::String(env.inference_upstream.clone());
    }

    if let Some(sni) = &env.inference_tls_sni
        && let Some(mapping) = cluster.as_mapping_mut()
    {
        let mut tls = serde_yaml::Mapping::new();
        tls.insert(Value::String("sni".into()), Value::String(sni.clone()));
        mapping.insert(Value::String("tls".into()), Value::Mapping(tls));
    }
}

fn apply_cors_config(yaml: &mut Value, env: &wanaku_apis::config::WanakuEnv) {
    let Some(chains) = yaml.get_mut("filter_chains").and_then(Value::as_sequence_mut) else {
        return;
    };
    let Some(chain) = find_named_entry_mut(chains, "name", "mcp_router") else {
        return;
    };
    let Some(filters) = chain.get_mut("filters").and_then(Value::as_sequence_mut) else {
        return;
    };
    let Some(cors) = find_named_entry_mut(filters, "filter", "cors") else {
        return;
    };
    if let Some(origins) = cors.get_mut("allow_origins").and_then(Value::as_sequence_mut) {
        if let Some(first) = origins.first_mut() {
            *first = Value::String(env.cors_origin.clone());
        }
    }
}

/// Load configuration, falling back to the built-in default.
///
/// # Errors
///
/// Returns an error if the config cannot be loaded or is invalid.
pub fn load_config(
    explicit_path: Option<&str>,
) -> Result<praxis_core::config::Config, praxis_core::errors::ProxyError> {
    let env = &wanaku_apis::config::ENV;

    let config = match serde_yaml::from_str::<Value>(DEFAULT_CONFIG) {
        Ok(mut yaml) => {
            apply_inference_config(&mut yaml, env);
            apply_cors_config(&mut yaml, env);
            match serde_yaml::to_string(&yaml) {
                Ok(s) => s,
                Err(e) => {
                    tracing::error!(error = %e, "failed to serialize modified config — using raw defaults");
                    DEFAULT_CONFIG.to_string()
                }
            }
        }
        Err(e) => {
            tracing::error!(error = %e, "failed to parse embedded config — using raw defaults");
            DEFAULT_CONFIG.to_string()
        }
    };

    praxis_core::config::Config::load(explicit_path, &config)
}

/// Build a filter registry with builtins, MCP, and wanaku filters.
#[must_use]
pub fn build_full_registry() -> praxis_filter::FilterRegistry {
    let mut registry = praxis_filter::FilterRegistry::with_builtins();
    register_wanaku_filters(&mut registry);
    registry
}

#[expect(clippy::too_many_lines, reason = "filter registration is sequential and repetitive")]
fn register_wanaku_filters(registry: &mut praxis_filter::FilterRegistry) {
    praxis_filter::register_filters!(
        @register registry,
        http "mcp" => praxis_ai_filters::McpFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_mcp_id" => wanaku_filters::McpIdFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_namespace" => wanaku_filters::NamespaceFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_mcp_init" => wanaku_filters::McpInitFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_tool_list" => wanaku_filters::ToolListFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_tool_call" => wanaku_filters::ToolCallFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_resource_list" => wanaku_filters::ResourceListFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_resource_read" => wanaku_filters::ResourceReadFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_prompt_list" => wanaku_filters::PromptListFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_prompt_get" => wanaku_filters::PromptGetFilter::from_config
    );
}
