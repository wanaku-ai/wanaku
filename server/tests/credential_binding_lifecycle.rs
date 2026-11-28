//! Runtime credential bindings through the management API and MCP pipeline.

use std::io::{self, Write};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex, mpsc};
use std::time::Duration;

use bytes::Bytes;
use http::{HeaderMap, HeaderValue, Response, StatusCode, header};
use praxis_filter::{FilterAction, Request};
use praxis_protocol::http::pingora::context::PingoraRequestCtx;
use serde_json::{Value, json};
use tokio::sync::Notify;
use tracing::instrument::WithSubscriber as _;
use wanaku_infra::credentials::CredentialBroker;
use wanaku_server::app::{App, AppPersistence, AppResources, AppSettings};
use wanaku_types::credentials::{
    ResolutionContext, ResolvedSecret, ResolverError, ResolverRegistry, SecretMaterial, SecretRef,
    SecretResolver,
};
use wanaku_types::feature::HttpContext;
use wanaku_types::governance::{FailureBehavior, GovernanceConfig, NoMatchBehavior};
use wiremock::{Mock, MockServer, ResponseTemplate, matchers::method};

type Error = Box<dyn std::error::Error + Send + Sync>;
type TestResult = Result<(), Error>;

const COLLECTION: &str = "/api/v1/bindings";
const BINDING: &str = "/api/v1/bindings/upstream-token";
const SECRETS: [&str; 3] = [
    "binding-lifecycle-original-secret",
    "binding-lifecycle-updated-secret",
    "binding-lifecycle-recreated-secret",
];

#[derive(Debug)]
struct RotatingResolver(Arc<AtomicUsize>);

#[async_trait::async_trait]
impl SecretResolver for RotatingResolver {
    fn scheme(&self) -> &str {
        "rotate"
    }

    async fn resolve(
        &self,
        _secret_ref: &SecretRef,
        _context: &ResolutionContext,
    ) -> Result<ResolvedSecret, ResolverError> {
        Ok(ResolvedSecret::without_expiry(SecretMaterial::from_string(
            SECRETS[self.0.load(Ordering::Relaxed)].to_owned(),
        )))
    }
}

fn app(rotation: Arc<AtomicUsize>) -> Result<App, Error> {
    let pipeline = wanaku_server::load_config(None)?;
    let mut governance = GovernanceConfig::default();
    governance.default.no_match = NoMatchBehavior::Allow;
    governance.default.on_failure = FailureBehavior::Allow;
    let resolvers = ResolverRegistry::new().with_resolver(Arc::new(RotatingResolver(rotation)));
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

async fn upstream(rotation: Arc<AtomicUsize>) -> MockServer {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .respond_with(move |request: &wiremock::Request| {
            upstream_reply(request, SECRETS[rotation.load(Ordering::Relaxed)])
        })
        .mount(&server)
        .await;
    server
}

fn bearer_token(request: &wiremock::Request) -> Option<&str> {
    request
        .headers
        .get(header::AUTHORIZATION)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.strip_prefix("Bearer "))
}

fn upstream_reply(request: &wiremock::Request, secret: &str) -> ResponseTemplate {
    if bearer_token(request) != Some(secret) {
        return ResponseTemplate::new(401);
    }
    let body = serde_json::from_slice::<Value>(&request.body).unwrap_or_default();
    let result = match body["method"].as_str() {
        Some("initialize") => json!({
            "protocolVersion": "2025-11-25",
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "upstream", "version": "1.0.0"}
        }),
        Some("notifications/initialized") => return ResponseTemplate::new(202),
        Some("tools/list") => json!({
            "tools": [{"name":"echo", "description":secret, "inputSchema":{"type":"object"}}]
        }),
        Some("tools/call") => json!({"content":[{"type":"text","text":secret}]}),
        Some("resources/list") => json!({"resources":[]}),
        Some("resources/templates/list") => json!({"resourceTemplates":[]}),
        Some("prompts/list") => json!({"prompts":[]}),
        _ => return ResponseTemplate::new(400),
    };
    ResponseTemplate::new(200)
        .set_body_json(json!({"jsonrpc":"2.0", "id":body["id"], "result":result}))
}

fn definition(origin: &str) -> Value {
    json!({
        "id": "upstream-token",
        "forwardId": "upstream",
        "origin": origin,
        "mechanism": {"type":"bearer"},
        "secretRefs": ["rotate:access-token"],
        "allowedPurposes": ["discovery", "invocation"],
        "cache": {"maxTtlSeconds":600}
    })
}

fn assert_redacted(body: &[u8]) {
    let text = String::from_utf8_lossy(body);
    assert!(SECRETS.iter().all(|secret| !text.contains(secret)));
}

async fn dispatch(app: &App, method: &str, path: &str, body: Option<&Value>) -> Response<Vec<u8>> {
    let headers = HeaderMap::new();
    let body = body.map(Value::to_string);
    let context = HttpContext::new(method, path, None, body.as_deref(), &headers);
    let response = Box::pin(app.management.dispatch(&context)).await;
    assert_redacted(response.body());
    response
}

async fn invoke(app: &App) -> Result<Value, Error> {
    let swap = app.pipelines.get("mcp").ok_or("missing MCP pipeline")?;
    let mut protocol = PingoraRequestCtx::default();
    let pipeline = protocol.pin_pipeline(swap);
    let mut headers = HeaderMap::new();
    headers.insert(
        header::CONTENT_TYPE,
        HeaderValue::from_static("application/json"),
    );
    let request = Request {
        method: http::Method::POST,
        uri: "/default/mcp".parse()?,
        headers,
    };
    let mut ctx = protocol.build_filter_context(&pipeline, &request, None);
    let mut body = Some(Bytes::from_static(
        br#"{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"echo","arguments":{}}}"#,
    ));
    match Box::pin(pipeline.execute_http_request_body(&mut ctx, &mut body, true)).await? {
        FilterAction::Reject(rejection) => {
            let data = rejection.body.as_deref().ok_or("missing MCP response")?;
            assert_redacted(data);
            Ok(serde_json::from_slice(data)?)
        }
        _ => Err("MCP request did not produce a local response".into()),
    }
}

async fn assert_invocation(app: &App) -> TestResult {
    let response = Box::pin(invoke(app)).await?;
    assert_eq!(
        response["result"]["content"][0]["text"], "<redacted>",
        "{response}"
    );
    Ok(())
}

#[derive(Clone, Default)]
struct LogOutput(Arc<Mutex<Vec<u8>>>);

impl Write for LogOutput {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        self.0
            .lock()
            .map_err(|_| io::Error::other("log output lock poisoned"))?
            .write(bytes)
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

async fn register_forward(app: &App, server: &MockServer, name: &str) -> Result<Value, Error> {
    let forward = forward_definition(server, name);
    let registered = dispatch(app, "POST", "/api/v1/forwards", Some(&forward)).await;
    assert_eq!(registered.status(), StatusCode::OK);
    Ok(serde_json::from_slice(registered.body())?)
}

fn forward_definition(server: &MockServer, name: &str) -> Value {
    json!({
        "name":name, "address":server.uri(),
        "credentialBindings":{"discovery":"upstream-token", "invocation":"upstream-token"}
    })
}

async fn request_count(server: &MockServer) -> Result<usize, Error> {
    Ok(server
        .received_requests()
        .await
        .ok_or("request log disabled")?
        .len())
}

async fn assert_denied(app: &App, server: &MockServer, reason: &str) -> TestResult {
    let before = request_count(server).await?;
    let denied = Box::pin(invoke(app)).await?;
    assert_eq!(denied["error"]["message"], reason);
    assert_eq!(before, request_count(server).await?);
    Ok(())
}

async fn assert_binding_updates(
    app: &App,
    server: &MockServer,
    rotation: &AtomicUsize,
    binding: &mut Value,
) -> TestResult {
    // Keep the same secret reference and change only the definition. This must
    // resolve new material even though the original entry has not expired.
    rotation.store(1, Ordering::Relaxed);
    binding["cache"]["maxTtlSeconds"] = json!(599);
    let updated = dispatch(app, "PUT", BINDING, Some(binding)).await;
    assert_eq!(updated.status(), StatusCode::OK);
    Box::pin(assert_invocation(app)).await?;
    let refreshed = dispatch(app, "POST", "/api/v1/forwards/upstream/refreshes", None).await;
    let refreshed: Value = serde_json::from_slice(refreshed.body())?;
    assert_eq!(refreshed["data"]["tools_discovered"], 1);

    let mut discovery_only = binding.clone();
    discovery_only["allowedPurposes"] = json!(["discovery"]);
    assert_eq!(
        dispatch(app, "PUT", BINDING, Some(&discovery_only))
            .await
            .status(),
        StatusCode::OK
    );
    Box::pin(assert_denied(app, server, "credential brokerage failed")).await?;

    binding["origin"] = json!("https://different.example.net");
    let changed_origin = dispatch(app, "PUT", BINDING, Some(binding)).await;
    assert_eq!(changed_origin.status(), StatusCode::OK);
    Box::pin(assert_denied(app, server, "credential brokerage failed")).await
}

async fn assert_delete_and_recreate(
    app: &App,
    server: &MockServer,
    rotation: &AtomicUsize,
    binding: &mut Value,
) -> TestResult {
    assert_eq!(
        dispatch(app, "DELETE", BINDING, None).await.status(),
        StatusCode::OK
    );
    Box::pin(assert_denied(app, server, "credential binding not found")).await?;

    rotation.store(2, Ordering::Relaxed);
    binding["origin"] = json!(server.uri());
    assert_eq!(
        dispatch(app, "POST", COLLECTION, Some(binding))
            .await
            .status(),
        StatusCode::CREATED
    );
    Box::pin(assert_invocation(app)).await
}

async fn lifecycle() -> TestResult {
    let rotation = Arc::new(AtomicUsize::new(0));
    let app = app(rotation.clone())?;
    let server = Box::pin(upstream(rotation.clone())).await;
    let mut binding = definition(&server.uri());

    let created = dispatch(&app, "POST", COLLECTION, Some(&binding)).await;
    assert_eq!(created.status(), StatusCode::CREATED);
    let registered = register_forward(&app, &server, "upstream").await?;
    assert_eq!(registered["data"]["tools_discovered"], 1);
    let tools = dispatch(&app, "GET", "/api/v1/tools", None).await;
    assert!(String::from_utf8_lossy(tools.body()).contains("<redacted>"));
    Box::pin(assert_invocation(&app)).await?;
    assert!(!app.broker.cache().is_empty());

    let before = request_count(&server).await?;
    let wrong_owner = register_forward(&app, &server, "wrong-owner").await?;
    assert_eq!(wrong_owner["data"]["forward"]["available"], false);
    assert_eq!(before, request_count(&server).await?);

    Box::pin(assert_binding_updates(
        &app,
        &server,
        &rotation,
        &mut binding,
    ))
    .await?;

    Box::pin(assert_delete_and_recreate(
        &app,
        &server,
        &rotation,
        &mut binding,
    ))
    .await
}

#[tokio::test]
async fn runtime_binding_lifecycle_controls_discovery_and_invocation() -> TestResult {
    let output = LogOutput::default();
    let writer = output.clone();
    let subscriber = tracing_subscriber::fmt()
        .with_max_level(tracing::Level::DEBUG)
        .with_ansi(false)
        .with_writer(move || writer.clone())
        .finish();
    Box::pin(lifecycle()).with_subscriber(subscriber).await?;
    let logs = output.0.lock().map_err(|_| "log output lock poisoned")?;
    assert!(!logs.is_empty());
    assert_redacted(&logs);
    Ok(())
}

async fn gated_upstream() -> (MockServer, Arc<Notify>, mpsc::Sender<()>) {
    let server = MockServer::start().await;
    let listing_started = Arc::new(Notify::new());
    let started = listing_started.clone();
    let (release, wait_for_release) = mpsc::channel();
    let wait_for_release = Mutex::new(Some(wait_for_release));
    Mock::given(method("POST"))
        .respond_with(move |request: &wiremock::Request| {
            let body = serde_json::from_slice::<Value>(&request.body).unwrap_or_default();
            if bearer_token(request) == Some(SECRETS[0]) && body["method"] == "tools/list" {
                // Wiremock runs in a separate thread. Hold the successful list
                // response until the binding changes in the management API.
                started.notify_one();
                let receiver = wait_for_release
                    .lock()
                    .ok()
                    .and_then(|mut receiver| receiver.take());
                if receiver.is_none_or(|rx| rx.recv_timeout(Duration::from_secs(10)).is_err()) {
                    return ResponseTemplate::new(500);
                }
            }
            upstream_reply(request, SECRETS[0])
        })
        .mount(&server)
        .await;
    (server, listing_started, release)
}

#[tokio::test]
#[expect(
    clippy::too_many_lines,
    reason = "checks management state before and after the discovery race"
)]
async fn binding_update_discards_in_flight_authenticated_discovery() -> TestResult {
    let app = &app(Arc::new(AtomicUsize::new(0)))?;
    let (server, listing_started, release) = gated_upstream().await;
    let mut binding = definition(&server.uri());
    assert_eq!(
        dispatch(app, "POST", COLLECTION, Some(&binding))
            .await
            .status(),
        StatusCode::CREATED
    );
    binding["cache"]["maxTtlSeconds"] = json!(599);
    let forward = forward_definition(&server, "upstream");
    let register = dispatch(app, "POST", "/api/v1/forwards", Some(&forward));
    let mutate = async move {
        tokio::time::timeout(Duration::from_secs(5), listing_started.notified()).await?;
        let updated = dispatch(app, "PUT", BINDING, Some(&binding)).await;
        release.send(())?;
        assert_eq!(updated.status(), StatusCode::OK);
        Ok::<_, Error>(())
    };
    let (registered, mutation) = tokio::join!(register, mutate);
    mutation?;
    assert_eq!(registered.status(), StatusCode::CONFLICT);
    let forward = dispatch(app, "GET", "/api/v1/forwards/upstream", None).await;
    assert_eq!(forward.status(), StatusCode::NOT_FOUND);
    let tools = dispatch(app, "GET", "/api/v1/tools", None).await;
    let tools: Value = serde_json::from_slice(tools.body())?;
    assert_eq!(tools["data"], json!([]));
    Ok(())
}

#[tokio::test]
#[expect(
    clippy::too_many_lines,
    reason = "covers the binding mutation error matrix"
)]
async fn mutation_errors_are_redacted_and_preserve_the_existing_binding() -> TestResult {
    let app = app(Arc::new(AtomicUsize::new(0)))?;
    let mut binding = definition("https://API.example.net");
    let created = dispatch(&app, "POST", COLLECTION, Some(&binding)).await;
    assert_eq!(created.status(), StatusCode::CREATED);
    let data: Value = serde_json::from_slice(created.body())?;
    assert_eq!(data["data"]["origin"], "https://api.example.net:443");
    assert_eq!(
        dispatch(&app, "POST", COLLECTION, Some(&binding))
            .await
            .status(),
        StatusCode::CONFLICT
    );

    binding["revision"] = data["data"]["revision"].clone();
    binding["forwardId"] = json!("different-forward");
    assert_eq!(
        dispatch(&app, "PUT", BINDING, Some(&binding))
            .await
            .status(),
        StatusCode::CONFLICT
    );

    binding["forwardId"] = json!("upstream");
    for (field, invalid) in [
        ("origin", json!("file:///unusable")),
        ("secretRefs", json!([SECRETS[0]])),
        ("secretValue", json!(SECRETS[0])),
        ("mechanism", json!({"type":"basic"})),
        ("id", json!("different-id")),
    ] {
        let mut invalid_body = binding.clone();
        invalid_body[field] = invalid;
        assert_eq!(
            dispatch(&app, "PUT", BINDING, Some(&invalid_body))
                .await
                .status(),
            StatusCode::BAD_REQUEST
        );
    }
    let unchanged = dispatch(&app, "GET", BINDING, None).await;
    assert_eq!(serde_json::from_slice::<Value>(unchanged.body())?, data);

    binding["id"] = json!("missing");
    assert_eq!(
        dispatch(&app, "PUT", "/api/v1/bindings/missing", Some(&binding))
            .await
            .status(),
        StatusCode::NOT_FOUND
    );
    assert_eq!(
        dispatch(&app, "DELETE", "/api/v1/bindings/missing", None)
            .await
            .status(),
        StatusCode::NOT_FOUND
    );
    Ok(())
}
