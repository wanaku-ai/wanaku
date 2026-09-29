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

/// Capabilities and stores available during one processor invocation.
pub struct ExecutionContext {
    pub registry: InMemoryRegistry,
    pub interactions: InMemoryInteractionStore,
    pub compiled_schema: Option<Arc<CompiledSchema>>,
    pub allow_side_effects: bool,
}

#[derive(Debug)]
pub struct EvaluationResult {
    pub action: ActionResult,
    pub side_effects_suppressed: bool,
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
        let wasi_ctx = WasiCtxBuilder::new().build();

        let host_state = HostState {
            registry: execution.registry,
            interactions: execution.interactions,
            allow_side_effects: execution.allow_side_effects,
            side_effects_suppressed: false,
            action: ActionResult::Pass,
            evaluator_name: self.name.clone(),
            compiled_schema: execution.compiled_schema,
            wasi_ctx,
            wasi_table: ResourceTable::new(),
        };

        let mut store = Store::new(&self.engine, host_state);
        store
            .set_fuel(PROCESSOR_FUEL)
            .map_err(|_| EvaluatorExecutionError::Instantiation)?;

        let instance = match EvaluatorAction::instantiate(&mut store, &self.component, &self.linker)
        {
            Ok(i) => i,
            Err(e) => {
                tracing::error!(
                    evaluator = %self.name,
                    error = %e,
                    "failed to instantiate WASM evaluator"
                );
                return Err(EvaluatorExecutionError::Instantiation);
            }
        };

        if let Err(e) = instance.call_evaluate(&mut store, &ctx) {
            tracing::error!(
                evaluator = %self.name,
                error = %e,
                "WASM evaluator execution failed"
            );
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
        };
        assert!(matches!(
            processor.evaluate(execution, fuel_test_context()),
            Err(EvaluatorExecutionError::Execution)
        ));
    }

    #[test]
    fn infinite_loop_traps_specifically_on_fuel_exhaustion() {
        let processor = looping_processor();
        let state = HostState {
            registry: InMemoryRegistry::new(),
            interactions: InMemoryInteractionStore::new(10),
            allow_side_effects: false,
            side_effects_suppressed: false,
            action: ActionResult::Pass,
            evaluator_name: "fuel-exhaustion".to_owned(),
            compiled_schema: None,
            wasi_ctx: WasiCtxBuilder::new().build(),
            wasi_table: ResourceTable::new(),
        };
        let mut store = Store::new(&processor.engine, state);
        store.set_fuel(PROCESSOR_FUEL).unwrap();
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
