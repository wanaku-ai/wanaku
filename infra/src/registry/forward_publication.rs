use std::collections::{HashMap, HashSet};
use std::sync::atomic::Ordering;
use std::sync::{Arc, Weak};

use dashmap::DashMap;
use wanaku_types::registry::{
    DEFAULT_NAMESPACE, ForwardEntry, ForwardRegistry, PromptEntry, ResourceEntry, ToolEntry,
    inject_request_id_arg,
};

use super::{EntryKey, RuntimeRegistry};

/// An in-flight discovery request captured before contacting an upstream.
pub struct ForwardDiscoveryRequest {
    registry: RuntimeRegistry,
    target: ForwardEntry,
    previous: Option<ForwardEntry>,
    generation: Arc<()>,
    bindings: HashMap<String, Arc<()>>,
}

impl ForwardDiscoveryRequest {
    /// Registry that supplied this request's configuration and identity.
    #[must_use]
    pub const fn registry(&self) -> &RuntimeRegistry {
        &self.registry
    }

    /// Configuration to use for discovery.
    #[must_use]
    pub const fn target(&self) -> &ForwardEntry {
        &self.target
    }

    /// Stored registration that a registration request would replace.
    #[must_use]
    pub const fn previous(&self) -> Option<&ForwardEntry> {
        self.previous.as_ref()
    }
}

/// A complete set of validated capabilities from discovery.
pub struct ForwardCapabilities {
    pub tools: Vec<ToolEntry>,
    pub resources: Vec<ResourceEntry>,
    pub prompts: Vec<PromptEntry>,
}

/// The identity and capabilities from a complete discovery.
pub struct DiscoveredForward {
    pub server_info: Option<wanaku_types::registry::McpServerInfo>,
    pub capabilities: ForwardCapabilities,
}

/// The stored forward and the number of entries published in its inventory.
#[derive(Debug)]
pub struct PublishedForwardUpdate {
    pub forward: ForwardEntry,
    pub counts: PublishedCounts,
}

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub struct PublishedCounts {
    pub tools: usize,
    pub resources: usize,
    pub prompts: usize,
}

#[derive(Debug, PartialEq, Eq, thiserror::Error)]
pub enum ForwardPublicationError {
    #[error("forward already exists: {0}")]
    AlreadyExists(String),
    #[error("forward changed during discovery: {0}")]
    Stale(String),
    #[error("{0}")]
    InvalidNamespace(String),
    #[error("{0}")]
    ReplacementFailed(String),
}

/// Track only registrations and bindings with live discovery requests. Remove
/// the token on mutation, including mutations that restore identical values.
#[derive(Default)]
pub(super) struct ForwardChanges {
    pub(super) forwards: HashMap<String, Weak<()>>,
    pub(super) bindings: HashMap<String, Weak<()>>,
}

fn capture(changes: &mut HashMap<String, Weak<()>>, id: &str) -> Arc<()> {
    if let Some(generation) = changes.get(id).and_then(Weak::upgrade) {
        return generation;
    }
    let generation = Arc::new(());
    changes.insert(id.to_owned(), Arc::downgrade(&generation));
    generation
}

fn is_current(changes: &HashMap<String, Weak<()>>, id: &str, generation: &Arc<()>) -> bool {
    changes
        .get(id)
        .is_some_and(|current| current.ptr_eq(&Arc::downgrade(generation)))
}

impl ForwardChanges {
    fn is_current(&self, request: &ForwardDiscoveryRequest) -> bool {
        is_current(&self.forwards, &request.target.name, &request.generation)
            && request
                .bindings
                .iter()
                .all(|(id, generation)| is_current(&self.bindings, id, generation))
    }
    fn request(
        &mut self,
        registry: &RuntimeRegistry,
        target: ForwardEntry,
        previous: Option<ForwardEntry>,
    ) -> ForwardDiscoveryRequest {
        self.forwards.retain(|_, token| token.strong_count() > 0);
        self.bindings.retain(|_, token| token.strong_count() > 0);
        let generation = capture(&mut self.forwards, &target.name);
        let bindings = target
            .credential_bindings
            .values()
            .map(|id| (id.clone(), capture(&mut self.bindings, id)))
            .collect();
        ForwardDiscoveryRequest {
            registry: registry.clone(),
            target,
            previous,
            generation,
            bindings,
        }
    }
}

impl RuntimeRegistry {
    /// Capture the current forward configuration and relevant binding identities
    /// for refresh. The registry releases the lock before returning the request.
    pub fn begin_forward_refresh(&self, name: &str) -> Option<ForwardDiscoveryRequest> {
        let mut changes = self.lock_namespaces();
        let target = self.get_forward(name)?;
        Some(changes.request(self, target, None))
    }

    /// Capture the state required to create or replace a forward. The candidate
    /// remains outside the registry until a prepared result is published.
    pub fn begin_forward_registration(
        &self,
        target: ForwardEntry,
        replace: bool,
    ) -> Result<ForwardDiscoveryRequest, ForwardPublicationError> {
        let mut changes = self.lock_namespaces();
        self.validate_concrete_namespace(namespace(&target))
            .map_err(ForwardPublicationError::InvalidNamespace)?;
        let previous = self.get_forward(&target.name);
        if previous.is_some() && !replace {
            return Err(ForwardPublicationError::AlreadyExists(target.name));
        }
        Ok(changes.request(self, target, previous))
    }

    /// Conditionally publish a prepared discovery result. Validation, capability
    /// replacement, and the status write share one lock with configuration writes
    /// and removal. A failed refresh retains the inventory, and a failed
    /// replacement retains both the previous inventory and registration.
    pub fn publish_forward_discovery(
        &self,
        request: ForwardDiscoveryRequest,
        result: Result<DiscoveredForward, String>,
    ) -> Result<PublishedForwardUpdate, ForwardPublicationError> {
        let mut changes = self.lock_namespaces();
        if !changes.is_current(&request) {
            return Err(ForwardPublicationError::Stale(request.target.name));
        }
        let mut target = request.target;
        let namespace = namespace(&target).to_owned();
        self.validate_concrete_namespace(&namespace)
            .map_err(ForwardPublicationError::InvalidNamespace)?;

        let _persistence_guard = self.lock_persistence();
        let counts = self.apply_discovery_result(
            &mut target,
            &namespace,
            result,
            request.previous.is_some(),
        )?;
        changes.forwards.remove(&target.name);
        target.namespace = Some(namespace);
        self.insert_forward(target.clone());
        self.persist_locked();
        Ok(PublishedForwardUpdate {
            forward: target,
            counts,
        })
    }

    fn apply_discovery_result(
        &self,
        target: &mut ForwardEntry,
        namespace: &str,
        result: Result<DiscoveredForward, String>,
        replacing: bool,
    ) -> Result<PublishedCounts, ForwardPublicationError> {
        let discovered = match result {
            Ok(discovered) => discovered,
            Err(message) if replacing => {
                return Err(ForwardPublicationError::ReplacementFailed(message));
            }
            Err(message) => {
                target.available = false;
                target.status_message = Some(message);
                return Ok(PublishedCounts::default());
            }
        };

        // Invocation readers resolve the owner by name without this lock.
        // Block calls while its configuration and inventory are changing.
        target.available = false;
        self.insert_forward(target.clone());
        let (counts, skipped) =
            self.replace_forward_capabilities(&target.name, namespace, discovered.capabilities);
        target.server_info = discovered.server_info;
        target.available = true;
        target.status_message = skipped_status(namespace, &skipped);
        Ok(counts)
    }

    pub(super) fn remove_forward_capabilities(&self, forward_id: &str) {
        self.tools
            .retain(|_, entry| entry.forward_id.as_deref() != Some(forward_id));
        self.resources
            .retain(|_, entry| entry.forward_id.as_deref() != Some(forward_id));
        self.prompts
            .retain(|_, entry| entry.forward_id.as_deref() != Some(forward_id));
    }

    #[expect(
        clippy::too_many_lines,
        reason = "replace all three capability kinds in one publication"
    )]
    fn replace_forward_capabilities(
        &self,
        forward_id: &str,
        namespace: &str,
        capabilities: ForwardCapabilities,
    ) -> (PublishedCounts, Vec<String>) {
        let mut skipped = Vec::new();
        let inject = self.inject_request_id.load(Ordering::Relaxed);
        let tools = Inventory::new(&self.tools, "tool", forward_id, namespace).replace(
            capabilities.tools,
            &mut skipped,
            |tool| (&tool.name, tool.forward_id.as_deref(), tool.enabled),
            |tool, enabled| {
                tool.forward_id = Some(forward_id.to_owned());
                tool.namespace = Some(namespace.to_owned());
                tool.enabled = enabled;
                if inject {
                    inject_request_id_arg(&mut tool.input_schema);
                }
            },
        );
        let resources = Inventory::new(&self.resources, "resource", forward_id, namespace).replace(
            capabilities.resources,
            &mut skipped,
            |resource| {
                (
                    &resource.name,
                    resource.forward_id.as_deref(),
                    resource.enabled,
                )
            },
            |resource, enabled| {
                resource.forward_id = Some(forward_id.to_owned());
                resource.namespace = Some(namespace.to_owned());
                resource.enabled = enabled;
            },
        );
        let prompts = Inventory::new(&self.prompts, "prompt", forward_id, namespace).replace(
            capabilities.prompts,
            &mut skipped,
            |prompt| (&prompt.name, prompt.forward_id.as_deref(), prompt.enabled),
            |prompt, enabled| {
                prompt.forward_id = Some(forward_id.to_owned());
                prompt.namespace = Some(namespace.to_owned());
                prompt.enabled = enabled;
            },
        );
        (
            PublishedCounts {
                tools,
                resources,
                prompts,
            },
            skipped,
        )
    }
}

fn namespace(forward: &ForwardEntry) -> &str {
    forward.namespace.as_deref().unwrap_or(DEFAULT_NAMESPACE)
}

type InventoryMetadata<T> = fn(&T) -> (&str, Option<&str>, bool);

struct Inventory<'a, T> {
    entries: &'a DashMap<EntryKey, T>,
    kind: &'static str,
    forward_id: &'a str,
    namespace: &'a str,
}

impl<'a, T> Inventory<'a, T> {
    const fn new(
        entries: &'a DashMap<EntryKey, T>,
        kind: &'static str,
        forward_id: &'a str,
        namespace: &'a str,
    ) -> Self {
        Self {
            entries,
            kind,
            forward_id,
            namespace,
        }
    }

    fn replace(
        &self,
        incoming: Vec<T>,
        skipped: &mut Vec<String>,
        metadata: InventoryMetadata<T>,
        set_metadata: impl Fn(&mut T, bool),
    ) -> usize {
        let mut disabled = HashSet::new();
        self.entries.retain(|_, entry| {
            let (name, owner, enabled) = metadata(entry);
            let owned = owner == Some(self.forward_id);
            if owned && !enabled {
                disabled.insert(name.to_owned());
            }
            !owned
        });

        let mut count = 0;
        for mut entry in incoming {
            let name = metadata(&entry).0.to_owned();
            set_metadata(&mut entry, !disabled.contains(&name));
            let key = super::entry_key(self.namespace, &name);
            if super::insert_unless_taken(self.entries, key, entry, |entry| metadata(entry).1)
                .is_some()
            {
                skipped.push(format!("{} {name}", self.kind));
            } else {
                count += 1;
            }
        }
        count
    }
}

fn skipped_status(namespace: &str, skipped: &[String]) -> Option<String> {
    (!skipped.is_empty()).then(|| {
        format!(
            "skipped {} discovered entries because other owners already use the names in namespace {namespace}: {}",
            skipped.len(),
            skipped.join(", ")
        )
    })
}
