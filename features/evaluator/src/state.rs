use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, RwLock};

use wanaku_apis::metrics::MetricsStore;

use crate::config::{EvaluatorDef, LlmConnection};
use crate::engine::CompiledEvaluator;
use crate::revision::{
    RecordRevisionParams, Revision, RevisionError, RevisionOrigin, RevisionStore,
};
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
    connections: Arc<RwLock<HashMap<String, LlmConnection>>>,
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
            connections: Arc::new(RwLock::new(HashMap::new())),
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
    /// Load named LLM connections from config. Config-only: there is no
    /// management API route that calls this, so connections can never be
    /// set or changed at runtime by a client.
    ///
    /// Rejects the whole set (loading none) if any name is empty or
    /// duplicated. A silent first/last-wins collision would leave an
    /// evaluator referencing that name wired to the wrong endpoint and
    /// credential without any operator-visible signal.
    pub fn load_llm_connections(&self, connections: Vec<LlmConnection>) -> Result<(), String> {
        let mut seen = std::collections::HashSet::new();
        for conn in &connections {
            if conn.name.is_empty() {
                return Err("llm connection name must not be empty".to_owned());
            }
            if !seen.insert(&conn.name) {
                return Err(format!("duplicate llm connection name: '{}'", conn.name));
            }
        }

        let count = connections.len();
        let map = connections.into_iter().map(|c| (c.name.clone(), c)).collect();
        if let Ok(mut guard) = self.connections.write() {
            *guard = map;
        }
        tracing::info!(count = count, "LLM connections loaded from config");
        Ok(())
    }

    pub fn get_llm_connection(&self, name: &str) -> Option<LlmConnection> {
        self.connections
            .read()
            .ok()
            .and_then(|guard| guard.get(name).cloned())
    }

    /// Names of configured connections, for display/selection — never the
    /// model, URL, or credential, so this endpoint has nothing worth leaking.
    /// Sorted for a stable, deterministic order — `HashMap` iteration order
    /// is randomized per process and would otherwise vary across restarts
    /// even with an unchanged config.
    pub fn list_llm_connections(&self) -> Vec<String> {
        let mut names: Vec<String> = self
            .connections
            .read()
            .map(|guard| guard.keys().cloned().collect())
            .unwrap_or_default();
        names.sort();
        names
    }

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
    pub fn try_activate(
        &self,
        defs: Vec<EvaluatorDef>,
        origin: RevisionOrigin,
        actor: Option<String>,
        expected_revision: Option<u64>,
    ) -> Result<Revision, RevisionError> {
        validate_evaluator_names(&defs)?;
        validate_triggers(&defs)?;
        self.validate_llm_connections(&defs)?;

        let (compiled_modules, wasm_errors) = self.try_compile_modules(&defs);
        let (compiled_schemas, schema_errors) = try_compile_schemas(&defs);

        let all_errors = collect_errors(wasm_errors, schema_errors);
        if !all_errors.is_empty() {
            return self.reject_config(&RecordRevisionParams {
                evaluators: defs,
                origin,
                actor,
                expected_revision,
                activate: false,
                failure_reason: Some(all_errors.join("; ")),
            });
        }

        let revision = self.revisions.record_revision(&RecordRevisionParams {
            evaluators: defs.clone(),
            origin,
            actor,
            expected_revision,
            activate: true,
            failure_reason: None,
        })?;

        self.swap_active_state(defs, compiled_modules, compiled_schemas);
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
        self.try_activate(defs, RevisionOrigin::Api, None, expected_revision)
    }

    fn reject_config(
        &self,
        params: &RecordRevisionParams,
    ) -> Result<Revision, RevisionError> {
        let failure_reason = params
            .failure_reason
            .clone()
            .unwrap_or_default();
        tracing::warn!(
            errors = %failure_reason,
            "evaluator configuration rejected: validation/compilation failed"
        );
        let _rejected = self.revisions.record_revision(params)?;
        Err(RevisionError::ValidationFailed(failure_reason))
    }

    fn swap_active_state(
        &self,
        defs: Vec<EvaluatorDef>,
        compiled_modules: HashMap<PathBuf, Arc<CompiledEvaluator>>,
        compiled_schemas: HashMap<String, Arc<CompiledSchema>>,
    ) {
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

    fn compile_modules(&self, defs: &[EvaluatorDef]) {
        let compiled = compile_wasm_map(defs);
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

    fn try_compile_modules(
        &self,
        defs: &[EvaluatorDef],
    ) -> (HashMap<PathBuf, Arc<CompiledEvaluator>>, Vec<String>) {
        let (compiled, errors) = compile_wasm_map_with_errors(defs);
        if let Some(ref store) = self.metrics {
            store.set_wasm_compiled(compiled.len() as u64);
        }
        (compiled, errors)
    }

    /// Validate that every evaluator's `llm.connection` names a connection
    /// loaded from config. Connections are immutable after startup, so once
    /// an evaluator is active this can never later become dangling.
    fn validate_llm_connections(&self, defs: &[EvaluatorDef]) -> Result<(), RevisionError> {
        let guard = self.connections.read().map_err(|_| {
            RevisionError::ValidationFailed("LLM connection registry lock poisoned".to_owned())
        })?;
        for def in defs {
            if !guard.contains_key(&def.llm.connection) {
                return Err(RevisionError::ValidationFailed(format!(
                    "evaluator '{}': unknown llm connection '{}'",
                    def.name, def.llm.connection
                )));
            }
        }
        Ok(())
    }
}

fn compile_wasm_map(defs: &[EvaluatorDef]) -> HashMap<PathBuf, Arc<CompiledEvaluator>> {
    let (compiled, _) = compile_wasm_map_with_errors(defs);
    compiled
}

fn compile_wasm_map_with_errors(
    defs: &[EvaluatorDef],
) -> (HashMap<PathBuf, Arc<CompiledEvaluator>>, Vec<String>) {
    let mut compiled = HashMap::new();
    let mut errors = Vec::new();
    for def in defs {
        for path in collect_wasm_paths(def) {
            if compiled.contains_key(&path) {
                continue;
            }
            match compile_single_wasm(&def.name, &path) {
                Ok(module) => {
                    compiled.insert(path, module);
                }
                Err(msg) => errors.push(msg),
            }
        }
    }
    (compiled, errors)
}

fn compile_single_wasm(
    name: &str,
    path: &Path,
) -> Result<Arc<CompiledEvaluator>, String> {
    match CompiledEvaluator::from_file(name, path) {
        Ok(module) => {
            tracing::info!(evaluator = %name, path = %path.display(), "compiled WASM action module");
            Ok(Arc::new(module))
        }
        Err(e) => {
            tracing::error!(evaluator = %name, path = %path.display(), error = %e, "failed to compile WASM action module");
            Err(format!("evaluator '{name}': WASM compilation failed for {}: {e}", path.display()))
        }
    }
}

fn collect_errors(wasm_errors: Vec<String>, schema_errors: Vec<String>) -> Vec<String> {
    let mut all = Vec::with_capacity(wasm_errors.len() + schema_errors.len());
    all.extend(wasm_errors);
    all.extend(schema_errors);
    all
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
