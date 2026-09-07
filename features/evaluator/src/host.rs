use wasmtime::component::{bindgen, HasSelf};

bindgen!({
    path: "wit/evaluator.wit",
    world: "evaluator-action",
});

pub use wanaku::evaluator::types;

use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::interactions::{InMemoryInteractionStore, InteractionStore};
use wanaku_types::registry::ToolRegistry;

use std::sync::Arc;

use crate::action::ActionResult;
use crate::schema::CompiledSchema;

/// Host state available to the WASM guest during evaluation.
pub struct HostState {
    pub registry: InMemoryRegistry,
    pub interactions: InMemoryInteractionStore,
    pub action: ActionResult,
    pub evaluator_name: String,
    pub compiled_schema: Option<Arc<CompiledSchema>>,
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

    fn reject_malformed(&mut self, reason: String) {
        self.action = ActionResult::RejectMalformed(reason);
    }

    fn set_metadata(&mut self, key: String, value: String) {
        self.action = ActionResult::SetMetadata(key, value);
    }
}

impl wanaku::evaluator::validation::Host for HostState {
    fn verify_llm_result(&mut self, raw: String) -> Result<String, String> {
        let Some(schema) = &self.compiled_schema else {
            return Ok(raw);
        };
        schema.validate(&raw).map(|()| raw)
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

fn tool_entry_to_wit(t: wanaku_types::registry::ToolEntry) -> types::ToolEntry {
    types::ToolEntry {
        name: t.name,
        description: t.description,
        uri: t.uri,
        tool_type: t.type_,
        namespace: t.namespace,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    use http::StatusCode;
    use wanaku::evaluator::conversation::Host as _;
    use wanaku::evaluator::registry::Host as _;
    use wanaku::evaluator::response::Host as _;
    use wanaku::evaluator::validation::Host as _;
    use wanaku_types::interactions::Interaction;

    fn host_state() -> HostState {
        HostState {
            registry: InMemoryRegistry::new(),
            interactions: InMemoryInteractionStore::new(16),
            action: ActionResult::Pass,
            evaluator_name: "test-eval".to_owned(),
            compiled_schema: None,
            wasi_ctx: wasmtime_wasi::WasiCtxBuilder::new().build(),
            wasi_table: wasmtime::component::ResourceTable::new(),
        }
    }

    fn sample_tool(name: &str) -> wanaku_types::registry::ToolEntry {
        wanaku_types::registry::ToolEntry {
            name: name.to_owned(),
            description: "desc".to_owned(),
            uri: "uri".to_owned(),
            type_: "http".to_owned(),
            input_schema: serde_json::Value::Null,
            labels: HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        }
    }

    // ---- response action setters ----

    #[test]
    fn response_pass_sets_pass() {
        let mut state = host_state();
        state.block("bad".to_owned());
        state.pass();
        assert!(matches!(state.action, ActionResult::Pass));
    }

    #[test]
    fn response_block_sets_block_with_reason() {
        let mut state = host_state();
        state.block("policy violation".to_owned());
        assert!(matches!(state.action, ActionResult::Block(r) if r == "policy violation"));
    }

    #[test]
    fn response_warn_sets_warn() {
        let mut state = host_state();
        state.warn("heads up".to_owned());
        assert!(matches!(state.action, ActionResult::Warn(m) if m == "heads up"));
    }

    #[test]
    fn response_filter_tools_sets_filter() {
        let mut state = host_state();
        state.filter_tools(vec!["a".to_owned(), "b".to_owned()]);
        assert!(matches!(state.action, ActionResult::FilterTools(names) if names == vec!["a", "b"]));
    }

    #[test]
    fn response_reject_malformed_sets_reject() {
        let mut state = host_state();
        state.reject_malformed("bad json".to_owned());
        assert!(matches!(state.action, ActionResult::RejectMalformed(r) if r == "bad json"));
    }

    #[test]
    fn response_set_metadata_sets_metadata() {
        let mut state = host_state();
        state.set_metadata("k".to_owned(), "v".to_owned());
        assert!(matches!(state.action, ActionResult::SetMetadata(k, v) if k == "k" && v == "v"));
    }

    // ---- validation ----

    #[test]
    fn verify_llm_result_passes_through_without_schema() {
        let mut state = host_state();
        let raw = "anything goes".to_owned();
        assert_eq!(state.verify_llm_result(raw.clone()), Ok(raw));
    }

    // ---- registry lookups ----

    #[test]
    fn list_tools_reflects_registry() {
        let mut state = host_state();
        state.registry.register_tool(sample_tool("alpha"));
        let tools = state.list_tools();
        assert_eq!(tools.len(), 1);
        assert_eq!(tools[0].name, "alpha");
        assert_eq!(tools[0].tool_type, "http");
    }

    #[test]
    fn get_tool_returns_none_for_missing() {
        let mut state = host_state();
        assert!(state.get_tool("missing".to_owned()).is_none());
    }

    #[test]
    fn get_tool_returns_registered() {
        let mut state = host_state();
        state.registry.register_tool(sample_tool("alpha"));
        let entry = state.get_tool("alpha".to_owned()).expect("tool present");
        assert_eq!(entry.name, "alpha");
    }

    #[test]
    fn copy_tool_to_namespace_moves_and_reports() {
        let mut state = host_state();
        state.registry.register_tool(sample_tool("alpha"));
        assert!(state.copy_tool_to_namespace("alpha".to_owned(), "prod".to_owned()));
        let copied = state
            .get_tool("alpha".to_owned())
            .expect("copied tool present");
        assert_eq!(copied.namespace.as_deref(), Some("prod"));
        assert!(!state.copy_tool_to_namespace("missing".to_owned(), "prod".to_owned()));
    }

    // ---- conversation history parsing ----

    fn interaction(conv: &str, messages: &serde_json::Value) -> Interaction {
        Interaction {
            epoch_ms: 0,
            path: "/v1/chat/completions".to_owned(),
            conversation_id: Some(conv.to_owned()),
            completion_id: None,
            model: None,
            request_body: serde_json::json!({ "messages": messages }),
            response_body: serde_json::Value::Null,
            status_code: StatusCode::OK.as_u16(),
            duration_ms: 0,
        }
    }

    #[test]
    fn get_history_parses_messages() {
        let mut state = host_state();
        state.interactions.record(interaction(
            "wk-1",
            &serde_json::json!([
                {"role": "user", "content": "hi"},
                {"role": "assistant", "content": "hello"},
            ]),
        ));

        let history = state.get_history("wk-1".to_owned());
        assert_eq!(history.len(), 2);
        assert_eq!(history[0].role, "user");
        assert_eq!(history[0].content, "hi");
        assert_eq!(history[1].role, "assistant");
        assert_eq!(history[1].content, "hello");
    }

    #[test]
    fn get_history_skips_empty_content_and_defaults_role() {
        let mut state = host_state();
        state.interactions.record(interaction(
            "wk-1",
            &serde_json::json!([
                {"role": "user", "content": ""},
                {"content": "no role"},
            ]),
        ));

        let history = state.get_history("wk-1".to_owned());
        assert_eq!(history.len(), 1);
        assert_eq!(history[0].role, "unknown");
        assert_eq!(history[0].content, "no role");
    }

    #[test]
    fn get_history_empty_for_unknown_conversation() {
        let mut state = host_state();
        assert!(state.get_history("nope".to_owned()).is_empty());
    }

    // ---- tool_entry_to_wit ----

    #[test]
    fn tool_entry_to_wit_maps_fields() {
        let mut src = sample_tool("t1");
        src.namespace = Some("ns".to_owned());
        let wit = tool_entry_to_wit(src);
        assert_eq!(wit.name, "t1");
        assert_eq!(wit.description, "desc");
        assert_eq!(wit.uri, "uri");
        assert_eq!(wit.tool_type, "http");
        assert_eq!(wit.namespace.as_deref(), Some("ns"));
    }
}
