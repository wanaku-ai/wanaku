use serde_json::{Value, json};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use wanaku_feature_tool_handler::ToolHandlerFeature;
use wanaku_feature_tool_handler::config::HandlerDefinition;
use wanaku_feature_tool_handler::state::HandlerState;
use wanaku_infra::registry::RuntimeRegistry;
use wanaku_types::feature::{Feature, HttpContext};
use wanaku_types::wasm::{HostError, HostErrorKind, McpHostClient, ToolHandler, ToolTarget};

type TestError = Box<dyn std::error::Error>;
type TestResult = Result<(), TestError>;

struct Client {
    calls: Mutex<Vec<Value>>,
    result: Value,
}
#[async_trait::async_trait]
impl McpHostClient for Client {
    async fn request(
        &self,
        method: &str,
        namespace: &str,
        params: Value,
    ) -> Result<Value, HostError> {
        self.calls
            .lock()
            .map_err(|_| HostError::new(HostErrorKind::Internal, "lock poisoned"))?
            .push(json!({"method":method,"namespace":namespace,"params":params}));
        Ok(self.result.clone())
    }
}
fn client(result: Value) -> Arc<Client> {
    Arc::new(Client {
        calls: Mutex::new(Vec::new()),
        result,
    })
}
fn definition(path: &Path) -> Result<HandlerDefinition, serde_json::Error> {
    serde_json::from_value(
        json!({"tool":{"name":"handler","description":"test","uri":"wasm:handler","type":"wasm","inputSchema":{"type":"object"},"namespace":"default"},"path":path,"allowed_namespaces":["default"]}),
    )
}
fn target(state: &HandlerState) -> Result<ToolTarget, TestError> {
    let definition = state.get("default", "handler")?.ok_or("handler missing")?;
    Ok(ToolTarget {
        namespace: "default".into(),
        name: "handler".into(),
        binding_id: definition.tool.id,
    })
}
fn result_fixture(path: &Path, value: &Value) -> std::io::Result<()> {
    let raw = value.to_string();
    let encoded: String = raw.bytes().map(|byte| format!("\\{byte:02x}")).collect();
    let wat = include_str!("fixtures/result.wat")
        .replace("RESULT_JSON", &encoded)
        .replace("RESULT_LENGTH", &raw.len().to_string());
    std::fs::write(path, wat)
}
fn host_fixture(path: &Path, template: &str) -> std::io::Result<()> {
    let wat = template
        .replace("TARGET_NAMESPACE", "default")
        .replace("TARGET_NAME", "echo")
        .replace("NAMESPACE_LENGTH", "7")
        .replace("NAME_LENGTH", "4");
    std::fs::write(path, wat)
}

struct Runtime {
    directory: tempfile::TempDir,
    component: PathBuf,
    definition: HandlerDefinition,
    state: HandlerState,
}
impl Runtime {
    fn result(value: &Value) -> Result<Self, TestError> {
        let directory = tempfile::tempdir()?;
        let component = directory.path().join("handler.wat");
        result_fixture(&component, value)?;
        Self::new(directory, component)
    }
    fn host(template: &str) -> Result<Self, TestError> {
        let directory = tempfile::tempdir()?;
        let component = directory.path().join("handler.wat");
        host_fixture(&component, template)?;
        Self::new(directory, component)
    }
    fn new(directory: tempfile::TempDir, component: PathBuf) -> Result<Self, TestError> {
        let state = HandlerState::new(RuntimeRegistry::new());
        let definition = definition(&component)?;
        state.replace(vec![definition.clone()])?;
        Ok(Self {
            directory,
            component,
            definition,
            state,
        })
    }
    fn activate(&self) -> TestResult {
        self.state.replace(vec![self.definition.clone()])?;
        Ok(())
    }
    async fn call(
        &self,
        arguments: Value,
        client: Arc<dyn McpHostClient>,
    ) -> Result<Value, TestError> {
        Ok(self
            .state
            .call(target(&self.state)?, arguments, client)
            .await?)
    }
    async fn failure(&self, arguments: Value) -> Result<HostErrorKind, TestError> {
        Ok(self
            .state
            .call(target(&self.state)?, arguments, client(json!({})))
            .await
            .err()
            .ok_or("expected failure")?
            .kind)
    }
}

#[test]
fn excessive_concurrency_is_rejected_before_runtime_construction() -> TestResult {
    let state = HandlerState::new(RuntimeRegistry::new());
    let mut definition = definition(Path::new("unused.wasm"))?;
    definition.limits.max_concurrent = usize::MAX;
    let error = state
        .replace(vec![definition])
        .err()
        .ok_or("expected invalid concurrency")?;
    assert!(error.contains("concurrency"), "{error}");
    Ok(())
}

#[tokio::test]
async fn structured_result_survives_restart_and_removal() -> TestResult {
    let expected = json!({"content":[{"type":"image","data":"AA==","mimeType":"image/png"}],"structuredContent":{"value":true},"_meta":{"custom":1}});
    let mut runtime = Runtime::result(&expected)?;
    let persisted = runtime.directory.path().join("handlers.json");
    runtime.state = runtime.state.with_persistence(&persisted)?;
    runtime.activate()?;
    assert_eq!(
        runtime
            .call(json!({"typed":[true,1,null]}), client(json!({})))
            .await?,
        expected
    );
    let restored = HandlerState::new(RuntimeRegistry::new()).with_persistence(&persisted)?;
    assert_eq!(
        restored
            .call(target(&restored)?, json!({}), client(json!({})))
            .await?,
        expected
    );
    assert!(restored.remove("default", "handler")?);
    assert!(restored.list()?.is_empty());
    assert!(
        HandlerState::new(RuntimeRegistry::new())
            .with_persistence(&persisted)?
            .list()?
            .is_empty()
    );
    Ok(())
}

#[tokio::test]
async fn failed_replacement_retains_binding_and_successful_replacement_rejects_old_binding()
-> TestResult {
    let runtime = Runtime::result(&json!({"content":[]}))?;
    let selected = target(&runtime.state)?;
    let mut rejected = runtime.definition.clone();
    rejected.path = runtime.directory.path().join("invalid.wasm");
    std::fs::write(&rejected.path, b"invalid")?;
    assert!(runtime.state.replace(vec![rejected]).is_err());
    assert_eq!(target(&runtime.state)?.binding_id, selected.binding_id);
    runtime.activate()?;
    let error = runtime
        .state
        .call(selected, json!({}), client(json!({})))
        .await
        .err()
        .ok_or("expected stale binding")?;
    assert_eq!(error.kind, HostErrorKind::Unavailable);
    Ok(())
}

#[tokio::test]
async fn host_dispatch_preserves_json_and_enforces_capability_grants() -> TestResult {
    let mut runtime = Runtime::host(include_str!("fixtures/dispatch.wat"))?;
    let expected = json!({"content":[],"structuredContent":{"value":true},"isError":true});
    let client = client(expected.clone());
    let arguments = json!({"boolean":true,"string":"true","nested":[1,null,{"x":false}]});
    assert_eq!(
        runtime.call(arguments.clone(), client.clone()).await?,
        expected
    );
    {
        let calls = client.calls.lock().map_err(|_| "lock poisoned")?;
        assert_eq!(calls[0]["params"]["arguments"], arguments);
    }
    runtime.definition.allowed_namespaces.clear();
    runtime.activate()?;
    let error = runtime
        .state
        .call(target(&runtime.state)?, arguments, client)
        .await
        .err()
        .ok_or("expected denied grant")?;
    assert_eq!(error.kind, HostErrorKind::Denied);
    Ok(())
}

#[tokio::test]
async fn fuel_exhaustion_during_instantiation_returns_limit() -> TestResult {
    let mut runtime = Runtime::result(&json!({"content":[]}))?;
    let wat = std::fs::read_to_string(&runtime.component)?.replace("(global $next",
        "(func $startup (local $count i32) (local.set $count (i32.const 16)) (loop $again (local.set $count (i32.sub (local.get $count) (i32.const 1))) (br_if $again (local.get $count)))) (start $startup)\n(global $next");
    std::fs::write(&runtime.component, wat)?;
    runtime.definition.limits.fuel = 1;
    runtime.activate()?;
    assert_eq!(runtime.failure(json!({})).await?, HostErrorKind::Limit);
    Ok(())
}

#[tokio::test]
async fn memory_and_invalid_results_fail_closed() -> TestResult {
    let mut runtime = Runtime::result(&json!({"content":[]}))?;
    runtime.definition.limits.memory_bytes = 65536;
    runtime.activate()?;
    assert!(runtime.call(json!({}), client(json!({}))).await.is_err());
    runtime.definition.limits.memory_bytes = 1_048_576;
    result_fixture(&runtime.component, &json!({"result":{"content":[]}}))?;
    runtime.activate()?;
    assert_eq!(runtime.failure(json!({})).await?, HostErrorKind::Protocol);
    Ok(())
}

struct WaitingClient {
    entered: tokio::sync::Notify,
}
#[async_trait::async_trait]
impl McpHostClient for WaitingClient {
    async fn request(
        &self,
        _method: &str,
        _namespace: &str,
        _params: Value,
    ) -> Result<Value, HostError> {
        self.entered.notify_one();
        std::future::pending().await
    }
}

fn waiting_runtime() -> Result<Runtime, TestError> {
    let mut runtime = Runtime::host(include_str!("fixtures/dispatch.wat"))?;
    runtime.definition.limits.timeout_ms = 250;
    runtime.definition.limits.max_concurrent = 1;
    runtime.activate()?;
    Ok(runtime)
}

#[tokio::test]
async fn cancelled_requests_retain_admission_until_guest_exits() -> TestResult {
    let runtime = waiting_runtime()?;
    let waiting = Arc::new(WaitingClient {
        entered: tokio::sync::Notify::new(),
    });
    let selected = target(&runtime.state)?;
    let running = {
        let state = runtime.state.clone();
        let target = selected.clone();
        let client = waiting.clone();
        tokio::spawn(async move { state.call(target, json!({}), client).await })
    };
    tokio::time::timeout(
        std::time::Duration::from_secs(2),
        waiting.entered.notified(),
    )
    .await?;
    running.abort();
    assert_eq!(runtime.failure(json!({})).await?, HostErrorKind::Limit);
    tokio::time::sleep(std::time::Duration::from_millis(300)).await;
    assert_eq!(
        runtime
            .call(json!({}), client(json!({"content":[]})))
            .await?,
        json!({"content":[]})
    );
    Ok(())
}

#[tokio::test]
async fn host_wait_uses_handler_deadline() -> TestResult {
    let runtime = waiting_runtime()?;
    let waiting = Arc::new(WaitingClient {
        entered: tokio::sync::Notify::new(),
    });
    let error = runtime
        .state
        .call(target(&runtime.state)?, json!({}), waiting)
        .await
        .err()
        .ok_or("expected timeout")?;
    assert_eq!(error.kind, HostErrorKind::Timeout);
    Ok(())
}

#[tokio::test]
async fn catalog_access_returns_definition_metadata() -> TestResult {
    let runtime = Runtime::host(include_str!("fixtures/catalog.wat"))?;
    let expected = json!({"definition":{"inputSchema":{"type":"object"},"outputSchema":{"type":"object"},"annotations":{"readOnlyHint":true}},"identity":{"namespace":"default","name":"echo"}});
    let client = client(expected.clone());
    assert_eq!(
        runtime.call(json!({}), client.clone()).await?["structuredContent"],
        expected
    );
    assert_eq!(
        client.calls.lock().map_err(|_| "lock poisoned")?[0]["method"],
        "tools/get"
    );
    Ok(())
}

async fn management_status(
    feature: &ToolHandlerFeature,
    method: &str,
    body: Option<&str>,
) -> Result<http::StatusCode, TestError> {
    let headers = http::HeaderMap::new();
    let path = if method == "PUT" {
        "/api/v1/tool-handlers"
    } else {
        "/api/v1/tool-handlers/default/handler"
    };
    let context = HttpContext::new(method, path, None, body, &headers);
    Ok(feature
        .handle_route(&context)
        .await
        .ok_or("missing management route")?
        .status())
}

#[tokio::test]
async fn management_routes_activate_read_and_remove_bindings() -> TestResult {
    let runtime = Runtime::result(&json!({"content":[]}))?;
    let feature = ToolHandlerFeature::new(RuntimeRegistry::new());
    let body = serde_json::to_string(&vec![runtime.definition])?;
    assert_eq!(
        management_status(&feature, "PUT", Some(&body)).await?,
        http::StatusCode::OK
    );
    assert_eq!(
        management_status(&feature, "GET", None).await?,
        http::StatusCode::OK
    );
    assert_eq!(
        management_status(&feature, "DELETE", None).await?,
        http::StatusCode::OK
    );
    assert_eq!(
        management_status(&feature, "GET", None).await?,
        http::StatusCode::NOT_FOUND
    );
    Ok(())
}

#[tokio::test]
async fn selected_input_and_output_schemas_are_enforced() -> TestResult {
    let mut runtime =
        Runtime::result(&json!({"content":[],"structuredContent":{"value":"wrong"}}))?;
    let schema =
        json!({"type":"object","properties":{"value":{"type":"boolean"}},"required":["value"]});
    runtime.definition.tool.input_schema = schema.clone();
    runtime.definition.tool.mcp.output_schema = Some(schema);
    runtime.activate()?;
    assert_eq!(
        runtime.failure(json!({"value":"true"})).await?,
        HostErrorKind::InvalidArguments
    );
    assert_eq!(
        runtime.failure(json!({"value":true})).await?,
        HostErrorKind::Protocol
    );
    Ok(())
}

#[test]
fn invalid_schema_rejects_activation_without_changing_binding() -> TestResult {
    let mut runtime = Runtime::result(&json!({"content":[]}))?;
    let selected = target(&runtime.state)?;
    runtime.definition.tool.input_schema = json!({"type":"nonexistent"});
    assert!(runtime.activate().is_err());
    assert_eq!(target(&runtime.state)?.binding_id, selected.binding_id);
    Ok(())
}
