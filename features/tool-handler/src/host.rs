use std::sync::Arc;
use std::time::Instant;

use serde_json::{Value, json};
use wanaku_types::wasm::{HostError as RuntimeHostError, HostErrorKind, McpHostClient};
use wasmtime::component::{HasSelf, Linker, ResourceTable, bindgen};
use wasmtime_wasi::{WasiCtx, WasiCtxView, WasiView};

bindgen!({ path: "wit/tool-handler.wit", world: "tool-handler" });

pub use wanaku::tool_handler::types;

type GuestResult = Result<String, types::HostError>;

pub struct HostState {
    pub client: Arc<dyn McpHostClient>,
    pub allowed_namespaces: Vec<String>,
    pub deadline: Instant,
    pub runtime: tokio::runtime::Handle,
    pub wasi: WasiCtx,
    pub table: ResourceTable,
    pub limits: wasmtime::StoreLimits,
}

impl HostState {
    fn request(&self, method: &str, namespace: &str, params: Value) -> GuestResult {
        if !self
            .allowed_namespaces
            .iter()
            .any(|allowed| allowed == namespace)
        {
            return Err(to_guest_error(RuntimeHostError::new(
                HostErrorKind::Denied,
                "namespace is outside the handler capability grant",
            )));
        }
        let deadline = tokio::time::Instant::from_std(self.deadline);
        let result = self.runtime.block_on(async {
            tokio::time::timeout_at(deadline, self.client.request(method, namespace, params))
                .await
                .map_err(|_| {
                    RuntimeHostError::new(HostErrorKind::Timeout, "handler deadline exceeded")
                })?
        });
        result
            .map(|value| value.to_string())
            .map_err(to_guest_error)
    }
}

pub fn to_guest_error(error: RuntimeHostError) -> types::HostError {
    let kind = serde_json::to_value(error.kind)
        .ok()
        .and_then(|value| value.as_str().map(str::to_owned))
        .unwrap_or_else(|| "internal".to_owned());
    types::HostError {
        kind,
        message: error.message,
        code: error.code,
    }
}

pub fn from_guest_error(error: types::HostError) -> RuntimeHostError {
    let kind =
        serde_json::from_value(Value::String(error.kind)).unwrap_or(HostErrorKind::Execution);
    RuntimeHostError {
        kind,
        message: error.message,
        code: error.code,
    }
}

fn parse_arguments(arguments: &str) -> Result<Value, types::HostError> {
    let parsed: Value = serde_json::from_str(arguments).map_err(|_| {
        to_guest_error(RuntimeHostError::new(
            HostErrorKind::InvalidArguments,
            "arguments must contain valid JSON",
        ))
    })?;
    if !parsed.is_object() {
        return Err(to_guest_error(RuntimeHostError::new(
            HostErrorKind::InvalidArguments,
            "arguments must be a JSON object",
        )));
    }
    Ok(parsed)
}

impl wanaku::tool_handler::catalog::Host for HostState {
    fn list_tools(&mut self, namespace: String) -> GuestResult {
        self.request("tools/list", &namespace, json!({}))
    }

    fn get_tool(&mut self, namespace: String, name: String) -> GuestResult {
        self.request("tools/get", &namespace, json!({"name":name}))
    }

    fn list_resources(&mut self, namespace: String) -> GuestResult {
        self.request("resources/list", &namespace, json!({}))
    }

    fn list_resource_templates(&mut self, namespace: String) -> GuestResult {
        self.request("resources/templates/list", &namespace, json!({}))
    }

    fn list_prompts(&mut self, namespace: String) -> GuestResult {
        self.request("prompts/list", &namespace, json!({}))
    }
}

impl wanaku::tool_handler::mcp::Host for HostState {
    fn call_tool(&mut self, namespace: String, name: String, arguments: String) -> GuestResult {
        let arguments = parse_arguments(&arguments)?;
        self.request(
            "tools/call",
            &namespace,
            json!({"name":name,"arguments":arguments}),
        )
    }

    fn read_resource(&mut self, namespace: String, uri: String) -> GuestResult {
        self.request("resources/read", &namespace, json!({"uri":uri}))
    }

    fn get_prompt(&mut self, namespace: String, name: String, arguments: String) -> GuestResult {
        let arguments = parse_arguments(&arguments)?;
        self.request(
            "prompts/get",
            &namespace,
            json!({"name":name,"arguments":arguments}),
        )
    }
}

impl wanaku::tool_handler::log::Host for HostState {
    fn info(&mut self, message: String) {
        tracing::info!(target:"wanaku::tool_handler::guest", %message);
    }
    fn warn(&mut self, message: String) {
        tracing::warn!(target:"wanaku::tool_handler::guest", %message);
    }
    fn error(&mut self, message: String) {
        tracing::error!(target:"wanaku::tool_handler::guest", %message);
    }
}

impl WasiView for HostState {
    fn ctx(&mut self) -> WasiCtxView<'_> {
        WasiCtxView {
            ctx: &mut self.wasi,
            table: &mut self.table,
        }
    }
}

pub fn link(linker: &mut Linker<HostState>) -> Result<(), String> {
    wasmtime_wasi::p2::add_to_linker_sync::<HostState>(linker)
        .map_err(|error| error.to_string())?;
    wanaku::tool_handler::catalog::add_to_linker::<HostState, HasSelf<HostState>>(
        linker,
        |state| state,
    )
    .map_err(|error| error.to_string())?;
    wanaku::tool_handler::mcp::add_to_linker::<HostState, HasSelf<HostState>>(linker, |state| {
        state
    })
    .map_err(|error| error.to_string())?;
    wanaku::tool_handler::log::add_to_linker::<HostState, HasSelf<HostState>>(linker, |state| {
        state
    })
    .map_err(|error| error.to_string())?;
    Ok(())
}
