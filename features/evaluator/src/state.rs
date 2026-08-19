use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, RwLock};

use wanaku_apis::metrics::MetricsStore;

use crate::config::EvaluatorDef;
use crate::engine::CompiledEvaluator;
use crate::schema::CompiledSchema;

/// Shared state for the evaluator engine.
/// Holds loaded evaluator definitions, compiled WASM modules,
/// and namespace-to-conversation bindings.
#[derive(Clone)]
pub struct EvaluatorState {
    evaluators: Arc<RwLock<Vec<EvaluatorDef>>>,
    compiled: Arc<RwLock<HashMap<PathBuf, Arc<CompiledEvaluator>>>>,
    schemas: Arc<RwLock<HashMap<String, Arc<CompiledSchema>>>>,
    bindings: Arc<RwLock<HashMap<String, String>>>,
    metrics: Option<MetricsStore>,
}

impl EvaluatorState {
    #[must_use]
    pub fn new() -> Self {
        Self {
            evaluators: Arc::new(RwLock::new(Vec::new())),
            compiled: Arc::new(RwLock::new(HashMap::new())),
            schemas: Arc::new(RwLock::new(HashMap::new())),
            bindings: Arc::new(RwLock::new(HashMap::new())),
            metrics: None,
        }
    }

    pub fn with_metrics(mut self, store: MetricsStore) -> Self {
        self.metrics = Some(store);
        self
    }

}

impl Default for EvaluatorState {
    fn default() -> Self {
        Self::new()
    }
}

impl EvaluatorState {
    pub fn load_evaluators(&self, defs: Vec<EvaluatorDef>) {
        self.compile_modules(&defs);
        self.compile_schemas(&defs);
        let count = defs.len() as u64;
        if let Ok(mut guard) = self.evaluators.write() {
            *guard = defs;
        }
        if let Some(ref store) = self.metrics {
            store.set_evaluators_loaded(count);
        }
    }

    pub fn list_evaluators(&self) -> Vec<EvaluatorDef> {
        self.evaluators
            .read()
            .map(|guard| guard.clone())
            .unwrap_or_default()
    }

    pub fn find_matching(&self, method: &str, namespace: &str) -> Option<EvaluatorDef> {
        self.evaluators
            .read()
            .ok()
            .and_then(|guard| guard.iter().find(|e| e.trigger.matches(method, namespace)).cloned())
    }

    pub fn get_compiled_schema(&self, evaluator_name: &str) -> Option<Arc<CompiledSchema>> {
        self.schemas
            .read()
            .ok()
            .and_then(|guard| guard.get(evaluator_name).cloned())
    }

    pub fn get_compiled(&self, path: &Path) -> Option<Arc<CompiledEvaluator>> {
        self.compiled
            .read()
            .ok()
            .and_then(|guard| guard.get(path).cloned())
    }

    pub fn bind_namespace(&self, namespace: &str, conversation_id: &str) {
        if let Ok(mut guard) = self.bindings.write() {
            guard.insert(namespace.to_owned(), conversation_id.to_owned());
            if let Some(ref store) = self.metrics {
                store.set_namespace_bindings(guard.len() as u64);
            }
        }
    }

    pub fn unbind_namespace(&self, namespace: &str) {
        if let Ok(mut guard) = self.bindings.write() {
            guard.remove(namespace);
            if let Some(ref store) = self.metrics {
                store.set_namespace_bindings(guard.len() as u64);
            }
        }
    }

    pub fn get_binding(&self, namespace: &str) -> Option<String> {
        self.bindings
            .read()
            .ok()
            .and_then(|guard| guard.get(namespace).cloned())
    }

    pub fn list_bindings(&self) -> HashMap<String, String> {
        self.bindings
            .read()
            .map(|guard| guard.clone())
            .unwrap_or_default()
    }

    #[expect(clippy::too_many_lines, reason = "WASM compilation loop with error handling")]
    fn compile_modules(&self, defs: &[EvaluatorDef]) {
        let mut compiled = HashMap::new();

        for def in defs {
            let paths = collect_wasm_paths(def);
            for path in paths {
                if compiled.contains_key(&path) {
                    continue;
                }
                match CompiledEvaluator::from_file(&def.name, &path) {
                    Ok(module) => {
                        tracing::info!(
                            evaluator = %def.name,
                            path = %path.display(),
                            "compiled WASM action module"
                        );
                        compiled.insert(path, Arc::new(module));
                    }
                    Err(e) => {
                        tracing::error!(
                            evaluator = %def.name,
                            path = %path.display(),
                            error = %e,
                            "failed to compile WASM action module"
                        );
                    }
                }
            }
        }

        let count = compiled.len() as u64;
        if let Ok(mut guard) = self.compiled.write() {
            *guard = compiled;
        }
        if let Some(ref store) = self.metrics {
            store.set_wasm_compiled(count);
        }
    }

    fn compile_schemas(&self, defs: &[EvaluatorDef]) {
        let mut schemas = HashMap::new();

        for def in defs {
            if let Some(ref schema_val) = def.llm.result_schema
                && let Some(compiled) = CompiledSchema::compile(schema_val) {
                    tracing::info!(
                        evaluator = %def.name,
                        "compiled result schema"
                    );
                    schemas.insert(def.name.clone(), Arc::new(compiled));
                }
        }

        if let Ok(mut guard) = self.schemas.write() {
            *guard = schemas;
        }
    }
}

fn collect_wasm_paths(def: &EvaluatorDef) -> Vec<PathBuf> {
    vec![def.processor.path.clone()]
}
