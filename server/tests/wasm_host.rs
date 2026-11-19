//! Production assembly checks for WASM host calls and nested MCP governance.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use bytes::Bytes;
use http::{HeaderName, HeaderValue};
use praxis_filter::FilterAction;
use praxis_protocol::http::pingora::context::PingoraRequestCtx;
use serde_json::{Value, json};
use wanaku_infra::credentials::CredentialBroker;
use wanaku_infra::mcp_client::{CallToolResponse, McpClientError, McpInvoker};
use wanaku_server::app::{App, AppPersistence, AppResources, AppSettings};
use wanaku_types::credentials::fake_resolver::FakeResolver;
use wanaku_types::credentials::{CredentialBinding, ResolverRegistry};
use wanaku_types::feature::HttpContext;
use wanaku_types::governance::{FailureBehavior, GovernanceConfig, NoMatchBehavior};
use wanaku_types::registry::{BindingRegistry, ForwardRegistry, ToolRegistry};

type Error = Box<dyn std::error::Error + Send + Sync>;
type TestResult = Result<(), Error>;
const SECRET: &str = "wasm-host-test-secret";

#[derive(Clone, Debug)]
struct Invocation {
    address: String,
    name: String,
    arguments: Value,
    headers: HashMap<HeaderName, HeaderValue>,
}

struct RecordingInvoker {
    calls: Mutex<Vec<Invocation>>,
    result: Value,
}

impl RecordingInvoker {
    const fn new(result: Value) -> Self {
        Self {
            calls: Mutex::new(Vec::new()),
            result,
        }
    }

    fn calls(&self) -> Result<Vec<Invocation>, Error> {
        self.calls
            .lock()
            .map(|calls| calls.clone())
            .map_err(|_| "recording lock poisoned".into())
    }
}

#[async_trait::async_trait]
impl McpInvoker for RecordingInvoker {
    async fn call_tool(
        &self,
        address: &str,
        name: &str,
        arguments: Value,
        headers: HashMap<HeaderName, HeaderValue>,
    ) -> Result<CallToolResponse, McpClientError> {
        self.calls
            .lock()
            .map_err(|_| McpClientError::Connection {
                url: address.to_owned(),
                message: "recording lock poisoned".to_owned(),
            })?
            .push(Invocation {
                address: address.to_owned(),
                name: name.to_owned(),
                arguments,
                headers,
            });
        Ok(CallToolResponse {
            result: self.result.clone(),
            is_error: self.result["isError"].as_bool().unwrap_or(false),
        })
    }

    async fn read_resource(
        &self,
        address: &str,
        _uri: &str,
        _headers: HashMap<HeaderName, HeaderValue>,
    ) -> Result<Value, McpClientError> {
        Err(McpClientError::Connection {
            url: address.to_owned(),
            message: "unexpected resource read".to_owned(),
        })
    }

    async fn get_prompt(
        &self,
        address: &str,
        _name: &str,
        _arguments: Option<serde_json::Map<String, Value>>,
        _headers: HashMap<HeaderName, HeaderValue>,
    ) -> Result<Value, McpClientError> {
        Err(McpClientError::Connection {
            url: address.to_owned(),
            message: "unexpected prompt request".to_owned(),
        })
    }
}

fn app() -> Result<App, Error> {
    let pipeline = wanaku_server::load_config(None)?;
    let mut governance = GovernanceConfig::default();
    // Unconfigured evaluators do not obscure the explicit target-policy checks.
    governance.default.no_match = NoMatchBehavior::Allow;
    governance.default.on_failure = FailureBehavior::Allow;
    let resolvers = ResolverRegistry::new()
        .with_resolver(Arc::new(FakeResolver::new().with_value("token", SECRET)));
    Ok(App::build(
        &AppSettings {
            pipeline: &pipeline,
            wanaku_config: None,
            governance,
            plugins_dir: None,
        },
        AppResources {
            persistence: AppPersistence::default(),
            broker: Arc::new(CredentialBroker::new(resolvers)),
        },
    )?)
}

async fn manage(app: &App, method: &str, path: &str, body: Option<Value>) -> Result<Value, Error> {
    let headers = http::HeaderMap::new();
    let body = body.map(|body| body.to_string());
    let response = app
        .management
        .dispatch(&HttpContext::new(
            method,
            path,
            None,
            body.as_deref(),
            &headers,
        ))
        .await;
    let payload: Value = serde_json::from_slice(response.body())?;
    if !response.status().is_success() {
        return Err(format!("{method} {path}: {payload}").into());
    }
    Ok(payload)
}

async fn setup_namespaces(app: &App) -> TestResult {
    for name in ["handlers", "kafka", "flink", "private"] {
        Box::pin(manage(
            app,
            "POST",
            "/api/v1/namespaces",
            Some(json!({"name":name})),
        ))
        .await?;
    }
    Box::pin(manage(
        app,
        "POST",
        "/api/v1/namespaces",
        Some(json!({"name":"integration","members":["handlers","kafka","flink"]})),
    ))
    .await?;
    Ok(())
}

fn backend_tool(namespace: &str) -> Result<wanaku_types::registry::ToolEntry, Error> {
    Ok(serde_json::from_value(json!({
        "name":"publish", "description":"Publish typed values", "namespace":namespace,
        "uri":"publish", "type":"mcp-forward", "forwardId":format!("{namespace}-forward"),
        "inputSchema":{"type":"object", "properties":{"payload":{"type":"object"}}, "required":["payload"]},
        "outputSchema":{"type":"object"}, "annotations":{"idempotentHint":true},
        "_meta":{"example/owner":namespace}
    }))?)
}

fn register_backend(app: &App, namespace: &str) -> TestResult {
    app.registry.register_forward(serde_json::from_value(json!({
        "name":format!("{namespace}-forward"), "namespace":namespace,
        "address":format!("https://{namespace}.example/mcp"),
        "credentialBindings": {"invocation":format!("{namespace}-binding")}
    }))?);
    app.registry.register_tool(backend_tool(namespace)?);
    let binding: CredentialBinding = serde_json::from_value(json!({
        "id":format!("{namespace}-binding"), "forwardId":format!("{namespace}-forward"),
        "origin":format!("https://{namespace}.example:443"), "mechanism":{"type":"bearer"},
        "secretRefs":["fake:token"], "allowedPurposes":["invocation"],
        "restrictions":{"namespaces":[namespace],"governed_items":["publish"],"operations":["tools/call"]}
    }))?;
    app.registry.register_binding(binding);
    Ok(())
}

async fn setup(app: &App) -> TestResult {
    setup_namespaces(app).await?;
    for namespace in ["kafka", "flink", "private"] {
        register_backend(app, namespace)?;
    }
    Ok(())
}

fn virtual_request() -> Result<praxis_filter::Request, Error> {
    let mut headers = http::HeaderMap::new();
    headers.insert(
        http::header::CONTENT_TYPE,
        HeaderValue::from_static("application/json"),
    );
    headers.insert(
        "x-request-id",
        HeaderValue::from_static("wasm-host-correlation"),
    );
    Ok(praxis_filter::Request {
        method: http::Method::POST,
        uri: "/virtual-ns/integration/mcp".parse()?,
        headers,
    })
}

async fn invoke(
    app: &App,
    invoker: Arc<RecordingInvoker>,
    arguments: Value,
) -> Result<Value, Error> {
    let swap = app.pipelines.get("mcp").ok_or("missing MCP pipeline")?;
    let mut protocol = PingoraRequestCtx::default();
    let pipeline = protocol.pin_pipeline(swap);
    let request = virtual_request()?;
    let mut ctx = protocol.build_filter_context(&pipeline, &request, None);
    ctx.extensions.insert::<Arc<dyn McpInvoker>>(invoker);
    let mut body = Some(Bytes::from(json!({"jsonrpc":"2.0","id":17,"method":"tools/call","params":{"name":"handlers/composite","arguments":arguments}}).to_string()));
    match pipeline
        .execute_http_request_body(&mut ctx, &mut body, true)
        .await?
    {
        FilterAction::Reject(rejection) => Ok(serde_json::from_slice(
            rejection.body.as_deref().ok_or("missing MCP response")?,
        )?),
        _ => Err("MCP request did not produce a local result".into()),
    }
}

async fn activate(app: &App, namespace: &str) -> Result<tempfile::NamedTempFile, Error> {
    activate_with(
        app,
        namespace,
        include_str!("../../features/tool-handler/tests/fixtures/dispatch.wat"),
    )
    .await
}

async fn activate_with(
    app: &App,
    namespace: &str,
    source: &str,
) -> Result<tempfile::NamedTempFile, Error> {
    use std::io::Write as _;
    let source = source
        .replace("TARGET_NAMESPACE", namespace)
        .replace("TARGET_NAME", "publish")
        .replace("NAMESPACE_LENGTH", &namespace.len().to_string())
        .replace("NAME_LENGTH", "7");
    let mut component = tempfile::NamedTempFile::new()?;
    component.write_all(source.as_bytes())?;
    manage(app, "PUT", "/api/v1/tool-handlers", Some(json!([{
        "tool":{"name":"composite", "description":"Calls a registered target", "namespace":"handlers", "uri":"composite", "type":"wasm", "inputSchema":{"type":"object"}},
        "path":component.path(), "allowed_namespaces":[namespace]
    }]))).await?;
    Ok(component)
}

fn typed_arguments() -> Value {
    json!({"payload":{"enabled":true,"count":12,"nothing":null,"nested":["true",true,3.5]}})
}

fn tool_result(is_error: bool) -> Value {
    json!({
        "content":[{"type":"text","text":"published"},{"type":"image","data":"aW1hZ2U=","mimeType":"image/png"},{"type":"audio","data":"YXVkaW8=","mimeType":"audio/wav"}],
        "structuredContent":{"count":12,"enabled":true}, "isError":is_error,
        "_meta":{"trace":"preserved","echoed_credential":SECRET}
    })
}

async fn assert_tool_dispatch(app: &App, is_error: bool) -> TestResult {
    let result = tool_result(is_error);
    let invoker = Arc::new(RecordingInvoker::new(result.clone()));
    let response = invoke(app, invoker.clone(), typed_arguments()).await?;
    let mut expected = result;
    expected["_meta"]["echoed_credential"] = json!("<redacted>");
    assert_eq!(response["result"], expected, "{response}");
    let calls = invoker.calls()?;
    assert_eq!(calls.len(), 1);
    assert_eq!(calls[0].address, "https://kafka.example/mcp");
    assert_eq!(calls[0].name, "publish");
    assert_eq!(calls[0].arguments, typed_arguments());
    assert_eq!(
        calls[0]
            .headers
            .get(&http::header::AUTHORIZATION)
            .and_then(|value| value.to_str().ok()),
        Some(format!("Bearer {SECRET}").as_str())
    );
    assert!(!response.to_string().contains(SECRET));
    Ok(())
}

#[tokio::test]
async fn wasm_dispatch_preserves_typed_arguments_results_credentials_and_target_identity()
-> TestResult {
    let app = app()?;
    setup(&app).await?;
    let _component = activate(&app, "kafka").await?;
    for is_error in [false, true] {
        assert_tool_dispatch(&app, is_error).await?;
    }
    let events = manage(&app, "GET", "/api/v1/audit/events", None).await?;
    let events = events["data"]["events"]
        .as_array()
        .ok_or("missing audit events")?;
    assert!(
        events.iter().any(|event| event["operation"] == "tools/call"
            && event["correlation_id"] == "wasm-host-correlation"
            && event["attributes"]["concrete_namespace"] == "kafka"
            && event["attributes"]["virtual_namespace"] == "integration"),
        "{events:?}"
    );
    Ok(())
}

#[tokio::test]
async fn wasm_target_policy_denial_runs_before_transport() -> TestResult {
    let app = app()?;
    setup(&app).await?;
    let _component = activate(&app, "kafka").await?;
    manage(&app, "PUT", "/api/v1/action-policies", Some(json!({"policy":{"rules":[
        {"id":"allow-handler","effect":"allow","selectors":{"operation":"tools/call"}},
        {"id":"deny-kafka","effect":"deny","selectors":{"operation":"tools/call","namespace":"kafka"}}
    ]}}))).await?;
    let invoker = Arc::new(RecordingInvoker::new(json!({"content":[]})));
    let response = invoke(&app, invoker.clone(), typed_arguments()).await?;
    assert_eq!(response["error"]["code"], -32003, "{response}");
    assert!(invoker.calls()?.is_empty());
    let events = manage(&app, "GET", "/api/v1/audit/events", None).await?;
    assert!(
        events["data"]["events"]
            .as_array()
            .ok_or("missing audit events")?
            .iter()
            .any(|event| event["namespace"] == "kafka" && event["decision"] == "block")
    );
    Ok(())
}

#[tokio::test]
async fn wasm_target_arguments_and_availability_are_checked_before_transport() -> TestResult {
    let app = app()?;
    setup(&app).await?;
    let _component = activate(&app, "kafka").await?;
    let invoker = Arc::new(RecordingInvoker::new(json!({"content":[]})));
    let invalid = invoke(&app, invoker.clone(), json!({"payload":"wrong type"})).await?;
    assert_eq!(invalid["error"]["code"], -32602, "{invalid}");
    let mut forward = app
        .registry
        .get_forward("kafka-forward")
        .ok_or("missing forward")?;
    forward.available = false;
    app.registry.register_forward(forward);
    let unavailable = invoke(&app, invoker.clone(), typed_arguments()).await?;
    assert!(unavailable.get("error").is_some(), "{unavailable}");
    assert!(invoker.calls()?.is_empty());
    Ok(())
}

#[tokio::test]
async fn handler_grant_cannot_expand_the_callers_virtual_namespace_scope() -> TestResult {
    let app = app()?;
    setup(&app).await?;
    let _component = activate(&app, "private").await?;
    let invoker = Arc::new(RecordingInvoker::new(json!({"content":[]})));
    let response = invoke(&app, invoker.clone(), typed_arguments()).await?;
    assert_eq!(response["error"]["code"], -32003, "{response}");
    assert!(invoker.calls()?.is_empty());
    Ok(())
}

#[tokio::test]
async fn wasm_catalog_uses_explicit_namespace_and_preserves_complete_definitions() -> TestResult {
    let app = app()?;
    setup(&app).await?;
    let source = include_str!("../../features/tool-handler/tests/fixtures/catalog.wat");
    let _component = activate_with(&app, "kafka", source).await?;
    let invoker = Arc::new(RecordingInvoker::new(json!({"content":[]})));
    let response = invoke(&app, invoker.clone(), json!({})).await?;
    let catalog = &response["result"]["structuredContent"];
    assert_eq!(
        catalog["identity"],
        json!({"namespace":"kafka","name":"publish"}),
        "{response}"
    );
    assert_eq!(catalog["forward_id"], "kafka-forward");
    assert_eq!(catalog["definition"]["name"], "publish");
    assert_eq!(
        catalog["definition"]["inputSchema"]["properties"]["payload"]["type"],
        "object"
    );
    assert_eq!(
        catalog["definition"]["outputSchema"],
        json!({"type":"object"})
    );
    assert_eq!(
        catalog["definition"]["annotations"],
        json!({"idempotentHint":true})
    );
    assert_eq!(catalog["definition"]["_meta"]["example/owner"], "kafka");
    assert!(!response.to_string().contains(SECRET));
    assert!(invoker.calls()?.is_empty());
    Ok(())
}

#[tokio::test]
async fn recursive_handler_dispatch_fails_without_entering_transport() -> TestResult {
    let app = app()?;
    setup(&app).await?;
    let source = include_str!("../../features/tool-handler/tests/fixtures/dispatch.wat")
        .replace("\"TARGET_NAME\"", "\"composite\"")
        .replace("NAME_LENGTH", "9");
    let _component = activate_with(&app, "handlers", &source).await?;
    let invoker = Arc::new(RecordingInvoker::new(json!({"content":[]})));
    let response = invoke(&app, invoker.clone(), json!({})).await?;
    assert!(response.get("error").is_some(), "{response}");
    assert!(
        response["error"]["message"]
            .as_str()
            .is_some_and(|message| message.contains("recursive")),
        "{response}"
    );
    assert!(invoker.calls()?.is_empty());
    Ok(())
}

#[derive(Clone)]
struct ProtocolInvocation {
    method: &'static str,
    target: Invocation,
}

struct ProtocolInvoker {
    calls: Mutex<Vec<ProtocolInvocation>>,
    resource: Value,
    prompt: Value,
}

impl ProtocolInvoker {
    fn new() -> Self {
        Self {
            calls: Mutex::new(Vec::new()),
            resource: json!({"contents":[{"uri":"file:///data","mimeType":"application/octet-stream","blob":"YmluYXJ5"}],"_meta":{"trace":"resource-trace","echo":SECRET}}),
            prompt: json!({"description":"Summary","messages":[{"role":"user","content":{"type":"image","data":"aW1hZ2U=","mimeType":"image/png","annotations":{"priority":0.5}}}],"_meta":{"trace":"prompt-trace","echo":SECRET}}),
        }
    }

    fn record(&self, method: &'static str, target: Invocation) -> Result<Value, McpClientError> {
        self.calls
            .lock()
            .map_err(|_| McpClientError::Connection {
                url: target.address.clone(),
                message: "recording lock poisoned".to_owned(),
            })?
            .push(ProtocolInvocation { method, target });
        Ok(match method {
            "resources/read" => self.resource.clone(),
            _ => self.prompt.clone(),
        })
    }

    fn calls(&self) -> Result<Vec<ProtocolInvocation>, Error> {
        self.calls
            .lock()
            .map(|calls| calls.clone())
            .map_err(|_| "recording lock poisoned".into())
    }
}

#[async_trait::async_trait]
impl McpInvoker for ProtocolInvoker {
    async fn call_tool(
        &self,
        address: &str,
        _name: &str,
        _arguments: Value,
        _headers: HashMap<HeaderName, HeaderValue>,
    ) -> Result<CallToolResponse, McpClientError> {
        Err(McpClientError::Connection {
            url: address.to_owned(),
            message: "unexpected tool call".to_owned(),
        })
    }

    async fn read_resource(
        &self,
        address: &str,
        uri: &str,
        headers: HashMap<HeaderName, HeaderValue>,
    ) -> Result<Value, McpClientError> {
        self.record(
            "resources/read",
            Invocation {
                address: address.to_owned(),
                name: uri.to_owned(),
                arguments: Value::Null,
                headers,
            },
        )
    }

    async fn get_prompt(
        &self,
        address: &str,
        name: &str,
        arguments: Option<serde_json::Map<String, Value>>,
        headers: HashMap<HeaderName, HeaderValue>,
    ) -> Result<Value, McpClientError> {
        self.record(
            "prompts/get",
            Invocation {
                address: address.to_owned(),
                name: name.to_owned(),
                arguments: arguments.map_or(Value::Null, Value::Object),
                headers,
            },
        )
    }
}

async fn setup_protocol_entries(app: &App) -> TestResult {
    use wanaku_types::registry::{PromptRegistry as _, ResourceRegistry as _};
    setup(app).await?;
    app.registry.register_resource(serde_json::from_value(json!({
        "name":"data", "namespace":"kafka", "location":"file:///data", "type":"mcp-forward", "forwardId":"kafka-forward"
    }))?);
    app.registry.register_prompt(serde_json::from_value(json!({
        "name":"summary", "namespace":"kafka", "forwardId":"kafka-forward"
    }))?);
    let mut binding = app
        .registry
        .get_binding("kafka-binding")
        .ok_or("missing binding")?;
    binding.restrictions.operations = vec!["resources/read".to_owned(), "prompts/get".to_owned()];
    binding.restrictions.governed_items = vec!["file:///data".to_owned(), "summary".to_owned()];
    app.registry.register_binding(binding);
    Ok(())
}

fn protocol_client(
    app: &App,
    invoker: Arc<ProtocolInvoker>,
) -> Result<Arc<dyn wanaku_types::wasm::McpHostClient>, Error> {
    use wanaku_types::wasm::McpHostFactory;
    let swap = app.pipelines.get("mcp").ok_or("missing MCP pipeline")?;
    let mut protocol = PingoraRequestCtx::default();
    let pipeline = protocol.pin_pipeline(swap);
    let request = praxis_filter::Request {
        method: http::Method::POST,
        uri: "/virtual-ns/integration/mcp".parse()?,
        headers: http::HeaderMap::new(),
    };
    let mut ctx = protocol.build_filter_context(&pipeline, &request, None);
    ctx.set_metadata(wanaku_types::NAMESPACE_METADATA_KEY, "handlers");
    ctx.set_metadata(wanaku_types::VIRTUAL_NAMESPACE_METADATA_KEY, "integration");
    ctx.extensions.insert::<Arc<dyn McpInvoker>>(invoker);
    let factory = ctx
        .extensions
        .get::<Arc<dyn McpHostFactory>>()
        .ok_or("missing MCP host factory")?;
    Ok(factory.create(&ctx)?)
}

fn prompt_arguments() -> Value {
    json!({"topic":"typed","include":true,"limit":3})
}

async fn assert_protocol_results(
    client: &dyn wanaku_types::wasm::McpHostClient,
    invoker: &ProtocolInvoker,
) -> TestResult {
    let resource = client
        .request("resources/read", "kafka", json!({"uri":"file:///data"}))
        .await?;
    let mut expected_resource = invoker.resource.clone();
    expected_resource["contents"][0]["uri"] = json!("wanaku://kafka/file:///data");
    expected_resource["_meta"]["echo"] = json!("<redacted>");
    assert_eq!(resource, expected_resource);
    let prompt_args = prompt_arguments();
    let prompt = client
        .request(
            "prompts/get",
            "kafka",
            json!({"name":"summary","arguments":prompt_args}),
        )
        .await?;
    let mut expected_prompt = invoker.prompt.clone();
    expected_prompt["_meta"]["echo"] = json!("<redacted>");
    assert_eq!(prompt, expected_prompt);
    assert!(!resource.to_string().contains(SECRET));
    assert!(!prompt.to_string().contains(SECRET));
    Ok(())
}

fn assert_protocol_invocations(invoker: &ProtocolInvoker) -> TestResult {
    let calls = invoker.calls()?;
    assert_eq!(calls.len(), 2);
    assert_eq!(calls[0].method, "resources/read");
    assert_eq!(calls[0].target.name, "file:///data");
    assert_eq!(calls[1].method, "prompts/get");
    assert_eq!(calls[1].target.name, "summary");
    assert_eq!(calls[1].target.arguments, prompt_arguments());
    for call in calls {
        assert_eq!(call.target.address, "https://kafka.example/mcp");
        assert_eq!(
            call.target
                .headers
                .get(&http::header::AUTHORIZATION)
                .and_then(|header| header.to_str().ok()),
            Some(format!("Bearer {SECRET}").as_str())
        );
    }
    Ok(())
}

#[tokio::test]
async fn host_resource_and_prompt_requests_preserve_envelopes_credentials_and_scope() -> TestResult
{
    use wanaku_types::wasm::HostErrorKind;
    let app = app()?;
    setup_protocol_entries(&app).await?;
    let invoker = Arc::new(ProtocolInvoker::new());
    let client = protocol_client(&app, invoker.clone())?;
    assert_protocol_results(client.as_ref(), &invoker).await?;
    assert_protocol_invocations(&invoker)?;
    let denied = client
        .request("resources/read", "private", json!({"uri":"file:///data"}))
        .await;
    assert!(matches!(denied, Err(error) if error.kind == HostErrorKind::Denied));
    let missing = client
        .request(
            "prompts/get",
            "kafka",
            json!({"name":"missing","arguments":{}}),
        )
        .await;
    assert!(matches!(missing, Err(error) if error.kind == HostErrorKind::NotFound));
    assert_eq!(invoker.calls()?.len(), 2);
    Ok(())
}

#[tokio::test]
async fn host_resource_and_prompt_requests_obey_concrete_target_policies() -> TestResult {
    use wanaku_types::wasm::HostErrorKind;
    let app = app()?;
    setup_protocol_entries(&app).await?;
    manage(&app, "PUT", "/api/v1/action-policies", Some(json!({"policy":{"rules":[
        {"id":"deny-resource","effect":"deny","selectors":{"operation":"resources/read","namespace":"kafka"}},
        {"id":"deny-prompt","effect":"deny","selectors":{"operation":"prompts/get","namespace":"kafka"}}
    ]}}))).await?;
    let invoker = Arc::new(ProtocolInvoker::new());
    let client = protocol_client(&app, invoker.clone())?;
    for (method, params) in [
        ("resources/read", json!({"uri":"file:///data"})),
        ("prompts/get", json!({"name":"summary","arguments":{}})),
    ] {
        let denied = client.request(method, "kafka", params).await;
        assert!(
            matches!(denied, Err(error) if error.kind == HostErrorKind::Denied && error.code == Some(-32003))
        );
    }
    assert!(invoker.calls()?.is_empty());
    Ok(())
}
