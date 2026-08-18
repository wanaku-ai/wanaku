
pub mod http_response;
pub mod management;
pub mod openapi;
pub mod pipelines;

const DEFAULT_CONFIG: &str = include_str!("default.yaml");

/// Load configuration, falling back to `praxis.yaml` then the built-in default.
///
/// # Errors
///
/// Returns an error if the config cannot be loaded or is invalid.
pub fn load_config(
    explicit_path: Option<&str>,
) -> Result<praxis_core::config::Config, praxis_core::errors::ProxyError> {
    let env = &wanaku_praxis_apis::config::ENV;
    let mut config = DEFAULT_CONFIG.replace("127.0.0.1:11434", &env.inference_upstream);
    if let Some(sni) = &env.inference_tls_sni {
        config = config.replace(
            "- name: inference\n            endpoints:",
            &format!("- name: inference\n            tls:\n              sni: \"{sni}\"\n            endpoints:"),
        );
    }
    praxis_core::config::Config::load(explicit_path, &config)
}

/// Build a filter registry with praxis builtins, praxis-ai MCP, and wanaku filters.
#[must_use]
pub fn build_full_registry() -> praxis_filter::FilterRegistry {
    let mut registry = praxis_filter::FilterRegistry::with_builtins();
    register_wanaku_filters(&mut registry);
    registry
}

fn register_wanaku_filters(registry: &mut praxis_filter::FilterRegistry) {
    praxis_filter::register_filters!(
        @register registry,
        http "mcp" => praxis_ai_filters::McpFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_namespace" => wanaku_praxis_filters::NamespaceFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_mcp_init" => wanaku_praxis_filters::McpInitFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_tool_list" => wanaku_praxis_filters::ToolListFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_tool_call" => wanaku_praxis_filters::ToolCallFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_resource_list" => wanaku_praxis_filters::ResourceListFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_resource_read" => wanaku_praxis_filters::ResourceReadFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_prompt_list" => wanaku_praxis_filters::PromptListFilter::from_config
    );
    praxis_filter::register_filters!(
        @register registry,
        http "wanaku_prompt_get" => wanaku_praxis_filters::PromptGetFilter::from_config
    );
}
