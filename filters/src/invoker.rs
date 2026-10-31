//! Access to the MCP invocation adapter for the terminal forwarding filters.
//!
//! The `tools/call`, `resources/read` and `prompts/get` filters reach the
//! upstream MCP server only through an [`McpInvoker`] taken from the request
//! extensions. The server inserts the production rmcp adapter. Tests insert a
//! recording fake, so whole-filter tests need no listening MCP server.

use std::sync::Arc;

use praxis_filter::{FilterAction, HttpFilterContext};
use wanaku_infra::mcp_client::McpInvoker;

/// Return the MCP invocation adapter from the request extensions.
///
/// Returns `Err(FilterAction)` with a JSON-RPC internal error when no adapter
/// is present, so the request fails closed before credential brokerage.
pub fn invoker_from_extensions(
    ctx: &HttpFilterContext<'_>,
    json_rpc_id: &serde_json::Value,
) -> Result<Arc<dyn McpInvoker>, FilterAction> {
    match ctx.extensions.get::<Arc<dyn McpInvoker>>() {
        Some(invoker) => Ok(Arc::clone(invoker)),
        None => {
            tracing::error!("MCP invoker not found in request extensions");
            Err(crate::response::json_rpc_error(
                json_rpc_id,
                crate::response::JSONRPC_INTERNAL_ERROR,
                "internal error: MCP invoker unavailable",
            ))
        }
    }
}

/// Test fixtures for whole-filter forwarding tests: a recording invoker fake
/// and a minimal filter context.
#[cfg(test)]
pub mod testing {
    use std::collections::HashMap;
    use std::sync::{Arc, LazyLock, Mutex};

    use http::{HeaderName, HeaderValue};
    use praxis_filter::{FilterAction, HttpFilterContext, Request, RequestExtensions};
    use serde_json::Value;
    use wanaku_infra::credentials::CredentialBroker;
    use wanaku_infra::mcp_client::{CallToolResponse, McpClientError, McpInvoker};
    use wanaku_infra::registry::InMemoryRegistry;
    use wanaku_types::credentials::CredentialPurpose;
    use wanaku_types::credentials::binding::{
        BindingRestrictions, CacheRules, CredentialBinding, NormalizedOrigin,
    };
    use wanaku_types::credentials::fake_resolver::FakeResolver;
    use wanaku_types::credentials::injection::InjectionMechanism;
    use wanaku_types::credentials::resolver::{ResolverRegistry, SecretRef};
    use wanaku_types::registry::{BindingRegistry, ForwardEntry, ForwardRegistry};

    /// The upstream address of the test forward.
    pub const ADDRESS: &str = "https://api.example.com/mcp";
    /// The forward id of the test forward.
    pub const FORWARD_ID: &str = "fwd-a";
    /// The brokered secret the fake resolver returns.
    pub const SECRET: &str = "s3cr3t";

    static TEST_ID_GENERATOR: LazyLock<praxis_core::id::IdGenerator> =
        LazyLock::new(|| praxis_core::id::IdGenerator::with_seed(0));

    /// One recorded call to the invocation adapter.
    #[derive(Debug, Clone)]
    pub struct Invocation {
        pub operation: &'static str,
        pub address: String,
        pub target: String,
        pub arguments: Option<Value>,
        pub headers: HashMap<HeaderName, HeaderValue>,
    }

    /// An [`McpInvoker`] that records each call and returns a fixed outcome.
    pub struct RecordingInvoker {
        outcome: Result<Value, String>,
        calls: Mutex<Vec<Invocation>>,
    }

    impl RecordingInvoker {
        /// Return `value` as the upstream result for every call.
        pub fn succeeding(value: Value) -> Arc<Self> {
            Arc::new(Self {
                outcome: Ok(value),
                calls: Mutex::new(Vec::new()),
            })
        }

        /// Return a connection error with `message` for every call.
        pub fn failing(message: &str) -> Arc<Self> {
            Arc::new(Self {
                outcome: Err(message.to_owned()),
                calls: Mutex::new(Vec::new()),
            })
        }

        pub fn calls(&self) -> Vec<Invocation> {
            self.calls.lock().unwrap().clone()
        }

        fn record(&self, invocation: Invocation) -> Result<Value, McpClientError> {
            let url = invocation.address.clone();
            self.calls.lock().unwrap().push(invocation);
            self.outcome
                .clone()
                .map_err(|message| McpClientError::Connection { url, message })
        }
    }

    #[async_trait::async_trait]
    impl McpInvoker for RecordingInvoker {
        async fn call_tool(
            &self,
            address: &str,
            tool_name: &str,
            arguments: Value,
            headers: HashMap<HeaderName, HeaderValue>,
        ) -> Result<CallToolResponse, McpClientError> {
            let result = self.record(Invocation {
                operation: crate::TOOLS_CALL,
                address: address.to_owned(),
                target: tool_name.to_owned(),
                arguments: Some(arguments),
                headers,
            })?;
            Ok(CallToolResponse {
                result,
                is_error: false,
            })
        }

        async fn read_resource(
            &self,
            address: &str,
            resource_uri: &str,
            headers: HashMap<HeaderName, HeaderValue>,
        ) -> Result<Vec<Value>, McpClientError> {
            let result = self.record(Invocation {
                operation: crate::RESOURCES_READ,
                address: address.to_owned(),
                target: resource_uri.to_owned(),
                arguments: None,
                headers,
            })?;
            Ok(vec![result])
        }

        async fn get_prompt(
            &self,
            address: &str,
            prompt_name: &str,
            arguments: Option<serde_json::Map<String, Value>>,
            headers: HashMap<HeaderName, HeaderValue>,
        ) -> Result<Value, McpClientError> {
            self.record(Invocation {
                operation: crate::PROMPTS_GET,
                address: address.to_owned(),
                target: prompt_name.to_owned(),
                arguments: arguments.map(Value::Object),
                headers,
            })
        }
    }

    /// Build a POST `/mcp` request with the given headers.
    pub fn request(headers: &[(&str, &str)]) -> Request {
        let mut map = http::HeaderMap::new();
        for (name, value) in headers {
            map.insert(
                HeaderName::from_bytes(name.as_bytes()).unwrap(),
                HeaderValue::from_str(value).unwrap(),
            );
        }
        Request {
            method: http::Method::POST,
            uri: "/mcp".parse().unwrap(),
            headers: map,
        }
    }

    /// Build a registry with the test forward. With `binding`, the forward uses
    /// that invocation binding. With `secret_ref`, the registry also stores a
    /// bearer binding under that id that resolves the secret reference.
    /// `"fake:token"` resolves to [`SECRET`]; other references fail.
    pub fn registry(binding: Option<&str>, secret_ref: Option<&str>) -> InMemoryRegistry {
        let registry = InMemoryRegistry::new();
        let mut credential_bindings = HashMap::new();
        if let Some(id) = binding {
            credential_bindings.insert(CredentialPurpose::Invocation, id.to_owned());
            if let Some(secret_ref) = secret_ref {
                registry.register_binding(CredentialBinding {
                    id: id.to_owned(),
                    forward_id: FORWARD_ID.to_owned(),
                    origin: NormalizedOrigin::from_address("https://api.example.com").unwrap(),
                    mechanism: InjectionMechanism::Bearer,
                    secret_refs: vec![SecretRef::parse(secret_ref).unwrap()],
                    allowed_purposes: vec![CredentialPurpose::Invocation],
                    restrictions: BindingRestrictions::default(),
                    cache: CacheRules::default(),
                    revision: 1,
                });
            }
        }
        registry.register_forward(ForwardEntry {
            name: FORWARD_ID.to_owned(),
            address: ADDRESS.to_owned(),
            namespace: None,
            server_info: None,
            labels: HashMap::new(),
            available: true,
            status_message: None,
            credential_bindings,
        });
        registry
    }

    /// Build a minimal filter context for `method` with the registry, a
    /// credential broker and the invocation adapter in the request extensions.
    #[expect(
        clippy::too_many_lines,
        reason = "Praxis test context requires every field explicitly"
    )]
    pub fn filter_context<'a>(
        req: &'a Request,
        method: &str,
        registry: &InMemoryRegistry,
        invoker: &Arc<RecordingInvoker>,
    ) -> HttpFilterContext<'a> {
        let mut extensions = RequestExtensions::default();
        extensions.insert(registry.clone());
        let resolvers = ResolverRegistry::new()
            .with_resolver(Arc::new(FakeResolver::new().with_value("token", SECRET)));
        extensions.insert(Arc::new(CredentialBroker::new(resolvers)));
        let invoker: Arc<dyn McpInvoker> = invoker.clone();
        extensions.insert(invoker);
        let mut ctx = HttpFilterContext {
            buffered_request_body: None,
            body_done_indices: Vec::new(),
            branch_iterations: HashMap::new(),
            client_addr: None,
            cluster: None,
            current_filter_id: None,
            downstream_tls: false,
            extensions,
            executed_branch_filters: Vec::new(),
            executed_filter_indices: Vec::new(),
            extra_request_headers: Vec::new(),
            request_headers_to_remove: Vec::new(),
            request_headers_to_set: Vec::new(),
            filter_metadata: HashMap::new(),
            prior_pre_read_mutations: Vec::new(),
            pre_read_mutations: Vec::new(),
            structured_metadata: HashMap::new(),
            filter_results: HashMap::new(),
            filter_state: HashMap::new(),
            grpc_completion: None,
            health_registry: None,
            hedge_policy: None,
            id_generator: &TEST_ID_GENERATOR,
            kv_stores: None,
            session_stores: None,
            metrics_route: None,
            peer_identity: None,
            subrequest_client: None,
            subrequest_response_mode: praxis_filter::SubRequestResponseMode::Buffered,
            request: req,
            request_body_bytes: 0,
            request_body_mode: praxis_filter::BodyMode::Stream,
            request_start: std::time::Instant::now(),
            response_body_bytes: 0,
            response_body_mode: praxis_filter::BodyMode::Stream,
            response_header: None,
            response_headers_modified: false,
            upstream_reached: false,
            rewritten_path: None,
            selected_endpoint_index: None,
            attempted_endpoints: Vec::new(),
            retry_policy: None,
            route_retry_policy: None,
            cluster_retry_state: None,
            cluster_retry_state_released: false,
            endpoint_reselector: None,
            pinned_endpoint_address: None,
            time_source: &praxis_core::time::SystemTimeSource,
            upstream: None,
        };
        ctx.set_metadata(crate::MCP_METHOD_KEY, method);
        ctx.set_metadata(crate::MCP_ID_KEY, "7");
        ctx
    }

    /// Return the JSON body of a rejection, the shape every terminal filter
    /// response uses. Any other action returns `Value::Null`.
    pub fn response_json(action: FilterAction) -> Value {
        match action {
            FilterAction::Reject(rejection) => {
                serde_json::from_slice(&rejection.body.unwrap()).unwrap()
            }
            _ => Value::Null,
        }
    }
}
