use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, RwLock};

use wanaku_apis::metrics::MetricsStore;

use crate::config::EvaluatorDef;
use crate::engine::CompiledEvaluator;
use crate::revision::{Revision, RevisionError, RevisionOrigin, RevisionStore};
use crate::schema::CompiledSchema;

/// Shared state for the evaluator engine.
/// Holds loaded evaluator definitions, compiled WASM modules,
/// namespace-to-conversation bindings, and the revision store.
#[derive(Clone)]
pub struct EvaluatorState {
    evaluators: Arc<RwLock<Vec<EvaluatorDef>>>,
    compiled: Arc<RwLock<HashMap<PathBuf, Arc<CompiledEvaluator>>>>,
    schemas: Arc<RwLock<HashMap<String, Arc<CompiledSchema>>>>,
    bindings: Arc<RwLock<HashMap<String, String>>>,
    metrics: Option<MetricsStore>,
    revisions: RevisionStore,
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
            revisions: RevisionStore::new(),
        }
    }

    pub fn with_metrics(mut self, store: MetricsStore) -> Self {
        self.metrics = Some(store);
        self
    }

    /// Return a reference to the revision store for query operations.
    #[must_use]
    pub const fn revision_store(&self) -> &RevisionStore {
        &self.revisions
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

    /// Validate, compile, and atomically activate a new evaluator
    /// configuration as a versioned revision.
    ///
    /// If `expected_revision` is provided, the update is rejected with a
    /// conflict error when the current active revision does not match.
    ///
    /// Compilation errors cause the update to be rejected while the
    /// previously active configuration remains untouched.
    #[expect(
        clippy::too_many_lines,
        reason = "multi-step validation, compilation, and atomic activation pipeline"
    )]
    pub fn try_activate(
        &self,
        defs: Vec<EvaluatorDef>,
        origin: RevisionOrigin,
        actor: Option<String>,
        expected_revision: Option<u64>,
    ) -> Result<Revision, RevisionError> {
        // 1. Validate evaluator names are non-empty and unique.
        validate_evaluator_names(&defs)?;

        // 2. Validate triggers.
        validate_triggers(&defs)?;

        // 3. Compile WASM modules. Collect failures.
        let (compiled_modules, wasm_errors) = self.try_compile_modules(&defs);

        // 4. Compile schemas. Collect failures.
        let (compiled_schemas, schema_errors) = try_compile_schemas(&defs);

        // 5. If any compilation failed, record a rejected revision and return
        //    the failure. The active configuration is unchanged.
        let mut all_errors: Vec<String> = Vec::new();
        all_errors.extend(wasm_errors);
        all_errors.extend(schema_errors);

        if !all_errors.is_empty() {
            let failure_reason = all_errors.join("; ");
            tracing::warn!(
                errors = %failure_reason,
                "evaluator configuration rejected: validation/compilation failed"
            );

            // Record the rejected revision in history.
            let _rejected = self.revisions.record_revision(
                &defs,
                origin,
                actor,
                expected_revision,
                false,
                Some(failure_reason.clone()),
            )?;

            return Err(RevisionError::ValidationFailed(failure_reason));
        }

        // 6. Record the new active revision (with optimistic concurrency).
        let revision =
            self.revisions
                .record_revision(&defs, origin, actor, expected_revision, true, None)?;

        // 7. Atomically swap in the new evaluators and compiled artifacts.
        let count = defs.len() as u64;
        if let Ok(mut guard) = self.compiled.write() {
            *guard = compiled_modules;
        }
        if let Ok(mut guard) = self.schemas.write() {
            *guard = compiled_schemas;
        }
        if let Ok(mut guard) = self.evaluators.write() {
            *guard = defs;
        }
        if let Some(ref store) = self.metrics {
            store.set_evaluators_loaded(count);
        }

        tracing::info!(
            revision_id = revision.metadata.id,
            evaluator_count = count,
            "evaluator configuration activated"
        );

        Ok(revision)
    }

    /// Restore a previous revision's configuration as a new active revision.
    ///
    /// The restored configuration is re-validated and re-compiled. A new
    /// revision ID is assigned; historical data is never mutated.
    pub fn rollback(
        &self,
        source_id: u64,
        expected_revision: Option<u64>,
    ) -> Result<Revision, RevisionError> {
        let defs = self
            .revisions
            .restore_revision(source_id, expected_revision)?;
        self.try_activate(defs, RevisionOrigin::Api, None, None)
    }

    pub fn list_evaluators(&self) -> Vec<EvaluatorDef> {
        self.evaluators
            .read()
            .map(|guard| guard.clone())
            .unwrap_or_default()
    }

    pub fn find_matching(&self, method: &str, namespace: &str) -> Option<EvaluatorDef> {
        self.evaluators.read().ok().and_then(|guard| {
            guard
                .iter()
                .find(|e| e.trigger.matches(method, namespace))
                .cloned()
        })
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

    #[expect(
        clippy::too_many_lines,
        reason = "WASM compilation loop with error handling"
    )]
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
                && let Some(compiled) = CompiledSchema::compile(schema_val)
            {
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

    /// Try to compile all WASM modules. Returns the compiled map and a list
    /// of error messages for any modules that failed compilation.
    #[expect(
        clippy::too_many_lines,
        reason = "WASM compilation loop with error collection"
    )]
    fn try_compile_modules(
        &self,
        defs: &[EvaluatorDef],
    ) -> (HashMap<PathBuf, Arc<CompiledEvaluator>>, Vec<String>) {
        let mut compiled = HashMap::new();
        let mut errors = Vec::new();

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
                        let msg = format!(
                            "evaluator '{}': WASM compilation failed for {}: {e}",
                            def.name,
                            path.display()
                        );
                        tracing::error!(
                            evaluator = %def.name,
                            path = %path.display(),
                            error = %e,
                            "failed to compile WASM action module"
                        );
                        errors.push(msg);
                    }
                }
            }
        }

        if let Some(ref store) = self.metrics {
            store.set_wasm_compiled(compiled.len() as u64);
        }

        (compiled, errors)
    }
}

/// Try to compile all result schemas. Returns the compiled map and a list of
/// error messages for any schemas that failed compilation.
fn try_compile_schemas(
    defs: &[EvaluatorDef],
) -> (HashMap<String, Arc<CompiledSchema>>, Vec<String>) {
    let mut schemas = HashMap::new();
    let mut errors = Vec::new();

    for def in defs {
        if let Some(ref schema_val) = def.llm.result_schema {
            match CompiledSchema::compile(schema_val) {
                Some(compiled) => {
                    tracing::info!(evaluator = %def.name, "compiled result schema");
                    schemas.insert(def.name.clone(), Arc::new(compiled));
                }
                None => {
                    let msg = format!("evaluator '{}': result schema compilation failed", def.name);
                    errors.push(msg);
                }
            }
        }
    }

    (schemas, errors)
}

/// Validate that all evaluator names are non-empty and unique.
fn validate_evaluator_names(defs: &[EvaluatorDef]) -> Result<(), RevisionError> {
    let mut seen = std::collections::HashSet::new();
    for def in defs {
        if def.name.is_empty() {
            return Err(RevisionError::ValidationFailed(
                "evaluator name must not be empty".to_owned(),
            ));
        }
        if !seen.insert(&def.name) {
            return Err(RevisionError::ValidationFailed(format!(
                "duplicate evaluator name: '{}'",
                def.name
            )));
        }
    }
    Ok(())
}

/// Validate that all triggers reference valid methods.
fn validate_triggers(defs: &[EvaluatorDef]) -> Result<(), RevisionError> {
    for def in defs {
        if def.trigger.method.is_empty() {
            return Err(RevisionError::ValidationFailed(format!(
                "evaluator '{}': trigger method must not be empty",
                def.name
            )));
        }
    }
    Ok(())
}

fn collect_wasm_paths(def: &EvaluatorDef) -> Vec<PathBuf> {
    vec![def.processor.path.clone()]
}
