//! Compile-before-activation lifecycle for WASM-backed tools.

use crate::config::HandlerDefinition;
use crate::engine::CompiledHandler;
use serde_json::Value;
use std::collections::{BTreeMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, RwLock};
use wanaku_infra::registry::RuntimeRegistry;
use wanaku_types::registry::{DEFAULT_NAMESPACE, NamespaceRegistry, ToolRegistry};
use wanaku_types::wasm::{HostError, HostErrorKind, McpHostClient, ToolHandler, ToolTarget};

pub const TOOL_TYPE: &str = "wasm";
type Key = (String, String);
type Snapshot = BTreeMap<Key, Arc<CompiledHandler>>;

/// Clone-safe runtime state. Each call retains its selected compiled version.
#[derive(Clone)]
pub struct HandlerState {
    registry: RuntimeRegistry,
    active: Arc<RwLock<Snapshot>>,
    mutation: Arc<Mutex<()>>,
    persistence: Option<PathBuf>,
}

impl HandlerState {
    #[must_use]
    pub fn new(registry: RuntimeRegistry) -> Self {
        // A registry snapshot alone cannot make a handler executable.
        for entry in registry
            .list_tools()
            .into_iter()
            .filter(|tool| tool.type_ == TOOL_TYPE)
        {
            registry.remove_tool(
                entry.namespace.as_deref().unwrap_or(DEFAULT_NAMESPACE),
                &entry.name,
            );
        }
        Self {
            registry,
            active: Arc::new(RwLock::new(BTreeMap::new())),
            mutation: Arc::new(Mutex::new(())),
            persistence: None,
        }
    }

    /// Restore local component paths and recompile them on this host.
    pub fn with_persistence(mut self, path: impl Into<PathBuf>) -> Result<Self, String> {
        let path = path.into();
        match std::fs::read(&path) {
            Ok(bytes) => {
                let definitions = serde_json::from_slice(&bytes)
                    .map_err(|error| format!("invalid handler snapshot: {error}"))?;
                self.replace(definitions)?;
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(format!("cannot read handler snapshot: {error}")),
        }
        self.persistence = Some(path);
        Ok(self)
    }

    pub fn list(&self) -> Result<Vec<HandlerDefinition>, String> {
        let active = self
            .active
            .read()
            .map_err(|_| "handler runtime unavailable")?;
        Ok(active
            .values()
            .map(|handler| handler.definition.clone())
            .collect())
    }

    pub fn get(&self, namespace: &str, name: &str) -> Result<Option<HandlerDefinition>, String> {
        let active = self
            .active
            .read()
            .map_err(|_| "handler runtime unavailable")?;
        Ok(active
            .get(&(namespace.to_owned(), name.to_owned()))
            .map(|handler| handler.definition.clone()))
    }

    /// Replace all bindings. Compile before publishing. A failed preparation or
    /// persistence operation leaves the active configuration unchanged.
    pub fn replace(&self, definitions: Vec<HandlerDefinition>) -> Result<(), String> {
        let prepared = self.prepare(definitions)?;
        let _mutation = self
            .mutation
            .lock()
            .map_err(|_| "handler runtime unavailable")?;
        let mut active = self
            .active
            .write()
            .map_err(|_| "handler runtime unavailable")?;
        let expected: Vec<_> = active
            .values()
            .map(|handler| handler.definition.tool.clone())
            .collect();
        let next: Vec<_> = prepared
            .values()
            .map(|handler| handler.definition.tool.clone())
            .collect();
        self.registry
            .swap_handler_tools_with(&expected, next, || self.persist(&prepared))?;
        *active = prepared;
        Ok(())
    }

    pub fn remove(&self, namespace: &str, name: &str) -> Result<bool, String> {
        let _mutation = self
            .mutation
            .lock()
            .map_err(|_| "handler runtime unavailable")?;
        let mut active = self
            .active
            .write()
            .map_err(|_| "handler runtime unavailable")?;
        let key = (namespace.to_owned(), name.to_owned());
        if !active.contains_key(&key) {
            return Ok(false);
        }
        let mut next = active.clone();
        next.remove(&key);
        let expected: Vec<_> = active
            .values()
            .map(|handler| handler.definition.tool.clone())
            .collect();
        let replacement: Vec<_> = next
            .values()
            .map(|handler| handler.definition.tool.clone())
            .collect();
        self.registry
            .swap_handler_tools_with(&expected, replacement, || self.persist(&next))?;
        *active = next;
        Ok(true)
    }

    fn prepare(&self, definitions: Vec<HandlerDefinition>) -> Result<Snapshot, String> {
        let mut prepared = BTreeMap::new();
        for mut definition in definitions {
            let namespace = definition
                .tool
                .namespace
                .clone()
                .unwrap_or_else(|| DEFAULT_NAMESPACE.to_owned());
            self.validate_definition(&definition, &namespace)?;
            definition.tool.namespace = Some(namespace.clone());
            definition.tool.id = Some(format!("wasm-{:032x}", rand::random::<u128>()));
            let key = (namespace, definition.tool.name.clone());
            if prepared.contains_key(&key) {
                return Err("duplicate handler identity".to_owned());
            }
            let compiled = CompiledHandler::compile(definition)?;
            prepared.insert(key, Arc::new(compiled));
        }
        Ok(prepared)
    }

    fn validate_definition(
        &self,
        definition: &HandlerDefinition,
        namespace: &str,
    ) -> Result<(), String> {
        let Some(owner) = self.registry.get_namespace(namespace) else {
            return Err(format!("namespace '{namespace}' does not exist"));
        };
        if owner.is_virtual() {
            return Err("handlers require a concrete namespace".into());
        }
        validate_tool_definition(definition)?;
        validate_limits(definition.limits)?;
        self.validate_grants(&definition.allowed_namespaces)
    }

    fn validate_grants(&self, namespaces: &[String]) -> Result<(), String> {
        let mut grants = HashSet::new();
        for namespace in namespaces {
            let Some(entry) = self.registry.get_namespace(namespace) else {
                return Err(format!("allowed namespace '{namespace}' does not exist"));
            };
            if entry.is_virtual() || !grants.insert(namespace) {
                return Err("allowed_namespaces must contain unique concrete namespaces".into());
            }
        }
        Ok(())
    }

    fn persist(&self, snapshot: &Snapshot) -> Result<(), String> {
        let Some(path) = &self.persistence else {
            return Ok(());
        };
        let definitions: Vec<_> = snapshot
            .values()
            .map(|handler| &handler.definition)
            .collect();
        let bytes = serde_json::to_vec_pretty(&definitions).map_err(|error| error.to_string())?;
        persist_atomic(path, &bytes)
    }
}

fn validate_tool_definition(definition: &HandlerDefinition) -> Result<(), String> {
    if definition.tool.type_ != TOOL_TYPE || definition.tool.forward_id.is_some() {
        return Err("handler tools require type 'wasm' and no forwardId".into());
    }
    if definition.tool.name.is_empty() || definition.tool.name.contains('/') {
        return Err("handler name must be nonempty and must not contain '/'".into());
    }
    if !definition.tool.input_schema.is_object() {
        return Err("inputSchema must be a JSON schema object".into());
    }
    if definition.path.as_os_str().is_empty() {
        return Err("handler component path is required".into());
    }
    Ok(())
}

fn validate_limits(limits: crate::config::HandlerLimits) -> Result<(), String> {
    if limits.fuel == 0
        || limits.memory_bytes == 0
        || limits.timeout_ms == 0
        || limits.max_concurrent == 0
    {
        return Err("handler limits must be positive".into());
    }
    if limits.max_concurrent > tokio::sync::Semaphore::MAX_PERMITS {
        return Err("handler concurrency exceeds the supported maximum".into());
    }
    Ok(())
}

fn persist_atomic(path: &Path, bytes: &[u8]) -> Result<(), String> {
    if let Some(parent) = path
        .parent()
        .filter(|parent| !parent.as_os_str().is_empty())
    {
        std::fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let temporary = path.with_extension("json.tmp");
    std::fs::write(&temporary, bytes).map_err(|error| error.to_string())?;
    std::fs::rename(&temporary, path).map_err(|error| error.to_string())
}

#[async_trait::async_trait]
impl ToolHandler for HandlerState {
    async fn call(
        &self,
        target: ToolTarget,
        arguments: Value,
        client: Arc<dyn McpHostClient>,
    ) -> Result<Value, HostError> {
        let handler = self
            .active
            .read()
            .map_err(|_| HostError::new(HostErrorKind::Unavailable, "handler runtime unavailable"))?
            .get(&(target.namespace.clone(), target.name.clone()))
            .cloned()
            .ok_or_else(|| {
                HostError::new(HostErrorKind::Unavailable, "handler binding unavailable")
            })?;
        if target.binding_id != handler.definition.tool.id {
            return Err(HostError::new(
                HostErrorKind::Unavailable,
                "handler binding changed; retry the request",
            ));
        }
        if !handler.definition.tool.enabled {
            return Err(HostError::new(HostErrorKind::Denied, "handler is disabled"));
        }
        handler.call(target, arguments, client).await
    }
}
