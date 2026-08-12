use std::path::Path;

use wasmtime::component::{Component, Linker, ResourceTable};
use wasmtime::{Engine, Store};
use wasmtime_wasi::{WasiCtxBuilder, WasiCtxView, WasiView};

use wanaku_praxis_apis::interactions::InMemoryInteractionStore;
use wanaku_praxis_apis::registry::InMemoryRegistry;

use crate::action::ActionResult;
use crate::host::{self, EvaluatorAction, HostState};

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
        let engine = Engine::default();

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
    pub fn evaluate(
        &self,
        registry: InMemoryRegistry,
        interactions: InMemoryInteractionStore,
        ctx: host::types::EvaluationContext,
    ) -> ActionResult {
        let wasi_ctx = WasiCtxBuilder::new().build();

        let host_state = HostState {
            registry,
            interactions,
            action: ActionResult::Pass,
            evaluator_name: self.name.clone(),
            wasi_ctx,
            wasi_table: ResourceTable::new(),
        };

        let mut store = Store::new(&self.engine, host_state);

        let instance = match EvaluatorAction::instantiate(&mut store, &self.component, &self.linker)
        {
            Ok(i) => i,
            Err(e) => {
                tracing::error!(
                    evaluator = %self.name,
                    error = %e,
                    "failed to instantiate WASM evaluator"
                );
                return ActionResult::Pass;
            }
        };

        if let Err(e) = instance.call_evaluate(&mut store, &ctx) {
            tracing::error!(
                evaluator = %self.name,
                error = %e,
                "WASM evaluator execution failed"
            );
            return ActionResult::Pass;
        }

        store.into_data().action
    }
}
