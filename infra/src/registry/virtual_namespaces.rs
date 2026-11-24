use std::collections::{HashMap, HashSet};
use std::sync::MutexGuard;

use wanaku_types::persistence::RegistrySnapshot;
use wanaku_types::registry::{
    DEFAULT_NAMESPACE, ForwardEntry, NamespaceEntry, NamespaceRegistry, PromptEntry,
    PromptRegistry, ResourceEntry, ResourceRegistry, ToolEntry, ToolRegistry,
    validate_namespace_name,
};

use super::{ForwardChanges, InMemoryRegistry};

impl InMemoryRegistry {
    pub(super) fn lock_namespaces(&self) -> MutexGuard<'_, ForwardChanges> {
        self.namespace_lock
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
    }

    pub fn validate_concrete_namespace(&self, name: &str) -> Result<(), String> {
        validate_namespace_name(name)?;
        match self.get_namespace(name) {
            Some(namespace) if namespace.is_virtual() => {
                Err(format!("virtual namespace {name} cannot own entries"))
            }
            _ => Ok(()),
        }
    }

    pub(super) fn accept_entry_namespace(&self, namespace: Option<&str>) -> bool {
        match self.validate_concrete_namespace(namespace.unwrap_or(DEFAULT_NAMESPACE)) {
            Ok(()) => true,
            Err(error) => {
                tracing::warn!(%error, "entry registration rejected");
                false
            }
        }
    }

    pub fn resolve_virtual_member(&self, name: &str, member: &str) -> bool {
        self.get_namespace(name)
            .and_then(|entry| entry.members)
            .is_some_and(|members| members.iter().any(|name| name == member))
            && self
                .get_namespace(member)
                .is_some_and(|entry| !entry.is_virtual())
    }

    pub fn virtual_members(&self, name: &str) -> Vec<String> {
        self.get_namespace(name)
            .and_then(|entry| entry.members)
            .unwrap_or_default()
            .into_iter()
            .filter(|member| {
                self.get_namespace(member)
                    .is_some_and(|entry| !entry.is_virtual())
            })
            .collect()
    }

    pub fn try_register_namespace(&self, namespace: NamespaceEntry) -> Result<(), String> {
        let _guard = self.lock_namespaces();
        self.validate_namespace_entry(&namespace)?;
        self.namespaces.insert(namespace.name.clone(), namespace);
        self.persist();
        Ok(())
    }

    fn validate_namespace_entry(&self, namespace: &NamespaceEntry) -> Result<(), String> {
        validate_namespace_name(&namespace.name)?;
        let Some(members) = &namespace.members else {
            return Ok(());
        };
        if namespace.name == DEFAULT_NAMESPACE {
            return Err("the default namespace must remain concrete".to_owned());
        }
        if self.namespace_is_referenced(&namespace.name) {
            return Err(format!(
                "namespace {} is a member of a virtual namespace",
                namespace.name
            ));
        }
        if self.namespace_has_entries(&namespace.name) {
            return Err(format!(
                "namespace {} owns entries; remove them before making it virtual",
                namespace.name
            ));
        }
        let mut unique = HashSet::new();
        for member in members {
            validate_namespace_name(member)?;
            if member == &namespace.name {
                return Err("a virtual namespace cannot include itself".to_owned());
            }
            if !unique.insert(member) {
                return Err(format!("duplicate namespace member: {member}"));
            }
            match self.get_namespace(member) {
                Some(entry) if !entry.is_virtual() => {}
                _ => {
                    return Err(format!(
                        "member must be an existing concrete namespace: {member}"
                    ));
                }
            }
        }
        Ok(())
    }

    fn namespace_is_referenced(&self, name: &str) -> bool {
        self.namespaces.iter().any(|entry| {
            entry
                .members
                .as_ref()
                .is_some_and(|members| members.iter().any(|member| member == name))
        })
    }

    fn namespace_has_entries(&self, name: &str) -> bool {
        self.tools.iter().any(|entry| entry.key().0 == name)
            || self.resources.iter().any(|entry| entry.key().0 == name)
            || self.prompts.iter().any(|entry| entry.key().0 == name)
            || self
                .forwards
                .iter()
                .any(|entry| entry.namespace.as_deref().unwrap_or(DEFAULT_NAMESPACE) == name)
            || self
                .agents
                .iter()
                .any(|entry| entry.entry.namespace == name)
    }

    pub fn try_remove_namespace(&self, name: &str) -> Result<bool, String> {
        let _guard = self.lock_namespaces();
        if self.namespace_is_referenced(name) {
            return Err(format!(
                "namespace {name} is a member of a virtual namespace"
            ));
        }
        if self
            .agents
            .iter()
            .any(|agent| agent.entry.namespace == name)
        {
            return Err("namespace contains A2A agents; remove the agents first".to_owned());
        }
        let removed = self.namespaces.remove(name).is_some();
        if removed {
            self.persist();
        }
        Ok(removed)
    }

    pub fn try_register_forward(&self, forward: ForwardEntry) -> Result<(), String> {
        let mut changes = self.lock_namespaces();
        self.validate_concrete_namespace(
            forward.namespace.as_deref().unwrap_or(DEFAULT_NAMESPACE),
        )?;
        changes.forwards.remove(&forward.name);
        self.insert_forward(forward);
        self.persist();
        Ok(())
    }

    pub fn list_virtual_agents(&self, name: &str) -> Vec<wanaku_types::agents::AgentEntry> {
        let members = self.virtual_members(name);
        let mut agents: Vec<_> = self
            .list_agents()
            .into_iter()
            .filter(|agent| members.contains(&agent.namespace))
            .map(|mut agent| {
                agent.name = format!("{}/{}", agent.namespace, agent.name);
                agent
            })
            .collect();
        agents.sort_by(|a, b| a.name.cmp(&b.name));
        agents
    }

    pub(super) fn virtual_tools(&self, name: &str) -> Vec<ToolEntry> {
        let mut tools: Vec<_> = self
            .virtual_members(name)
            .into_iter()
            .flat_map(|member| {
                self.list_tools_in_namespace(&member)
                    .into_iter()
                    .map(move |mut tool| {
                        tool.name = format!("{member}/{}", tool.name);
                        tool
                    })
            })
            .collect();
        tools.sort_by(|a, b| a.name.cmp(&b.name));
        tools
    }

    pub(super) fn virtual_resources(&self, name: &str) -> Vec<ResourceEntry> {
        let mut resources: Vec<_> = self
            .virtual_members(name)
            .into_iter()
            .flat_map(|member| {
                self.list_resources_in_namespace(&member)
                    .into_iter()
                    .map(move |mut resource| {
                        resource.name = format!("{member}/{}", resource.name);
                        resource.location = format!("wanaku://{member}/{}", resource.location);
                        resource
                    })
            })
            .collect();
        resources.sort_by(|a, b| a.name.cmp(&b.name));
        resources
    }

    pub(super) fn virtual_prompts(&self, name: &str) -> Vec<PromptEntry> {
        let mut prompts: Vec<_> = self
            .virtual_members(name)
            .into_iter()
            .flat_map(|member| {
                self.list_prompts_in_namespace(&member)
                    .into_iter()
                    .map(move |mut prompt| {
                        prompt.name = format!("{member}/{}", prompt.name);
                        prompt
                    })
            })
            .collect();
        prompts.sort_by(|a, b| a.name.cmp(&b.name));
        prompts
    }

    pub(crate) fn validate_snapshot_namespaces(
        &self,
        snapshot: &RegistrySnapshot,
    ) -> Result<(), String> {
        let mut definitions: HashMap<String, NamespaceEntry> = self
            .list_namespaces()
            .into_iter()
            .map(|entry| (entry.name.clone(), entry))
            .collect();
        let mut seen = HashSet::new();
        for namespace in &snapshot.namespaces {
            validate_namespace_name(&namespace.name)?;
            if !seen.insert(&namespace.name) {
                return Err(format!("duplicate persisted namespace: {}", namespace.name));
            }
            definitions.insert(namespace.name.clone(), namespace.clone());
        }
        let probe = Self::new();
        for namespace in definitions.values().filter(|entry| !entry.is_virtual()) {
            probe.try_register_namespace(namespace.clone())?;
        }
        // Forwards and agents historically auto-create their concrete namespace.
        for namespace in snapshot
            .forwards
            .iter()
            .map(|entry| entry.namespace.as_deref().unwrap_or(DEFAULT_NAMESPACE))
            .chain(snapshot.agents.iter().map(|entry| entry.namespace.as_str()))
        {
            validate_namespace_name(namespace)?;
            if !definitions.contains_key(namespace) {
                probe.try_register_namespace(NamespaceEntry {
                    name: namespace.to_owned(),
                    members: None,
                    labels: HashMap::new(),
                    auth_required: None,
                    audience: None,
                })?;
            }
        }
        for namespace in definitions.values().filter(|entry| entry.is_virtual()) {
            probe.try_register_namespace(namespace.clone())?;
        }
        for namespace in snapshot
            .tools
            .iter()
            .map(|entry| entry.namespace.as_deref().unwrap_or(DEFAULT_NAMESPACE))
            .chain(
                snapshot
                    .resources
                    .iter()
                    .map(|entry| entry.namespace.as_deref().unwrap_or(DEFAULT_NAMESPACE)),
            )
            .chain(
                snapshot
                    .prompts
                    .iter()
                    .map(|entry| entry.namespace.as_deref().unwrap_or(DEFAULT_NAMESPACE)),
            )
            .chain(
                snapshot
                    .forwards
                    .iter()
                    .map(|entry| entry.namespace.as_deref().unwrap_or(DEFAULT_NAMESPACE)),
            )
            .chain(snapshot.agents.iter().map(|entry| entry.namespace.as_str()))
        {
            probe.validate_concrete_namespace(namespace)?;
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use wanaku_types::persistence::{PersistenceBackend, PersistenceError};
    use wanaku_types::registry::ForwardRegistry;

    type TestResult = Result<(), Box<dyn std::error::Error>>;

    fn namespace(name: &str, members: Option<&[&str]>) -> NamespaceEntry {
        NamespaceEntry {
            name: name.to_owned(),
            members: members.map(|members| members.iter().map(|name| (*name).to_owned()).collect()),
            labels: HashMap::new(),
            auth_required: None,
            audience: None,
        }
    }

    fn populated() -> Result<InMemoryRegistry, String> {
        let registry = InMemoryRegistry::new();
        registry.try_register_namespace(namespace("kafka", None))?;
        registry.try_register_namespace(namespace("flink", None))?;
        registry.try_register_namespace(namespace("integration", Some(&["kafka", "flink"])))?;
        Ok(registry)
    }

    #[test]
    fn virtual_inventory_qualifies_collisions_and_preserves_ownership() -> TestResult {
        let registry = populated()?;
        for name in ["kafka", "flink"] {
            let tool: ToolEntry = serde_json::from_value(serde_json::json!({
                "name":"health-check", "description":"health", "uri":"http://upstream/mcp",
                "type":"mcp-forward", "inputSchema":{}, "namespace":name, "forwardId":name
            }))?;
            let resource: ResourceEntry = serde_json::from_value(serde_json::json!({
                "name":"health-check", "location":"data://records/{id}",
                "type":"mcp-forward", "namespace":name, "forwardId":name
            }))?;
            let prompt: PromptEntry = serde_json::from_value(serde_json::json!({
                "name":"health-check", "namespace":name, "forwardId":name
            }))?;
            registry.register_tool(tool);
            registry.register_resource(resource);
            registry.register_prompt(prompt);
        }
        let tools = registry.list_tools_in_namespace("virtual-ns/integration");
        assert_eq!(
            tools
                .iter()
                .map(|entry| entry.name.as_str())
                .collect::<Vec<_>>(),
            vec!["flink/health-check", "kafka/health-check"]
        );
        assert_eq!(tools[0].namespace.as_deref(), Some("flink"));
        assert_eq!(tools[0].forward_id.as_deref(), Some("flink"));
        assert_eq!(
            registry.list_resources_in_namespace("virtual-ns/integration")[0].location,
            "wanaku://flink/data://records/{id}"
        );
        assert_eq!(
            registry.list_prompts_in_namespace("virtual-ns/integration")[1].name,
            "kafka/health-check"
        );
        assert!(registry.list_tools_in_namespace("integration").is_empty());
        assert!(registry.get_tool("flink", "health-check").is_some());
        assert!(registry.set_tool_enabled("flink", "health-check", false));
        assert_eq!(
            registry
                .list_tools_in_namespace("virtual-ns/integration")
                .len(),
            1
        );
        registry.remove_tools_owned_by("kafka");
        registry.remove_resources_owned_by("kafka");
        registry.remove_prompts_owned_by("kafka");
        assert!(
            registry
                .list_tools_in_namespace("virtual-ns/integration")
                .is_empty()
        );
        assert_eq!(
            registry
                .list_resources_in_namespace("virtual-ns/integration")
                .len(),
            1
        );
        assert_eq!(
            registry
                .list_prompts_in_namespace("virtual-ns/integration")
                .len(),
            1
        );
        Ok(())
    }

    #[test]
    fn membership_validation_rejects_invalid_updates_without_changes() -> TestResult {
        let registry = populated()?;
        for members in [
            vec!["missing"],
            vec!["kafka", "kafka"],
            vec!["integration"],
            vec!["Bad Name"],
        ] {
            assert!(
                registry
                    .try_register_namespace(namespace("integration", Some(&members)))
                    .is_err()
            );
        }
        assert_eq!(
            registry.virtual_members("integration"),
            vec!["kafka", "flink"]
        );
        assert!(
            registry
                .try_register_namespace(namespace("kafka", Some(&[])))
                .is_err()
        );
        assert!(registry.try_remove_namespace("kafka").is_err());
        assert!(
            registry
                .try_register_namespace(namespace("outer", Some(&["integration"])))
                .is_err()
        );
        registry.try_register_namespace(namespace("empty", Some(&[])))?;
        assert!(
            registry
                .get_namespace("empty")
                .is_some_and(|entry| entry.is_virtual())
        );
        assert!(
            registry
                .list_tools_in_namespace("virtual-ns/empty")
                .is_empty()
        );
        assert!(registry.resolve_virtual_member("integration", "kafka"));
        assert!(!registry.resolve_virtual_member("integration", "missing"));
        Ok(())
    }

    #[test]
    fn virtual_namespaces_reject_every_direct_entity_type() -> TestResult {
        let registry = populated()?;
        registry.register_tool(serde_json::from_value(serde_json::json!({
            "name":"health-check", "description":"health", "uri":"http://upstream/mcp",
            "type":"mcp-forward", "inputSchema":{}, "namespace":"integration"
        }))?);
        registry.register_resource(serde_json::from_value(serde_json::json!({
            "name":"r", "location":"data://records", "type":"mcp-forward", "namespace":"integration"
        }))?);
        registry.register_prompt(serde_json::from_value(
            serde_json::json!({"name":"p", "namespace":"integration"}),
        )?);
        let forward = serde_json::from_value(serde_json::json!({
            "name":"f", "address":"http://backend/mcp", "namespace":"integration"
        }))?;
        assert!(registry.try_register_forward(forward).is_err());
        let agent = serde_json::from_value(serde_json::json!({
            "name":"a", "address":"http://backend/a2a", "namespace":"integration"
        }))?;
        assert!(registry.save_agent(agent, false).is_err());
        assert_eq!(registry.tool_count(), 0);
        assert_eq!(registry.resource_count(), 0);
        assert_eq!(registry.prompt_count(), 0);
        assert!(registry.list_forwards().is_empty());
        assert!(registry.list_agents().is_empty());
        Ok(())
    }

    #[test]
    fn populated_concrete_namespace_cannot_become_virtual() -> TestResult {
        let registry = populated()?;
        registry.try_register_forward(serde_json::from_value(serde_json::json!({
            "name":"f", "address":"http://backend/mcp", "namespace":"owned"
        }))?)?;
        assert!(
            registry
                .try_register_namespace(namespace("owned", Some(&["kafka"])))
                .is_err()
        );
        assert!(
            registry
                .get_namespace("owned")
                .is_some_and(|entry| !entry.is_virtual())
        );
        assert!(registry.get_forward("f").is_some());
        Ok(())
    }

    struct SnapshotBackend(&'static str);

    impl PersistenceBackend for SnapshotBackend {
        fn load(&self) -> Result<RegistrySnapshot, PersistenceError> {
            Ok(serde_json::from_str(self.0)?)
        }
        fn save(&self, _: &RegistrySnapshot) -> Result<(), PersistenceError> {
            Ok(())
        }
    }

    #[test]
    fn persisted_graph_is_validated_before_any_entries_are_restored() {
        for snapshot in [
            r#"{"namespaces":[{"name":"a","members":["b"]},{"name":"b","members":[]}]}"#,
            r#"{"namespaces":[{"name":"a","members":["missing"]}]}"#,
            r#"{"namespaces":[{"name":"a"},{"name":"a","members":[]}]}"#,
            r#"{"namespaces":[{"name":"a","members":[]}],"forwards":[{"name":"f","namespace":"a","address":"http://backend/mcp"}]}"#,
        ] {
            let registry = InMemoryRegistry::with_persistence(Arc::new(SnapshotBackend(snapshot)));
            registry.load_persisted();
            assert!(registry.get_namespace("a").is_none());
            assert!(registry.list_forwards().is_empty());
        }
    }

    #[test]
    fn persisted_virtual_graph_loads_and_old_namespace_shape_remains_concrete() {
        let registry = InMemoryRegistry::with_persistence(Arc::new(SnapshotBackend(
            r#"{"namespaces":[{"name":"integration","targets":["kafka"]},{"name":"kafka"}]}"#,
        )));
        registry.load_persisted();
        assert!(registry.resolve_virtual_member("integration", "kafka"));
        assert!(
            registry
                .get_namespace("kafka")
                .is_some_and(|entry| !entry.is_virtual())
        );
    }
}
