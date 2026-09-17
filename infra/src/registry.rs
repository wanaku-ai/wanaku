use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Condvar, Mutex};
use std::time::{Duration, Instant};

use dashmap::DashMap;
use wanaku_types::persistence::{PersistenceBackend, PersistenceError, RegistrySnapshot};
use wanaku_types::registry::{
    DEFAULT_NAMESPACE, ForwardEntry, ForwardRegistry, NamespaceEntry, NamespaceRegistry,
    PromptEntry, PromptRegistry, ResourceEntry, ResourceRegistry, ToolEntry, ToolRegistry,
    inject_request_id_arg,
};

#[derive(Clone)]
pub struct InMemoryRegistry {
    tools: Arc<DashMap<String, ToolEntry>>,
    resources: Arc<DashMap<String, ResourceEntry>>,
    prompts: Arc<DashMap<String, PromptEntry>>,
    forwards: Arc<DashMap<String, ForwardEntry>>,
    namespaces: Arc<DashMap<String, NamespaceEntry>>,
    persistence: Option<PersistenceCoordinator>,
    inject_request_id: Arc<AtomicBool>,
}

impl InMemoryRegistry {
    pub fn new() -> Self {
        let namespaces = DashMap::new();
        namespaces.insert(
            DEFAULT_NAMESPACE.to_owned(),
            NamespaceEntry {
                name: DEFAULT_NAMESPACE.to_owned(),
                labels: HashMap::new(),
                auth_required: None,
                audience: None,
            },
        );

        Self {
            tools: Arc::new(DashMap::new()),
            resources: Arc::new(DashMap::new()),
            prompts: Arc::new(DashMap::new()),
            forwards: Arc::new(DashMap::new()),
            namespaces: Arc::new(namespaces),
            persistence: None,
            inject_request_id: Arc::new(AtomicBool::new(false)),
        }
    }

    pub fn enable_request_id_injection(&self) {
        self.inject_request_id.store(true, Ordering::Relaxed);
    }

    pub fn with_persistence(backend: Arc<dyn PersistenceBackend>) -> Self {
        Self {
            persistence: Some(PersistenceCoordinator::start(backend)),
            ..Self::new()
        }
    }

    /// Load all entries from the persistence backend into memory.
    ///
    /// Inserts directly into the DashMaps to avoid triggering
    /// `persist()` on every entry (the data already came from disk).
    #[expect(
        clippy::too_many_lines,
        reason = "sequential loading of all registry types"
    )]
    pub fn load_persisted(&self) {
        let Some(persistence) = &self.persistence else {
            return;
        };

        let snapshot = match persistence.backend.load() {
            Ok(s) => s,
            Err(e) => {
                tracing::warn!(error = %e, "failed to load persisted registry");
                return;
            }
        };

        for mut tool in snapshot.tools {
            if tool.namespace.is_none() {
                tool.namespace = Some(DEFAULT_NAMESPACE.to_owned());
            }
            inject_request_id_arg(&mut tool.input_schema);
            self.tools.insert(tool.name.clone(), tool);
        }
        for mut resource in snapshot.resources {
            if resource.namespace.is_none() {
                resource.namespace = Some(DEFAULT_NAMESPACE.to_owned());
            }
            self.resources.insert(resource.name.clone(), resource);
        }
        for mut prompt in snapshot.prompts {
            if prompt.namespace.is_none() {
                prompt.namespace = Some(DEFAULT_NAMESPACE.to_owned());
            }
            self.prompts.insert(prompt.name.clone(), prompt);
        }
        for namespace in snapshot.namespaces {
            self.namespaces.insert(namespace.name.clone(), namespace);
        }
        for forward in snapshot.forwards {
            self.insert_forward(forward);
        }

        tracing::info!("loaded registry from persistence backend");
    }

    fn snapshot(&self) -> RegistrySnapshot {
        RegistrySnapshot {
            tools: self.list_tools(),
            resources: self.list_resources(),
            prompts: self.list_prompts(),
            forwards: self.list_forwards(),
            namespaces: self.list_namespaces(),
        }
    }

    fn persist(&self) {
        if let Some(persistence) = &self.persistence {
            let snapshot = self.snapshot();
            if let Err(error) = persistence.enqueue(snapshot) {
                tracing::warn!(error = %error, "failed to queue registry persistence");
            }
        }
    }

    /// Wait for registry snapshots submitted before this call to persist.
    ///
    /// The wait is bounded by `timeout`. This method is intended for orderly
    /// shutdown and tests; regular registry mutations remain non-blocking.
    pub fn flush_persistence(&self, timeout: Duration) -> Result<(), PersistenceError> {
        self.persistence
            .as_ref()
            .map_or(Ok(()), |persistence| persistence.flush(timeout))
    }

    /// Return a small, non-sensitive view of registry persistence progress.
    #[must_use]
    pub fn persistence_status(&self) -> RegistryPersistenceStatus {
        self.persistence.as_ref().map_or_else(
            || RegistryPersistenceStatus {
                enabled: false,
                pending: false,
                last_successful_generation: None,
                last_error: None,
            },
            PersistenceCoordinator::status,
        )
    }

    fn insert_forward(&self, mut forward: ForwardEntry) {
        let namespace = forward
            .namespace
            .clone()
            .unwrap_or_else(|| DEFAULT_NAMESPACE.to_owned());
        forward.namespace = Some(namespace.clone());

        if let Err(reason) = wanaku_types::registry::validate_namespace_name(&namespace) {
            tracing::warn!(
                forward = %forward.name,
                namespace = %namespace,
                error = %reason,
                "forward references an invalid namespace name; skipping namespace auto-registration"
            );
        } else {
            self.namespaces
                .entry(namespace.clone())
                .or_insert_with(|| NamespaceEntry {
                    name: namespace,
                    labels: HashMap::new(),
                    auth_required: None,
                    audience: None,
                });
        }

        self.forwards.insert(forward.name.clone(), forward);
    }
}

/// Non-sensitive registry persistence state for health and shutdown handling.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RegistryPersistenceStatus {
    pub enabled: bool,
    pub pending: bool,
    pub last_successful_generation: Option<u64>,
    pub last_error: Option<String>,
}

struct PersistenceCoordinator {
    backend: Arc<dyn PersistenceBackend>,
    shared: Arc<PersistenceShared>,
    join: Arc<Mutex<Option<std::thread::JoinHandle<()>>>>,
}

struct PersistenceShared {
    state: Mutex<PersistenceState>,
    ready: Condvar,
    completed: Condvar,
}

struct PersistenceState {
    pending: Option<PendingSnapshot>,
    next_generation: u64,
    completed_generation: u64,
    last_successful_generation: Option<u64>,
    last_error: Option<PersistenceFailure>,
    producers: usize,
}

struct PendingSnapshot {
    generation: u64,
    snapshot: RegistrySnapshot,
}

struct PersistenceFailure {
    generation: u64,
    message: String,
}

const REGISTRY_PERSISTENCE_ERROR: &str = "registry persistence unavailable";

impl PersistenceCoordinator {
    fn start(backend: Arc<dyn PersistenceBackend>) -> Self {
        let shared = Arc::new(PersistenceShared {
            state: Mutex::new(PersistenceState {
                pending: None,
                next_generation: 0,
                completed_generation: 0,
                last_successful_generation: None,
                last_error: None,
                producers: 1,
            }),
            ready: Condvar::new(),
            completed: Condvar::new(),
        });
        let worker_shared = Arc::clone(&shared);
        let worker_backend = Arc::clone(&backend);
        let join = match std::thread::Builder::new()
            .name("wanaku-registry-persistence".to_owned())
            .spawn(move || persistence_loop(&worker_shared, &worker_backend))
        {
            Ok(join) => Some(join),
            Err(error) => {
                let mut state = lock_state(&shared);
                state.last_error = Some(PersistenceFailure {
                    generation: 0,
                    message: error.to_string(),
                });
                None
            }
        };
        Self {
            backend,
            shared,
            join: Arc::new(Mutex::new(join)),
        }
    }

    fn enqueue(&self, snapshot: RegistrySnapshot) -> Result<(), PersistenceError> {
        let worker_available = match self.join.lock() {
            Ok(join) => join.is_some(),
            Err(error) => error.into_inner().is_some(),
        };
        if !worker_available {
            return Err(PersistenceError::Coordination(
                "registry persistence worker is unavailable".to_owned(),
            ));
        }
        let mut state = lock_state(&self.shared);
        state.next_generation = state.next_generation.saturating_add(1);
        let generation = state.next_generation;
        state.pending = Some(PendingSnapshot {
            generation,
            snapshot,
        });
        self.shared.ready.notify_one();
        Ok(())
    }

    fn flush(&self, timeout: Duration) -> Result<(), PersistenceError> {
        let deadline = Instant::now() + timeout;
        let mut state = lock_state(&self.shared);
        let target_generation = state.next_generation;
        while state.completed_generation < target_generation {
            let remaining = deadline.saturating_duration_since(Instant::now());
            if remaining.is_zero() {
                return Err(PersistenceError::Coordination(
                    "timed out waiting for registry persistence".to_owned(),
                ));
            }
            let wait = self.shared.completed.wait_timeout(state, remaining);
            state = match wait {
                Ok((state, _)) => state,
                Err(error) => error.into_inner().0,
            };
        }
        if let Some(error) = &state.last_error
            && error.generation >= target_generation
        {
            return Err(PersistenceError::Coordination(error.message.clone()));
        }
        Ok(())
    }

    fn status(&self) -> RegistryPersistenceStatus {
        let state = lock_state(&self.shared);
        RegistryPersistenceStatus {
            enabled: true,
            pending: state.pending.is_some() || state.completed_generation < state.next_generation,
            last_successful_generation: state.last_successful_generation,
            last_error: state
                .last_error
                .as_ref()
                .map(|_| REGISTRY_PERSISTENCE_ERROR.to_owned()),
        }
    }

    fn add_producer(&self) {
        let mut state = lock_state(&self.shared);
        state.producers = state.producers.saturating_add(1);
    }
}

impl Clone for PersistenceCoordinator {
    fn clone(&self) -> Self {
        self.add_producer();
        Self {
            backend: Arc::clone(&self.backend),
            shared: Arc::clone(&self.shared),
            join: Arc::clone(&self.join),
        }
    }
}

impl Drop for PersistenceCoordinator {
    fn drop(&mut self) {
        let last_producer = {
            let mut state = lock_state(&self.shared);
            state.producers = state.producers.saturating_sub(1);
            state.producers == 0
        };
        self.shared.ready.notify_one();
        if !last_producer {
            return;
        }
        let join = match self.join.lock() {
            Ok(mut join) => join.take(),
            Err(error) => error.into_inner().take(),
        };
        drop(join);
    }
}

fn persistence_loop(shared: &PersistenceShared, backend: &Arc<dyn PersistenceBackend>) {
    while let Some(pending) = next_pending_snapshot(shared) {
        let result = backend.save(&pending.snapshot);
        let mut state = lock_state(shared);
        state.completed_generation = state.completed_generation.max(pending.generation);
        match result {
            Ok(()) => {
                state.last_successful_generation = Some(pending.generation);
                state.last_error = None;
            }
            Err(error) => {
                tracing::warn!(error = %error, "failed to persist registry");
                state.last_error = Some(PersistenceFailure {
                    generation: pending.generation,
                    message: error.to_string(),
                });
            }
        }
        shared.completed.notify_all();
    }
}

fn next_pending_snapshot(shared: &PersistenceShared) -> Option<PendingSnapshot> {
    let mut state = lock_state(shared);
    while state.pending.is_none() && state.producers > 0 {
        state = match shared.ready.wait(state) {
            Ok(state) => state,
            Err(error) => error.into_inner(),
        };
    }
    state.pending.take()
}

fn lock_state(shared: &PersistenceShared) -> std::sync::MutexGuard<'_, PersistenceState> {
    match shared.state.lock() {
        Ok(state) => state,
        Err(error) => error.into_inner(),
    }
}

impl Default for InMemoryRegistry {
    fn default() -> Self {
        Self::new()
    }
}

fn effective_namespace(ns: &Option<String>) -> &str {
    ns.as_deref().unwrap_or(DEFAULT_NAMESPACE)
}

impl ToolRegistry for InMemoryRegistry {
    fn list_tools(&self) -> Vec<ToolEntry> {
        self.tools
            .iter()
            .map(|entry| entry.value().clone())
            .collect()
    }

    fn list_tools_in_namespace(&self, namespace: &str) -> Vec<ToolEntry> {
        self.tools
            .iter()
            .filter(|entry| effective_namespace(&entry.value().namespace) == namespace)
            .map(|entry| entry.value().clone())
            .collect()
    }

    fn get_tool(&self, name: &str) -> Option<ToolEntry> {
        self.tools.get(name).map(|entry| entry.value().clone())
    }

    fn get_tool_in_namespace(&self, namespace: &str, name: &str) -> Option<ToolEntry> {
        self.tools
            .get(name)
            .map(|entry| entry.value().clone())
            .filter(|tool| effective_namespace(&tool.namespace) == namespace)
    }

    fn register_tool(&self, mut tool: ToolEntry) {
        if tool.namespace.is_none() {
            tool.namespace = Some(DEFAULT_NAMESPACE.to_owned());
        }
        if self.inject_request_id.load(Ordering::Relaxed) {
            inject_request_id_arg(&mut tool.input_schema);
        }
        self.tools.insert(tool.name.clone(), tool);
        self.persist();
    }

    fn register_tools_batch(&self, tools: Vec<ToolEntry>) {
        if tools.is_empty() {
            return;
        }
        let inject = self.inject_request_id.load(Ordering::Relaxed);
        let default_ns = DEFAULT_NAMESPACE.to_owned();
        for mut tool in tools {
            if tool.namespace.is_none() {
                tool.namespace = Some(default_ns.clone());
            }
            if inject {
                inject_request_id_arg(&mut tool.input_schema);
            }
            self.tools.insert(tool.name.clone(), tool);
        }
        self.persist();
    }

    fn remove_tool(&self, name: &str) -> bool {
        let removed = self.tools.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
    }

    fn remove_tools_batch(&self, names: &[String]) -> usize {
        let mut count = 0;
        for name in names {
            if self.tools.remove(name.as_str()).is_some() {
                count += 1;
            }
        }
        if count > 0 {
            self.persist();
        }
        count
    }

    fn tool_count(&self) -> usize {
        self.tools.len()
    }
}

impl ResourceRegistry for InMemoryRegistry {
    fn list_resources(&self) -> Vec<ResourceEntry> {
        self.resources
            .iter()
            .map(|entry| entry.value().clone())
            .collect()
    }

    fn list_resources_in_namespace(&self, namespace: &str) -> Vec<ResourceEntry> {
        self.resources
            .iter()
            .filter(|entry| effective_namespace(&entry.value().namespace) == namespace)
            .map(|entry| entry.value().clone())
            .collect()
    }

    fn get_resource(&self, name: &str) -> Option<ResourceEntry> {
        self.resources.get(name).map(|entry| entry.value().clone())
    }

    fn get_resource_in_namespace(&self, namespace: &str, name: &str) -> Option<ResourceEntry> {
        self.resources
            .get(name)
            .map(|entry| entry.value().clone())
            .filter(|res| effective_namespace(&res.namespace) == namespace)
    }

    fn register_resource(&self, mut resource: ResourceEntry) {
        if resource.namespace.is_none() {
            resource.namespace = Some(DEFAULT_NAMESPACE.to_owned());
        }
        self.resources.insert(resource.name.clone(), resource);
        self.persist();
    }

    fn register_resources_batch(&self, resources: Vec<ResourceEntry>) {
        if resources.is_empty() {
            return;
        }
        let default_ns = DEFAULT_NAMESPACE.to_owned();
        for mut resource in resources {
            if resource.namespace.is_none() {
                resource.namespace = Some(default_ns.clone());
            }
            self.resources.insert(resource.name.clone(), resource);
        }
        self.persist();
    }

    fn remove_resource(&self, name: &str) -> bool {
        let removed = self.resources.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
    }

    fn remove_resources_batch(&self, names: &[String]) -> usize {
        let mut count = 0;
        for name in names {
            if self.resources.remove(name.as_str()).is_some() {
                count += 1;
            }
        }
        if count > 0 {
            self.persist();
        }
        count
    }

    fn resource_count(&self) -> usize {
        self.resources.len()
    }
}

impl PromptRegistry for InMemoryRegistry {
    fn list_prompts(&self) -> Vec<PromptEntry> {
        self.prompts
            .iter()
            .map(|entry| entry.value().clone())
            .collect()
    }

    fn list_prompts_in_namespace(&self, namespace: &str) -> Vec<PromptEntry> {
        self.prompts
            .iter()
            .filter(|entry| effective_namespace(&entry.value().namespace) == namespace)
            .map(|entry| entry.value().clone())
            .collect()
    }

    fn get_prompt(&self, name: &str) -> Option<PromptEntry> {
        self.prompts.get(name).map(|entry| entry.value().clone())
    }

    fn get_prompt_in_namespace(&self, namespace: &str, name: &str) -> Option<PromptEntry> {
        self.prompts
            .get(name)
            .map(|entry| entry.value().clone())
            .filter(|prompt| effective_namespace(&prompt.namespace) == namespace)
    }

    fn register_prompt(&self, mut prompt: PromptEntry) {
        if prompt.namespace.is_none() {
            prompt.namespace = Some(DEFAULT_NAMESPACE.to_owned());
        }
        self.prompts.insert(prompt.name.clone(), prompt);
        self.persist();
    }

    fn register_prompts_batch(&self, prompts: Vec<PromptEntry>) {
        if prompts.is_empty() {
            return;
        }
        let default_ns = DEFAULT_NAMESPACE.to_owned();
        for mut prompt in prompts {
            if prompt.namespace.is_none() {
                prompt.namespace = Some(default_ns.clone());
            }
            self.prompts.insert(prompt.name.clone(), prompt);
        }
        self.persist();
    }

    fn remove_prompt(&self, name: &str) -> bool {
        let removed = self.prompts.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
    }

    fn remove_prompts_batch(&self, names: &[String]) -> usize {
        let mut count = 0;
        for name in names {
            if self.prompts.remove(name.as_str()).is_some() {
                count += 1;
            }
        }
        if count > 0 {
            self.persist();
        }
        count
    }

    fn prompt_count(&self) -> usize {
        self.prompts.len()
    }
}

impl NamespaceRegistry for InMemoryRegistry {
    fn list_namespaces(&self) -> Vec<NamespaceEntry> {
        self.namespaces
            .iter()
            .map(|entry| entry.value().clone())
            .collect()
    }

    fn get_namespace(&self, name: &str) -> Option<NamespaceEntry> {
        self.namespaces.get(name).map(|entry| entry.value().clone())
    }

    fn register_namespace(&self, namespace: NamespaceEntry) {
        self.namespaces.insert(namespace.name.clone(), namespace);
        self.persist();
    }

    fn remove_namespace(&self, name: &str) -> bool {
        let removed = self.namespaces.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
    }
}

impl ForwardRegistry for InMemoryRegistry {
    fn list_forwards(&self) -> Vec<ForwardEntry> {
        self.forwards
            .iter()
            .map(|entry| entry.value().clone())
            .collect()
    }

    fn get_forward(&self, name: &str) -> Option<ForwardEntry> {
        self.forwards.get(name).map(|entry| entry.value().clone())
    }

    fn register_forward(&self, forward: ForwardEntry) {
        self.insert_forward(forward);
        self.persist();
    }

    fn remove_forward(&self, name: &str) -> bool {
        let removed = self.forwards.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_tool() -> ToolEntry {
        ToolEntry {
            name: "test-tool".to_owned(),
            description: "A test tool".to_owned(),
            uri: "camel:http://example.com".to_owned(),
            type_: "http".to_owned(),
            input_schema: serde_json::json!({"type": "object"}),
            labels: HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        }
    }

    fn sample_resource() -> ResourceEntry {
        ResourceEntry {
            name: "test-resource".to_owned(),
            description: "A test resource".to_owned(),
            location: "/tmp/test.txt".to_owned(),
            type_: "file".to_owned(),
            mime_type: "text/plain".to_owned(),
            labels: HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        }
    }

    #[test]
    fn register_and_list_tools() {
        let registry = InMemoryRegistry::new();
        registry.register_tool(sample_tool());
        let tools = registry.list_tools();
        assert_eq!(tools.len(), 1);
        assert_eq!(tools[0].name, "test-tool");
    }

    #[test]
    fn get_tool_by_name() {
        let registry = InMemoryRegistry::new();
        registry.register_tool(sample_tool());
        let tool = registry.get_tool("test-tool");
        assert!(tool.is_some());
        assert_eq!(
            tool.as_ref().map(|t| t.uri.as_str()),
            Some("camel:http://example.com")
        );
    }

    #[test]
    fn get_missing_tool_returns_none() {
        let registry = InMemoryRegistry::new();
        assert!(registry.get_tool("nonexistent").is_none());
    }

    #[test]
    fn remove_tool() {
        let registry = InMemoryRegistry::new();
        registry.register_tool(sample_tool());
        assert!(registry.remove_tool("test-tool"));
        assert!(registry.get_tool("test-tool").is_none());
    }

    #[test]
    fn register_and_list_resources() {
        let registry = InMemoryRegistry::new();
        registry.register_resource(sample_resource());
        let resources = registry.list_resources();
        assert_eq!(resources.len(), 1);
        assert_eq!(resources[0].name, "test-resource");
    }

    #[test]
    fn get_resource_by_name() {
        let registry = InMemoryRegistry::new();
        registry.register_resource(sample_resource());
        let res = registry.get_resource("test-resource");
        assert!(res.is_some());
        assert_eq!(
            res.as_ref().map(|r| r.location.as_str()),
            Some("/tmp/test.txt")
        );
    }

    #[test]
    fn remove_resource() {
        let registry = InMemoryRegistry::new();
        registry.register_resource(sample_resource());
        assert!(registry.remove_resource("test-resource"));
        assert!(registry.get_resource("test-resource").is_none());
    }

    #[test]
    fn remove_resources_batch() {
        let registry = InMemoryRegistry::new();
        registry.register_resource(ResourceEntry {
            name: "r1".to_owned(),
            ..sample_resource()
        });
        registry.register_resource(ResourceEntry {
            name: "r2".to_owned(),
            ..sample_resource()
        });
        registry.register_resource(ResourceEntry {
            name: "r3".to_owned(),
            ..sample_resource()
        });

        let removed = registry.remove_resources_batch(&["r1".to_owned(), "r3".to_owned()]);
        assert_eq!(removed, 2);
        assert!(registry.get_resource("r1").is_none());
        assert!(registry.get_resource("r2").is_some());
        assert!(registry.get_resource("r3").is_none());
    }

    #[test]
    fn register_tool_skips_injection_when_disabled() {
        let registry = InMemoryRegistry::new();
        let tool = ToolEntry {
            name: "test".to_owned(),
            description: "test tool".to_owned(),
            uri: "test://uri".to_owned(),
            type_: "test".to_owned(),
            input_schema: serde_json::json!({
                "type": "object",
                "properties": {
                    "msg": {"type": "string"}
                }
            }),
            namespace: None,
            id: None,
            configuration_uri: None,
            secrets_uri: None,
            labels: std::collections::HashMap::new(),
        };
        registry.register_tool(tool);
        let stored = registry.get_tool("test").expect("tool should exist");
        let props = stored.input_schema["properties"]
            .as_object()
            .expect("has properties");
        assert_eq!(
            props.len(),
            1,
            "x-request-id should not be injected when flag is disabled"
        );
    }

    #[test]
    fn register_tool_injects_when_enabled() {
        let registry = InMemoryRegistry::new();
        registry.enable_request_id_injection();
        let tool = ToolEntry {
            name: "test".to_owned(),
            description: "test tool".to_owned(),
            uri: "test://uri".to_owned(),
            type_: "test".to_owned(),
            input_schema: serde_json::json!({
                "type": "object",
                "properties": {
                    "msg": {"type": "string"}
                }
            }),
            namespace: None,
            id: None,
            configuration_uri: None,
            secrets_uri: None,
            labels: std::collections::HashMap::new(),
        };
        registry.register_tool(tool);
        let stored = registry.get_tool("test").expect("tool should exist");
        let props = stored.input_schema["properties"]
            .as_object()
            .expect("has properties");
        assert_eq!(
            props.len(),
            2,
            "x-request-id should be injected when flag is enabled"
        );
        assert!(props.contains_key("x-request-id"));
    }

    fn sample_forward(name: &str, namespace: Option<&str>) -> ForwardEntry {
        ForwardEntry {
            name: name.to_owned(),
            address: "http://localhost:9090/mcp".to_owned(),
            namespace: namespace.map(str::to_owned),
            server_info: None,
            labels: HashMap::new(),
            available: false,
            status_message: None,
            credential_bindings: HashMap::new(),
        }
    }

    #[test]
    fn register_forward_registers_referenced_namespace() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(sample_forward("example-mcp", Some("test-ns")));

        let namespace = registry.get_namespace("test-ns");
        assert!(
            namespace.is_some(),
            "namespace referenced by forward should be registered"
        );
        assert_eq!(namespace.expect("namespace should exist").name, "test-ns");
        assert!(
            registry
                .list_namespaces()
                .iter()
                .any(|ns| ns.name == "test-ns"),
            "namespace referenced by forward should appear in list_namespaces"
        );
    }

    #[test]
    fn register_forward_without_namespace_uses_default() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(sample_forward("example-mcp", None));

        let forward = registry
            .get_forward("example-mcp")
            .expect("forward should exist");
        assert_eq!(forward.namespace.as_deref(), Some(DEFAULT_NAMESPACE));
        assert!(registry.get_namespace(DEFAULT_NAMESPACE).is_some());
    }

    #[test]
    fn multiple_forwards_share_single_namespace_entry() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(sample_forward("forward-a", Some("shared-ns")));
        registry.register_forward(sample_forward("forward-b", Some("shared-ns")));

        let matching: Vec<_> = registry
            .list_namespaces()
            .into_iter()
            .filter(|ns| ns.name == "shared-ns")
            .collect();
        assert_eq!(
            matching.len(),
            1,
            "multiple forwards should create a single namespace entry"
        );
    }

    #[test]
    fn register_forward_does_not_clobber_existing_namespace() {
        let registry = InMemoryRegistry::new();
        let mut labels = HashMap::new();
        labels.insert("team".to_owned(), "finance".to_owned());
        registry.register_namespace(NamespaceEntry {
            name: "test-ns".to_owned(),
            labels: labels.clone(),
            auth_required: Some(true),
            audience: Some("finance-audience".to_owned()),
        });

        registry.register_forward(sample_forward("example-mcp", Some("test-ns")));

        let namespace = registry
            .get_namespace("test-ns")
            .expect("namespace should exist");
        assert_eq!(
            namespace.labels, labels,
            "existing namespace metadata must be preserved"
        );
        assert_eq!(namespace.auth_required, Some(true));
        assert_eq!(namespace.audience.as_deref(), Some("finance-audience"));
    }

    #[test]
    fn register_forward_with_invalid_namespace_skips_auto_registration() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(sample_forward("example-mcp", Some("Invalid NS")));

        assert!(
            registry.get_namespace("Invalid NS").is_none(),
            "invalid namespace names should not be auto-registered"
        );
        // The forward itself is still registered.
        assert!(registry.get_forward("example-mcp").is_some());
    }

    #[test]
    fn load_persisted_registers_namespace_referenced_by_forward() {
        struct SnapshotBackend {
            forward: ForwardEntry,
        }

        impl PersistenceBackend for SnapshotBackend {
            fn load(
                &self,
            ) -> Result<RegistrySnapshot, wanaku_types::persistence::PersistenceError> {
                Ok(RegistrySnapshot {
                    forwards: vec![self.forward.clone()],
                    ..RegistrySnapshot::default()
                })
            }

            fn save(
                &self,
                _snapshot: &RegistrySnapshot,
            ) -> Result<(), wanaku_types::persistence::PersistenceError> {
                Ok(())
            }
        }

        let backend = Arc::new(SnapshotBackend {
            forward: sample_forward("persisted-forward", Some("persisted-ns")),
        });
        let registry = InMemoryRegistry::with_persistence(backend);

        registry.load_persisted();

        assert!(registry.get_forward("persisted-forward").is_some());
        assert!(
            registry.get_namespace("persisted-ns").is_some(),
            "a persisted forward namespace should be registered when the snapshot omits it"
        );
    }

    struct BlockingBackend {
        calls: std::sync::atomic::AtomicUsize,
        active: std::sync::atomic::AtomicUsize,
        max_active: std::sync::atomic::AtomicUsize,
        snapshots: Mutex<Vec<RegistrySnapshot>>,
        first_entered: std::sync::mpsc::Sender<()>,
        release_first: Mutex<std::sync::mpsc::Receiver<()>>,
    }

    impl PersistenceBackend for BlockingBackend {
        fn load(&self) -> Result<RegistrySnapshot, PersistenceError> {
            Ok(RegistrySnapshot::default())
        }

        fn save(&self, snapshot: &RegistrySnapshot) -> Result<(), PersistenceError> {
            let active = self.active.fetch_add(1, Ordering::SeqCst) + 1;
            self.max_active.fetch_max(active, Ordering::SeqCst);
            let call = self.calls.fetch_add(1, Ordering::SeqCst);
            if call == 0 {
                self.first_entered
                    .send(())
                    .map_err(|error| PersistenceError::Coordination(error.to_string()))?;
                self.release_first
                    .lock()
                    .map_err(|error| PersistenceError::Coordination(error.to_string()))?
                    .recv()
                    .map_err(|error| PersistenceError::Coordination(error.to_string()))?;
            }
            self.snapshots
                .lock()
                .map_err(|error| PersistenceError::Coordination(error.to_string()))?
                .push(RegistrySnapshot {
                    tools: snapshot.tools.clone(),
                    resources: snapshot.resources.clone(),
                    prompts: snapshot.prompts.clone(),
                    forwards: snapshot.forwards.clone(),
                    namespaces: snapshot.namespaces.clone(),
                });
            self.active.fetch_sub(1, Ordering::SeqCst);
            Ok(())
        }
    }

    #[test]
    fn persistence_coalesces_pending_snapshots_and_flushes_latest_generation() {
        let (first_entered_tx, first_entered_rx) = std::sync::mpsc::channel();
        let (release_first_tx, release_first_rx) = std::sync::mpsc::channel();
        let backend = Arc::new(BlockingBackend {
            calls: std::sync::atomic::AtomicUsize::new(0),
            active: std::sync::atomic::AtomicUsize::new(0),
            max_active: std::sync::atomic::AtomicUsize::new(0),
            snapshots: Mutex::new(Vec::new()),
            first_entered: first_entered_tx,
            release_first: Mutex::new(release_first_rx),
        });
        let registry = InMemoryRegistry::with_persistence(backend.clone());

        registry.register_tool(sample_tool());
        assert!(
            first_entered_rx
                .recv_timeout(Duration::from_secs(2))
                .is_ok()
        );
        assert!(registry.persistence_status().pending);

        let mut second = sample_tool();
        second.name = "second-tool".to_owned();
        registry.register_tool(second);
        let mut third = sample_tool();
        third.name = "third-tool".to_owned();
        registry.register_tool(third);

        assert!(release_first_tx.send(()).is_ok());
        assert!(registry.flush_persistence(Duration::from_secs(2)).is_ok());

        let snapshots = backend.snapshots.lock().expect("snapshot lock");
        assert_eq!(backend.max_active.load(Ordering::SeqCst), 1);
        assert_eq!(
            snapshots.len(),
            2,
            "worker should coalesce pending snapshots"
        );
        let final_names: Vec<_> = snapshots
            .last()
            .into_iter()
            .flat_map(|snapshot| snapshot.tools.iter().map(|tool| tool.name.as_str()))
            .collect();
        assert_eq!(final_names.len(), 3);
        assert!(final_names.contains(&"third-tool"));
        assert_eq!(
            registry.persistence_status().last_successful_generation,
            Some(3)
        );
    }

    struct FailingBackend;

    impl PersistenceBackend for FailingBackend {
        fn load(&self) -> Result<RegistrySnapshot, PersistenceError> {
            Ok(RegistrySnapshot::default())
        }

        fn save(&self, _snapshot: &RegistrySnapshot) -> Result<(), PersistenceError> {
            Err(PersistenceError::Coordination("write failed".to_owned()))
        }
    }

    #[test]
    fn persistence_failure_is_reported_by_flush_and_status() {
        let registry = InMemoryRegistry::with_persistence(Arc::new(FailingBackend));
        registry.register_tool(sample_tool());

        assert!(registry.flush_persistence(Duration::from_secs(2)).is_err());
        let status = registry.persistence_status();
        assert!(status.enabled);
        assert!(!status.pending);
        assert_eq!(
            status.last_error.as_deref(),
            Some(REGISTRY_PERSISTENCE_ERROR)
        );
    }

    #[test]
    fn disabled_persistence_has_no_pending_work() {
        let registry = InMemoryRegistry::new();

        assert!(registry.flush_persistence(Duration::from_secs(1)).is_ok());
        assert_eq!(
            registry.persistence_status(),
            RegistryPersistenceStatus {
                enabled: false,
                pending: false,
                last_successful_generation: None,
                last_error: None,
            }
        );
    }
}
