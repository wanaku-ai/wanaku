use wasmtime::component::{bindgen, HasSelf};

bindgen!({
    path: "wit/evaluator.wit",
    world: "evaluator-action",
});

pub use wanaku::evaluator::types;

use wanaku_praxis_apis::interactions::{InMemoryInteractionStore, InteractionStore};
use wanaku_praxis_apis::registry::{InMemoryRegistry, ToolRegistry};

use crate::action::ActionResult;

/// Host state available to the WASM guest during evaluation.
pub struct HostState {
    pub registry: InMemoryRegistry,
    pub interactions: InMemoryInteractionStore,
    pub action: ActionResult,
    pub evaluator_name: String,
    pub wasi_ctx: wasmtime_wasi::WasiCtx,
    pub wasi_table: wasmtime::component::ResourceTable,
}

impl wanaku::evaluator::registry::Host for HostState {
    fn list_tools(&mut self) -> Vec<types::ToolEntry> {
        self.registry
            .list_tools()
            .into_iter()
            .map(tool_entry_to_wit)
            .collect()
    }

    fn list_tools_in_namespace(&mut self, namespace: String) -> Vec<types::ToolEntry> {
        self.registry
            .list_tools_in_namespace(&namespace)
            .into_iter()
            .map(tool_entry_to_wit)
            .collect()
    }

    fn get_tool(&mut self, name: String) -> Option<types::ToolEntry> {
        self.registry.get_tool(&name).map(tool_entry_to_wit)
    }

    fn copy_tool_to_namespace(&mut self, tool_name: String, target_namespace: String) -> bool {
        if let Some(mut tool) = self.registry.get_tool(&tool_name) {
            tool.namespace = Some(target_namespace);
            self.registry.register_tool(tool);
            true
        } else {
            false
        }
    }
}

impl wanaku::evaluator::conversation::Host for HostState {
    fn get_history(&mut self, conversation_id: String) -> Vec<types::Message> {
        let interactions = self.interactions.get_by_conversation_id(&conversation_id);
        let mut messages = Vec::new();

        for interaction in &interactions {
            if let Some(msg_array) = interaction
                .request_body
                .get("messages")
                .and_then(|m| m.as_array())
            {
                for msg in msg_array {
                    let role = msg
                        .get("role")
                        .and_then(serde_json::Value::as_str)
                        .unwrap_or("unknown")
                        .to_owned();
                    let content = msg
                        .get("content")
                        .and_then(serde_json::Value::as_str)
                        .unwrap_or("")
                        .to_owned();
                    if !content.is_empty() {
                        messages.push(types::Message { role, content });
                    }
                }
            }
        }

        messages
    }
}

impl wanaku::evaluator::response::Host for HostState {
    fn pass(&mut self) {
        self.action = ActionResult::Pass;
    }

    fn block(&mut self, reason: String) {
        self.action = ActionResult::Block(reason);
    }

    fn warn(&mut self, message: String) {
        self.action = ActionResult::Warn(message);
    }

    fn filter_tools(&mut self, tool_names: Vec<String>) {
        self.action = ActionResult::FilterTools(tool_names);
    }

    fn set_metadata(&mut self, key: String, value: String) {
        self.action = ActionResult::SetMetadata(key, value);
    }
}

impl wanaku::evaluator::log::Host for HostState {
    fn info(&mut self, message: String) {
        tracing::info!(evaluator = %self.evaluator_name, "{message}");
    }

    fn warn(&mut self, message: String) {
        tracing::warn!(evaluator = %self.evaluator_name, "{message}");
    }

    fn error(&mut self, message: String) {
        tracing::error!(evaluator = %self.evaluator_name, "{message}");
    }
}

impl wanaku::evaluator::types::Host for HostState {}

/// Link the evaluator action bindings using HasSelf (no projection needed).
pub fn link(linker: &mut wasmtime::component::Linker<HostState>) -> Result<(), String> {
    EvaluatorAction::add_to_linker::<_, HasSelf<HostState>>(linker, |state| state)
        .map_err(|e| format!("failed to link host functions: {e}"))
}

fn tool_entry_to_wit(t: wanaku_praxis_apis::registry::ToolEntry) -> types::ToolEntry {
    types::ToolEntry {
        name: t.name,
        description: t.description,
        uri: t.uri,
        tool_type: t.type_,
        namespace: t.namespace,
    }
}
