#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

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

fn apply_inference_config(yaml: &mut Value, env: &wanaku_types::config::WanakuEnv) {
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

fn apply_inference_host_header(yaml: &mut Value, env: &wanaku_types::config::WanakuEnv) {
    if let Err(reason) = try_apply_inference_host_header(yaml, env) {
        tracing::warn!(reason, "inference proxy Host header override skipped");
    }
}

fn try_apply_inference_host_header(yaml: &mut Value, env: &wanaku_types::config::WanakuEnv) -> Result<(), &'static str> {
    let chains = yaml
        .get_mut("filter_chains")
        .and_then(Value::as_sequence_mut)
        .ok_or("no filter_chains in pipeline config")?;
    let chain = find_named_entry_mut(chains, "name", "inference_proxy").ok_or("inference_proxy chain not found")?;
    let filters = chain
        .get_mut("filters")
        .and_then(Value::as_sequence_mut)
        .ok_or("chain has no filters")?;
    let headers = find_named_entry_mut(filters, "filter", "headers").ok_or("no headers filter on chain")?;
    let request_set = headers
        .get_mut("request_set")
        .and_then(Value::as_sequence_mut)
        .ok_or("headers filter has no request_set")?;
    let host_entry = find_named_entry_mut(request_set, "name", "Host").ok_or("no Host entry in request_set")?;
    let value = host_entry.get_mut("value").ok_or("Host entry has no value field")?;
    *value = Value::String(inference_host_header_value(env));
    Ok(())
}

/// The `Host` sent upstream must match what the inference backend expects,
/// not the browser's original request to Wanaku — reverse proxies that route
/// by hostname (e.g. TLS-terminating ingresses) reject a mismatched Host.
///
/// For the default HTTPS port, `inference_tls_sni` (a bare hostname) is
/// used as-is, matching how a client omitting an explicit Host would behave.
/// For any other port — including plain (non-TLS) upstreams — the full
/// `host:port` is required, since a bare hostname would silently drop it.
fn inference_host_header_value(env: &wanaku_types::config::WanakuEnv) -> String {
    match &env.inference_tls_sni {
        Some(hostname) if env.inference_upstream.ends_with(":443") => hostname.clone(),
        _ => env.inference_upstream.clone(),
    }
}

fn apply_inference_path_prefix(yaml: &mut Value, env: &wanaku_types::config::WanakuEnv) {
    if let Err(reason) = try_apply_inference_path_prefix(yaml, env) {
        tracing::warn!(reason, "inference proxy path prefix override skipped");
    }
}

/// A path component in `WANAKU_INFERENCE_UPSTREAM` (e.g. `/api` in
/// `https://host/api`) must be prepended to every request forwarded
/// upstream — the load-balancer only routes by host:port, so this path
/// would otherwise be silently dropped.
fn try_apply_inference_path_prefix(yaml: &mut Value, env: &wanaku_types::config::WanakuEnv) -> Result<(), &'static str> {
    let chains = yaml
        .get_mut("filter_chains")
        .and_then(Value::as_sequence_mut)
        .ok_or("no filter_chains in pipeline config")?;
    let chain = find_named_entry_mut(chains, "name", "inference_proxy").ok_or("inference_proxy chain not found")?;
    let filters = chain
        .get_mut("filters")
        .and_then(Value::as_sequence_mut)
        .ok_or("chain has no filters")?;
    let path_rewrite =
        find_named_entry_mut(filters, "filter", "path_rewrite").ok_or("no path_rewrite filter on chain")?;
    let add_prefix = path_rewrite.get_mut("add_prefix").ok_or("path_rewrite filter has no add_prefix")?;
    *add_prefix = Value::String(env.inference_path_prefix.clone());
    Ok(())
}

fn apply_cors_config(yaml: &mut Value, env: &wanaku_types::config::WanakuEnv) {
    for chain_name in ["mcp_router", "inference_proxy"] {
        apply_cors_to_chain(yaml, chain_name, &env.cors_origin);
    }
}

fn apply_cors_to_chain(yaml: &mut Value, chain_name: &str, origin: &str) {
    if let Err(reason) = try_apply_cors_to_chain(yaml, chain_name, origin) {
        tracing::warn!(chain = chain_name, reason, "WANAKU_CORS_ORIGIN override skipped");
    }
}

fn try_apply_cors_to_chain(
    yaml: &mut Value,
    chain_name: &str,
    origin: &str,
) -> Result<(), &'static str> {
    let chains = yaml
        .get_mut("filter_chains")
        .and_then(Value::as_sequence_mut)
        .ok_or("no filter_chains in pipeline config")?;
    let chain = find_named_entry_mut(chains, "name", chain_name).ok_or("chain not found")?;
    let filters = chain
        .get_mut("filters")
        .and_then(Value::as_sequence_mut)
        .ok_or("chain has no filters")?;
    let cors = find_named_entry_mut(filters, "filter", "cors").ok_or("no cors filter on chain")?;
    let origins = cors
        .get_mut("allow_origins")
        .and_then(Value::as_sequence_mut)
        .ok_or("cors filter has no allow_origins")?;
    let first = origins.first_mut().ok_or("cors filter's allow_origins is empty")?;
    *first = Value::String(origin.to_owned());
    Ok(())
}

/// Load configuration, falling back to the built-in default.
///
/// # Errors
///
/// Returns an error if the config cannot be loaded or is invalid.
pub fn load_config(
    explicit_path: Option<&str>,
) -> Result<praxis_core::config::Config, praxis_core::errors::ProxyError> {
    let env = &wanaku_types::config::ENV;

    let config = match serde_yaml::from_str::<Value>(DEFAULT_CONFIG) {
        Ok(mut yaml) => {
            apply_inference_config(&mut yaml, env);
            apply_inference_host_header(&mut yaml, env);
            apply_inference_path_prefix(&mut yaml, env);
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

#[cfg(test)]
mod tests {
    use wanaku_types::config::WanakuEnv;

    use super::{Value, inference_host_header_value, try_apply_inference_host_header, try_apply_inference_path_prefix};

    fn env_with(upstream: &str, tls_sni: Option<&str>) -> WanakuEnv {
        env_with_path(upstream, tls_sni, "")
    }

    fn env_with_path(upstream: &str, tls_sni: Option<&str>, path_prefix: &str) -> WanakuEnv {
        WanakuEnv {
            mgmt_listen: "0.0.0.0:8080".to_owned(),
            inference_upstream: upstream.to_owned(),
            inference_path_prefix: path_prefix.to_owned(),
            inference_tls_sni: tls_sni.map(ToOwned::to_owned),
            persist: None,
            ui_path: None,
            cors_origin: "*".to_owned(),
            forward_headers: Vec::new(),
            forward_healthcheck_interval: Some(std::time::Duration::from_secs(30)),
        }
    }

    #[test]
    fn host_header_default_tls_port_uses_bare_hostname() {
        let env = env_with("api.example.com:443", Some("api.example.com"));
        assert_eq!(inference_host_header_value(&env), "api.example.com");
    }

    #[test]
    fn host_header_non_default_tls_port_keeps_port() {
        let env = env_with("api.example.com:8443", Some("api.example.com"));
        assert_eq!(inference_host_header_value(&env), "api.example.com:8443");
    }

    #[test]
    fn host_header_plain_upstream_keeps_port() {
        let env = env_with("127.0.0.1:11434", None);
        assert_eq!(inference_host_header_value(&env), "127.0.0.1:11434");
    }

    #[test]
    fn action_policy_runs_before_evaluator() {
        let yaml: Value =
            serde_yaml::from_str(super::DEFAULT_CONFIG).expect("default.yaml must parse");
        let chains = yaml["filter_chains"]
            .as_sequence()
            .expect("filter chains");
        let router = chains
            .iter()
            .find(|chain| chain["name"].as_str() == Some("mcp_router"))
            .expect("MCP router");
        let filters: Vec<&str> = router["filters"]
            .as_sequence()
            .expect("filters")
            .iter()
            .filter_map(|entry| entry["filter"].as_str())
            .collect();
        let policy = filters
            .iter()
            .position(|name| *name == "wanaku_action_policy")
            .expect("action-policy filter");
        let init = filters
            .iter()
            .position(|name| *name == "wanaku_mcp_init")
            .expect("MCP init filter");
        let evaluator = filters
            .iter()
            .position(|name| *name == "wanaku_evaluator")
            .expect("evaluator filter");
        assert!(init < policy && policy < evaluator);
    }

    /// Guards against the `headers`/`request_set`/`Host` lookup silently
    /// drifting out of sync with `default.yaml` (e.g. a filter rename or
    /// reorder) and falling back to the unpatched placeholder value.
    #[test]
    fn try_apply_inference_host_header_matches_embedded_default_config() {
        let mut yaml: Value = serde_yaml::from_str(super::DEFAULT_CONFIG).expect("default.yaml must parse");
        let env = env_with("upstream.internal:9999", None);
        try_apply_inference_host_header(&mut yaml, &env).expect("headers filter must be found in default.yaml");

        let patched = serde_yaml::to_string(&yaml).expect("patched yaml must serialize");
        assert!(patched.contains("upstream.internal:9999"), "Host value was not patched:\n{patched}");
    }

    /// Guards against the `path_rewrite`/`add_prefix` lookup silently
    /// drifting out of sync with `default.yaml`, which would leave a
    /// configured upstream path (e.g. `/api`) silently dropped.
    #[test]
    fn try_apply_inference_path_prefix_matches_embedded_default_config() {
        let mut yaml: Value = serde_yaml::from_str(super::DEFAULT_CONFIG).expect("default.yaml must parse");
        let env = env_with_path("openrouter.ai:443", Some("openrouter.ai"), "/api");
        try_apply_inference_path_prefix(&mut yaml, &env).expect("path_rewrite filter must be found in default.yaml");

        let patched = serde_yaml::to_string(&yaml).expect("patched yaml must serialize");
        assert!(patched.contains("add_prefix: /api"), "path prefix was not patched:\n{patched}");
    }
}
