use std::path::Path;

use std::sync::Arc;

use wasmtime::component::{Component, Linker, ResourceTable};
use wasmtime::{Config, Engine, Store};
use wasmtime_wasi::{WasiCtxBuilder, WasiCtxView, WasiView};

use crate::schema::CompiledSchema;

use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::interactions::InMemoryInteractionStore;

use crate::action::ActionResult;
use crate::host::{self, EvaluatorAction, HostState};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EvaluatorExecutionError {
    Instantiation,
    Execution,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExecutionMode {
    Production,
    Simulation,
}

/// Capabilities and stores available during one processor invocation.
pub struct ExecutionContext {
    pub registry: InMemoryRegistry,
    pub interactions: InMemoryInteractionStore,
    pub compiled_schema: Option<Arc<CompiledSchema>>,
    pub allow_side_effects: bool,
    pub mode: ExecutionMode,
    pub limits: Option<SimulationLimits>,
}

#[derive(Debug)]
pub struct EvaluationResult {
    pub action: ActionResult,
    pub side_effects_suppressed: bool,
}

impl ExecutionContext {
    fn into_host_state(self, name: &str) -> HostState {
        let wasi_ctx = WasiCtxBuilder::new().build();

        HostState {
            registry: self.registry,
            interactions: self.interactions,
            allow_side_effects: self.allow_side_effects,
            mode: self.mode,
            side_effects_suppressed: false,
            action: ActionResult::Pass,
            evaluator_name: name.to_owned(),
            compiled_schema: self.compiled_schema,
            wasi_ctx,
            limits: store_limits(self.limits),
            wasi_table: ResourceTable::new(),
        }
    }
}

fn store_limits(limits: Option<SimulationLimits>) -> wasmtime::StoreLimits {
    limits.map_or_else(
        || wasmtime::StoreLimitsBuilder::new().build(),
        |limits| {
            wasmtime::StoreLimitsBuilder::new()
                .memory_size(limits.memory_bytes)
                .table_elements(10_000)
                .instances(32)
                .memories(8)
                .tables(8)
                .build()
        },
    )
}

/// Resource limits for transient processor execution.
#[derive(Debug, Clone, Copy)]
pub struct SimulationLimits {
    pub fuel: u64,
    pub memory_bytes: usize,
    pub timeout_ms: u64,
}

impl Default for SimulationLimits {
    fn default() -> Self {
        Self {
            fuel: PROCESSOR_FUEL,
            memory_bytes: 64 * 1024 * 1024,
            timeout_ms: 1000,
        }
    }
}

const PROCESSOR_FUEL: u64 = 10_000_000;

impl WasiView for HostState {
    fn ctx(&mut self) -> WasiCtxView<'_> {
        WasiCtxView {
            ctx: &mut self.wasi_ctx,
            table: &mut self.wasi_table,
        }
    }
}

/// Tick engine epochs only while a bounded invocation is active.
/// This evaluator runs synchronously, including outside a Tokio runtime. A
/// scoped, fallible OS worker must advance epochs while the caller executes
/// guest code. An async timer on that caller's runtime could be blocked by it.
struct EpochTicker {
    stop: std::sync::mpsc::Sender<()>,
    worker: Option<std::thread::JoinHandle<()>>,
}

impl EpochTicker {
    fn start(engine: Engine) -> Result<Self, EvaluatorExecutionError> {
        let (stop, receiver) = std::sync::mpsc::channel();
        let worker = std::thread::Builder::new()
            .name("simulation-wasm-budget".to_owned())
            .spawn(move || {
                while matches!(
                    receiver.recv_timeout(std::time::Duration::from_millis(10)),
                    Err(std::sync::mpsc::RecvTimeoutError::Timeout)
                ) {
                    engine.increment_epoch();
                }
            })
            .map_err(|_| EvaluatorExecutionError::Instantiation)?;
        Ok(Self {
            stop,
            worker: Some(worker),
        })
    }
}

impl Drop for EpochTicker {
    fn drop(&mut self) {
        let _ = self.stop.send(());
        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
    }
}

/// A pre-compiled WASM evaluator module ready for instantiation.
pub struct CompiledEvaluator {
    engine: Engine,
    component: Component,
    linker: Linker<HostState>,
    name: String,
}

impl CompiledEvaluator {
    /// Compile a WASM component from a file path.
    /// This is the expensive operation — do it once at startup or on hot-reload.
    pub fn from_file(name: &str, path: &Path) -> Result<Self, String> {
        let mut config = Config::new();
        config.consume_fuel(true);
        config.epoch_interruption(true);
        let engine =
            Engine::new(&config).map_err(|e| format!("failed to initialize WASM engine: {e}"))?;

        let component = Component::from_file(&engine, path)
            .map_err(|e| format!("failed to compile WASM component {}: {e}", path.display()))?;

        let mut linker = Linker::new(&engine);

        wasmtime_wasi::p2::add_to_linker_sync::<HostState>(&mut linker)
            .map_err(|e| format!("failed to link WASI: {e}"))?;

        host::link(&mut linker)?;

        Ok(Self {
            engine,
            component,
            linker,
            name: name.to_owned(),
        })
    }

    /// Only nonblocking, isolated imports are supported by simulation.
    pub(crate) fn supports_simulation(&self) -> bool {
        self.component
            .component_type()
            .imports(&self.engine)
            .all(|(name, _)| {
                name.starts_with("wanaku:evaluator/")
                    || name.starts_with("wasi:cli/environment@")
                    || name.starts_with("wasi:random/")
                    || name.starts_with("wasi:clocks/wall-clock@")
            })
    }

    fn configure_deadline(
        &self,
        store: &mut Store<HostState>,
        limits: Option<SimulationLimits>,
    ) -> Result<Option<EpochTicker>, EvaluatorExecutionError> {
        match limits {
            Some(limits) => {
                let deadline = std::time::Instant::now()
                    .checked_add(std::time::Duration::from_millis(limits.timeout_ms))
                    .ok_or(EvaluatorExecutionError::Instantiation)?;
                store.epoch_deadline_callback(move |_| {
                    if std::time::Instant::now() >= deadline {
                        Err(wasmtime::Error::msg("simulation_processor_timeout"))
                    } else {
                        Ok(wasmtime::UpdateDeadline::Continue(1))
                    }
                });
                store.set_epoch_deadline(1);
                EpochTicker::start(self.engine.clone()).map(Some)
            }
            None => {
                store.set_epoch_deadline(u64::MAX / 2);
                Ok(None)
            }
        }
    }

    /// Execute the evaluator with the given context.
    /// Creates a fresh WASM instance per call — no state sharing.
    #[expect(
        clippy::too_many_lines,
        reason = "WASM instantiation and execution pipeline"
    )]
    #[expect(
        clippy::needless_pass_by_value,
        reason = "ctx is moved into the WASM store"
    )]
    pub fn evaluate(
        &self,
        execution: ExecutionContext,
        ctx: host::types::EvaluationContext,
    ) -> Result<EvaluationResult, EvaluatorExecutionError> {
        let limits = execution.limits;
        let host_state = execution.into_host_state(&self.name);
        let mut store = Store::new(&self.engine, host_state);
        store.limiter(|state| &mut state.limits);
        store
            .set_fuel(limits.map_or(PROCESSOR_FUEL, |limits| limits.fuel))
            .map_err(|_| EvaluatorExecutionError::Instantiation)?;

        let _ticker = self.configure_deadline(&mut store, limits)?;

        let instance = match EvaluatorAction::instantiate(&mut store, &self.component, &self.linker)
        {
            Ok(i) => i,
            Err(e) => {
                if store.data().mode == ExecutionMode::Production {
                    tracing::error!(
                        evaluator = %self.name,
                        error = %e,
                        "failed to instantiate WASM evaluator"
                    );
                }
                return Err(EvaluatorExecutionError::Instantiation);
            }
        };

        if let Err(e) = instance.call_evaluate(&mut store, &ctx) {
            if store.data().mode == ExecutionMode::Production {
                tracing::error!(
                    evaluator = %self.name,
                    error = %e,
                    "WASM evaluator execution failed"
                );
            }
            return Err(EvaluatorExecutionError::Execution);
        }

        let state = store.into_data();
        Ok(EvaluationResult {
            action: state.action,
            side_effects_suppressed: state.side_effects_suppressed,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write as _;

    fn looping_processor() -> CompiledEvaluator {
        let path = Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/fuel-exhaustion.wat");
        CompiledEvaluator::from_file("fuel-exhaustion", &path).unwrap()
    }

    fn fuel_test_context() -> host::types::EvaluationContext {
        host::types::EvaluationContext {
            method: "tools/call".to_owned(),
            namespace: "default".to_owned(),
            tool_name: Some("test".to_owned()),
            arguments: Vec::new(),
            llm_result: "{}".to_owned(),
            conversation_id: None,
        }
    }

    #[test]
    fn infinite_loop_returns_execution_error() {
        let processor = looping_processor();
        let execution = ExecutionContext {
            registry: InMemoryRegistry::new(),
            interactions: InMemoryInteractionStore::new(10),
            compiled_schema: None,
            allow_side_effects: false,
            mode: ExecutionMode::Production,
            limits: None,
        };
        assert!(matches!(
            processor.evaluate(execution, fuel_test_context()),
            Err(EvaluatorExecutionError::Execution)
        ));
    }

    #[test]
    fn infinite_loop_traps_specifically_on_fuel_exhaustion() {
        let processor = looping_processor();
        let state = ExecutionContext {
            registry: InMemoryRegistry::new(),
            interactions: InMemoryInteractionStore::new(10),
            allow_side_effects: false,
            mode: ExecutionMode::Production,
            compiled_schema: None,
            limits: None,
        }
        .into_host_state("fuel-exhaustion");
        let mut store = Store::new(&processor.engine, state);
        store.set_fuel(PROCESSOR_FUEL).unwrap();
        store.set_epoch_deadline(u64::MAX / 2);
        let instance =
            EvaluatorAction::instantiate(&mut store, &processor.component, &processor.linker)
                .unwrap();
        let error = instance
            .call_evaluate(&mut store, &fuel_test_context())
            .unwrap_err();
        assert_eq!(
            error.downcast_ref::<wasmtime::Trap>(),
            Some(&wasmtime::Trap::OutOfFuel)
        );
        assert_eq!(store.get_fuel().unwrap(), 0);
    }

    #[test]
    fn production_store_remains_usable_after_simulation_epochs_advance() {
        let path = Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/pass.wat");
        let processor = CompiledEvaluator::from_file("pass", &path).unwrap();
        processor.engine.increment_epoch();
        let result = processor
            .evaluate(
                ExecutionContext {
                    registry: InMemoryRegistry::new(),
                    interactions: InMemoryInteractionStore::new(1),
                    compiled_schema: None,
                    allow_side_effects: true,
                    mode: ExecutionMode::Production,
                    limits: None,
                },
                fuel_test_context(),
            )
            .unwrap();
        assert!(matches!(result.action, ActionResult::Pass));
    }

    fn assert_compile_error(result: Result<CompiledEvaluator, String>) {
        match result {
            Ok(_) => unreachable!("expected compilation to fail"),
            Err(err) => assert!(
                err.contains("failed to compile WASM component"),
                "unexpected error: {err}"
            ),
        }
    }

    #[test]
    fn from_file_missing_path_is_err() {
        let path = Path::new("/nonexistent/definitely-not-here.wasm");
        assert_compile_error(CompiledEvaluator::from_file("missing", path));
    }

    #[test]
    fn from_file_garbage_bytes_is_err() {
        let mut file = tempfile::Builder::new()
            .suffix(".wasm")
            .tempfile()
            .expect("create temp file");
        file.write_all(b"this is not valid wasm at all")
            .expect("write garbage");
        file.flush().expect("flush");

        assert_compile_error(CompiledEvaluator::from_file("garbage", file.path()));
    }

    #[test]
    fn from_file_empty_file_is_err() {
        let file = tempfile::Builder::new()
            .suffix(".wasm")
            .tempfile()
            .expect("create temp file");
        let result = CompiledEvaluator::from_file("empty", file.path());
        assert!(result.is_err(), "empty file must fail to compile");
    }
}
