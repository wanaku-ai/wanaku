use crate::{SimulationDeps, SimulationLimits};
use wanaku_infra::registry::InMemoryRegistry;
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
    let registry = copy_registry(entries);
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
fn copy_registry(e: RegistryEntries) -> InMemoryRegistry {
    let registry = InMemoryRegistry::new();
    registry.register_tools_batch(e.tools);
    registry.register_resources_batch(e.resources);
    registry.register_prompts_batch(e.prompts);
    for ns in e.namespaces {
        registry.register_namespace(ns);
    }
    for f in e.forwards {
        registry.register_forward(f);
    }
    for b in e.bindings {
        registry.register_binding(b);
    }
    registry
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
