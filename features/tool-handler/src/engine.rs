use std::path::Path;
use std::sync::Arc;
use std::time::{Duration, Instant};

use serde_json::Value;
use wanaku_types::wasm::{HostError, HostErrorKind, McpHostClient, ToolTarget};
use wasmtime::component::{Component, Linker, ResourceTable};
use wasmtime::{Config, Engine, Store};

use crate::config::HandlerDefinition;
use crate::host::{self, HostState, ToolHandlerPre, types};
use wanaku_infra::wasm::EpochTicker;

pub(crate) struct CompiledHandler {
    engine: Engine,
    pre: ToolHandlerPre<HostState>,
    permits: Arc<tokio::sync::Semaphore>,
    input_schema: jsonschema::Validator,
    output_schema: Option<jsonschema::Validator>,
    pub definition: HandlerDefinition,
}

struct Execution {
    target: ToolTarget,
    arguments: Value,
    client: Arc<dyn McpHostClient>,
    runtime: tokio::runtime::Handle,
    deadline: Instant,
}

impl CompiledHandler {
    pub fn compile(definition: HandlerDefinition) -> Result<Self, String> {
        let input_schema = compile_schema(&definition.tool.input_schema)?;
        let output_schema = definition
            .tool
            .mcp
            .output_schema
            .as_ref()
            .map(compile_schema)
            .transpose()?;
        let (engine, pre) = compile_component(&definition.path)?;
        let permits = Arc::new(tokio::sync::Semaphore::new(
            definition.limits.max_concurrent,
        ));
        Ok(Self {
            engine,
            pre,
            permits,
            definition,
            input_schema,
            output_schema,
        })
    }

    pub async fn call(
        self: Arc<Self>,
        target: ToolTarget,
        arguments: Value,
        client: Arc<dyn McpHostClient>,
    ) -> Result<Value, HostError> {
        if !self.input_schema.is_valid(&arguments) {
            return Err(HostError::new(
                HostErrorKind::InvalidArguments,
                "arguments do not match the handler input schema",
            ));
        }
        let permit = self.permits.clone().try_acquire_owned().map_err(|_| {
            HostError::new(HostErrorKind::Limit, "handler concurrency limit exceeded")
        })?;
        let execution =
            Execution::new(target, arguments, client, self.definition.limits.timeout_ms)?;
        let deadline = execution.deadline;
        let runtime = execution.runtime.clone();
        let work = runtime.spawn_blocking(move || {
            let result = self.execute(execution);
            drop(permit);
            result
        });
        tokio::time::timeout_at(tokio::time::Instant::from_std(deadline), work)
            .await
            .map_err(|_| HostError::new(HostErrorKind::Timeout, "handler deadline exceeded"))?
            .map_err(|_| {
                HostError::new(HostErrorKind::Execution, "handler execution task failed")
            })?
    }

    fn execute(&self, execution: Execution) -> Result<Value, HostError> {
        let deadline = execution.deadline;
        let (mut store, request) = execution.into_store(&self.engine, &self.definition)?;
        let engine = self.engine.clone();
        let _ticker = EpochTicker::start(move || engine.increment_epoch()).map_err(|_| {
            HostError::new(HostErrorKind::Internal, "WASM deadline worker unavailable")
        })?;
        let instance = self
            .pre
            .instantiate(&mut store)
            .map_err(|error| execution_error(&error, deadline))?;
        let output = instance
            .call_handle(&mut store, &request)
            .map_err(|error| execution_error(&error, deadline))?
            .map_err(host::from_guest_error)?;
        self.decode_result(&output)
    }

    fn decode_result(&self, output: &str) -> Result<Value, HostError> {
        let value: Value = serde_json::from_str(output).map_err(|_| {
            HostError::new(HostErrorKind::Protocol, "handler returned invalid JSON")
        })?;
        validate_result(&value)?;
        match (
            value.get("isError").and_then(Value::as_bool) == Some(true),
            &self.output_schema,
        ) {
            (false, Some(schema))
                if !value
                    .get("structuredContent")
                    .is_some_and(|content| schema.is_valid(content)) =>
            {
                Err(HostError::new(
                    HostErrorKind::Protocol,
                    "handler result does not match outputSchema",
                ))
            }
            _ => Ok(value),
        }
    }
}

impl Execution {
    fn new(
        target: ToolTarget,
        arguments: Value,
        client: Arc<dyn McpHostClient>,
        timeout_ms: u64,
    ) -> Result<Self, HostError> {
        let runtime = tokio::runtime::Handle::try_current()
            .map_err(|_| HostError::new(HostErrorKind::Internal, "async runtime unavailable"))?;
        let deadline = Instant::now()
            .checked_add(Duration::from_millis(timeout_ms))
            .ok_or_else(|| {
                HostError::new(
                    HostErrorKind::Limit,
                    "handler timeout exceeds platform limits",
                )
            })?;
        Ok(Self {
            target,
            arguments,
            client,
            runtime,
            deadline,
        })
    }

    fn into_store(
        self,
        engine: &Engine,
        definition: &HandlerDefinition,
    ) -> Result<(Store<HostState>, types::Request), HostError> {
        let request = types::Request {
            namespace: self.target.namespace,
            name: self.target.name,
            arguments: self.arguments.to_string(),
        };
        let state = HostState {
            client: self.client,
            allowed_namespaces: definition.allowed_namespaces.clone(),
            deadline: self.deadline,
            runtime: self.runtime,
            wasi: wasmtime_wasi::WasiCtxBuilder::new().build(),
            table: ResourceTable::new(),
            limits: memory_limits(definition.limits.memory_bytes),
        };
        let mut store = Store::new(engine, state);
        configure_store(&mut store, definition.limits.fuel, self.deadline)?;
        Ok((store, request))
    }
}

fn compile_component(path: &Path) -> Result<(Engine, ToolHandlerPre<HostState>), String> {
    let mut config = Config::new();
    config.consume_fuel(true).epoch_interruption(true);
    let engine = Engine::new(&config).map_err(|error| error.to_string())?;
    let component = Component::from_file(&engine, path)
        .map_err(|error| format!("failed to compile WASM component: {error:#}"))?;
    let mut linker = Linker::new(&engine);
    host::link(&mut linker)?;
    let pre = linker
        .instantiate_pre(&component)
        .map_err(|error| error.to_string())?;
    let pre = ToolHandlerPre::new(pre)
        .map_err(|error| format!("invalid tool-handler contract: {error}"))?;
    Ok((engine, pre))
}

fn memory_limits(memory_bytes: usize) -> wasmtime::StoreLimits {
    wasmtime::StoreLimitsBuilder::new()
        .memory_size(memory_bytes)
        .table_elements(10_000)
        .instances(32)
        .memories(8)
        .tables(8)
        .build()
}

fn configure_store(
    store: &mut Store<HostState>,
    fuel: u64,
    deadline: Instant,
) -> Result<(), HostError> {
    if Instant::now() >= deadline {
        return Err(HostError::new(
            HostErrorKind::Timeout,
            "handler deadline exceeded",
        ));
    }
    store.limiter(|state| &mut state.limits);
    store
        .set_fuel(fuel)
        .map_err(|error| execution_error(&error, deadline))?;
    store.epoch_deadline_callback(move |_| {
        if Instant::now() >= deadline {
            Err(wasmtime::Error::msg("handler deadline exceeded"))
        } else {
            Ok(wasmtime::UpdateDeadline::Continue(1))
        }
    });
    store.set_epoch_deadline(1);
    Ok(())
}

fn execution_error(error: &wasmtime::Error, deadline: Instant) -> HostError {
    if error.downcast_ref::<wasmtime::Trap>() == Some(&wasmtime::Trap::OutOfFuel) {
        HostError::new(HostErrorKind::Limit, "handler fuel limit exceeded")
    } else if Instant::now() >= deadline {
        HostError::new(HostErrorKind::Timeout, "handler deadline exceeded")
    } else {
        HostError::new(
            HostErrorKind::Execution,
            format!("handler execution failed: {error}"),
        )
    }
}

pub fn validate_result(value: &Value) -> Result<(), HostError> {
    if !value.is_object()
        || !value.get("content").is_some_and(Value::is_array)
        || value
            .get("isError")
            .is_some_and(|value| !value.is_boolean())
        || value
            .get("structuredContent")
            .is_some_and(|value| !value.is_object())
    {
        return Err(HostError::new(
            HostErrorKind::Protocol,
            "handler must return a valid MCP tool result",
        ));
    }
    Ok(())
}

struct NoExternalSchemas;

impl jsonschema::Retrieve for NoExternalSchemas {
    fn retrieve(
        &self,
        _uri: &jsonschema::Uri<String>,
    ) -> Result<Value, Box<dyn std::error::Error + Send + Sync>> {
        Err("external schema retrieval is disabled".into())
    }
}

fn compile_schema(schema: &Value) -> Result<jsonschema::Validator, String> {
    jsonschema::options()
        .with_retriever(NoExternalSchemas)
        .build(schema)
        .map_err(|error| format!("invalid handler schema: {error}"))
}
