use crate::{SimulationDeps, SimulationLimits};
use wanaku_infra::registry::RuntimeRegistry;
use wanaku_types::registry::{
    BindingRegistry, ForwardRegistry, NamespaceRegistry, PromptRegistry, ResourceRegistry,
    ToolRegistry,
};
pub(crate) fn capture(
    deps: &SimulationDeps,
    limits: &SimulationLimits,
) -> Result<SimulationDeps, &'static str> {
    check_counts(deps, limits)?;
    let tools = deps.registry.list_tools();
    let resources = deps.registry.list_resources();
    let prompts = deps.registry.list_prompts();
    let namespaces = deps.registry.list_namespaces();
    let forwards = deps.registry.list_forwards();
    let bindings = deps.registry.list_bindings();
    let entries = RegistryEntries {
        tools,
        resources,
        prompts,
        namespaces,
        forwards,
        bindings,
    };
    if entries.len() > limits.max_registry_entries {
        return Err("registry_snapshot_limit");
    }
    let registry = copy_registry(entries)?;
    Ok(SimulationDeps {
        registry,
        ..deps.clone()
    })
}

struct RegistryEntries {
    tools: Vec<wanaku_types::registry::ToolEntry>,
    resources: Vec<wanaku_types::registry::ResourceEntry>,
    prompts: Vec<wanaku_types::registry::PromptEntry>,
    namespaces: Vec<wanaku_types::registry::NamespaceEntry>,
    forwards: Vec<wanaku_types::registry::ForwardEntry>,
    bindings: Vec<wanaku_types::credentials::CredentialBinding>,
}
impl RegistryEntries {
    fn len(&self) -> usize {
        [
            self.tools.len(),
            self.resources.len(),
            self.prompts.len(),
            self.namespaces.len(),
            self.forwards.len(),
            self.bindings.len(),
        ]
        .into_iter()
        .fold(0_usize, usize::saturating_add)
    }
}
fn copy_registry(e: RegistryEntries) -> Result<RuntimeRegistry, &'static str> {
    let registry = RuntimeRegistry::new();
    for ns in e.namespaces.iter().filter(|entry| !entry.is_virtual()) {
        registry
            .try_register_namespace(ns.clone())
            .map_err(|_| "registry_snapshot_invalid")?;
    }
    registry.register_tools_batch(e.tools);
    registry.register_resources_batch(e.resources);
    registry.register_prompts_batch(e.prompts);
    for f in e.forwards {
        registry
            .try_register_forward(f)
            .map_err(|_| "registry_snapshot_invalid")?;
    }
    for ns in e
        .namespaces
        .into_iter()
        .filter(wanaku_types::registry::NamespaceEntry::is_virtual)
    {
        registry
            .try_register_namespace(ns)
            .map_err(|_| "registry_snapshot_invalid")?;
    }
    for b in e.bindings {
        registry.register_binding(b);
    }
    Ok(registry)
}

fn check_counts(deps: &SimulationDeps, limits: &SimulationLimits) -> Result<(), &'static str> {
    let known = deps
        .registry
        .tool_count()
        .saturating_add(deps.registry.resource_count())
        .saturating_add(deps.registry.prompt_count())
        .saturating_add(deps.registry.binding_count());
    if known > limits.max_registry_entries {
        return Err("registry_snapshot_limit");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn virtual_members_survive_copy_regardless_of_namespace_order() {
        let namespaces = serde_json::from_value(serde_json::json!([
            {"name":"integration","members":["kafka"]}, {"name":"kafka"}
        ]))
        .unwrap();
        let tools = serde_json::from_value(serde_json::json!([
            {"name":"health-check","description":"health","uri":"health-check","type":"mcp-forward","inputSchema":{},"namespace":"kafka"}
        ])).unwrap();
        let registry = copy_registry(RegistryEntries {
            namespaces,
            tools,
            resources: vec![],
            prompts: vec![],
            forwards: vec![],
            bindings: vec![],
        })
        .unwrap();
        let tools = registry.list_tools_in_namespace("virtual-ns/integration");
        assert_eq!(tools.len(), 1);
        assert_eq!(tools[0].name, "kafka/health-check");
        assert_eq!(tools[0].namespace.as_deref(), Some("kafka"));
    }
}
