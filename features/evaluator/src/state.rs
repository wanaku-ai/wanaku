use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, MutexGuard, RwLock};

use wanaku_apis::metrics::MetricsStore;

use crate::config::{EvaluatorDef, LlmConnection};
use crate::engine::CompiledEvaluator;
use crate::revision::{
    RecordRevisionParams, Revision, RevisionError, RevisionOrigin, RevisionStore,
};
use crate::schema::CompiledSchema;

/// Immutable bundle of the runtime state derived from one evaluator
/// configuration: the evaluator definitions, the compiled WASM modules, and
/// the compiled result schemas.
///
/// All three are replaced together as a single `Arc` swap so that readers
/// always observe a self-consistent configuration. A concurrent activation can
/// never expose evaluator definitions from one revision alongside compiled
/// modules or schemas from another.
///
/// A request captures one snapshot at its start via
/// [`EvaluatorState::active_config`] and resolves its evaluator definition,
/// result schema, and WASM processor from that same snapshot. This guarantees a
/// request never mixes artifacts from different revisions, even if an activation
/// swaps in a new snapshot while the request awaits the LLM.
#[derive(Default)]
pub struct ActiveSnapshot {
    evaluators: Vec<EvaluatorDef>,
    compiled: HashMap<PathBuf, Arc<CompiledEvaluator>>,
    schemas: HashMap<String, Arc<CompiledSchema>>,
}

impl ActiveSnapshot {
    #[must_use]
    pub fn list_evaluators(&self) -> Vec<EvaluatorDef> {
        self.evaluators.clone()
    }

    #[must_use]
    pub fn find_matching(&self, method: &str, namespace: &str) -> Option<EvaluatorDef> {
        self.evaluators
            .iter()
            .find(|e| e.trigger.matches(method, namespace))
            .cloned()
    }

    #[must_use]
    pub fn get_compiled_schema(&self, evaluator_name: &str) -> Option<Arc<CompiledSchema>> {
        self.schemas.get(evaluator_name).cloned()
    }

    #[must_use]
    pub fn get_compiled(&self, path: &Path) -> Option<Arc<CompiledEvaluator>> {
        self.compiled.get(path).cloned()
    }
}

/// Shared state for the evaluator engine.
/// Holds the active configuration snapshot, namespace-to-conversation
/// bindings, LLM connections, and the revision store.
#[derive(Clone)]
pub struct EvaluatorState {
    active: Arc<RwLock<Arc<ActiveSnapshot>>>,
    bindings: Arc<RwLock<HashMap<String, String>>>,
    connections: Arc<RwLock<HashMap<String, LlmConnection>>>,
    metrics: Option<MetricsStore>,
    revisions: RevisionStore,
    /// Serializes revision commit and snapshot installation so that revision
    /// metadata and the active snapshot can never describe different
    /// configurations under concurrent activations.
    activation: Arc<Mutex<()>>,
}

impl EvaluatorState {
    #[must_use]
    pub fn new() -> Self {
        Self {
            active: Arc::new(RwLock::new(Arc::new(ActiveSnapshot::default()))),
            bindings: Arc::new(RwLock::new(HashMap::new())),
            connections: Arc::new(RwLock::new(HashMap::new())),
            metrics: None,
            revisions: RevisionStore::new(),
            activation: Arc::new(Mutex::new(())),
        }
    }

    pub fn with_metrics(mut self, store: MetricsStore) -> Self {
        self.metrics = Some(store);
        self
    }

    /// Replace the revision store with a persistence-backed one and load the
    /// persisted history (revisions, active pointer, ID counter).
    ///
    /// This does NOT install a runtime snapshot: the persisted active revision
    /// is re-validated, re-compiled, and installed later by
    /// [`Self::reconcile_startup`], after config-only LLM connections are
    /// loaded. Call during startup construction, before the state is shared or
    /// cloned into the pipeline.
    #[must_use]
    pub fn with_revision_persistence(
        mut self,
        backend: Arc<dyn crate::revision_persistence::RevisionPersistence>,
    ) -> Self {
        self.revisions = RevisionStore::with_persistence(backend);
        self
    }

    /// Reconcile the startup configuration with any persisted active revision
    /// and install the resulting runtime snapshot.
    ///
    /// Call once at startup, AFTER LLM connections are loaded, so activation can
    /// validate connection references exactly as it does at runtime. Behavior:
    ///
    /// - Startup config present and byte-identical to the persisted active
    ///   revision: keep that revision active and record no new one, but still
    ///   re-validate and re-compile it and install the snapshot (decision #1).
    /// - Startup config present and different (or no active revision): activate
    ///   it as a new revision through [`Self::try_activate`].
    /// - No startup config: re-activate the persisted active revision, if any,
    ///   without recording a new revision.
    ///
    /// Every path runs the same validation and compilation as a normal
    /// activation and fails closed. A restart therefore behaves exactly like a
    /// first boot with the same configuration: a revision that no longer
    /// validates or compiles on this host does not silently stay active — a
    /// rejected revision is recorded and the runtime is left without it.
    pub fn reconcile_startup(&self, startup_defs: Option<Vec<EvaluatorDef>>) {
        let active = self.revisions.active_revision();

        match startup_defs {
            Some(defs) => {
                if Self::matches_active(active.as_ref(), &defs) {
                    tracing::info!(
                        count = defs.len(),
                        "startup evaluator config matches persisted active revision; keeping it"
                    );
                    self.reinstall_active_revision();
                } else {
                    match self.try_activate(defs, RevisionOrigin::Startup, None, None) {
                        Ok(rev) => tracing::info!(
                            revision_id = rev.metadata.id,
                            "startup evaluator revision activated"
                        ),
                        Err(e) => tracing::error!(
                            error = %e,
                            "startup evaluator configuration rejected; no evaluators loaded"
                        ),
                    }
                }
            }
            None => {
                if active.is_some() {
                    self.reinstall_active_revision();
                }
            }
        }
    }

    /// Whether `defs` is byte-identical to the given active revision's
    /// configuration. False when there is no active revision or either checksum
    /// cannot be computed.
    fn matches_active(active: Option<&Revision>, defs: &[EvaluatorDef]) -> bool {
        let Some(active) = active else {
            return false;
        };
        match (
            crate::revision::config_checksum(&active.evaluators),
            crate::revision::config_checksum(defs),
        ) {
            (Some(a), Some(b)) => a == b,
            _ => false,
        }
    }

    /// Re-validate, re-compile, and install the already-recorded active revision
    /// as the live runtime snapshot, WITHOUT recording any new revision.
    ///
    /// This re-applies a revision that already exists in history, so it never
    /// appends to that history: doing so on every restart would churn the
    /// bounded history and, on a host where the revision no longer compiles,
    /// eventually evict all legitimate rollback history. Any failure (validation
    /// or compilation) is logged and leaves the runtime empty (fail closed). The
    /// revision stays as it was recorded; the failure is an operational
    /// condition of this host, surfaced through logs rather than history.
    fn reinstall_active_revision(&self) {
        let Some(active) = self.revisions.active_revision() else {
            return;
        };
        let revision_id = active.metadata.id;
        let defs = active.evaluators;

        if let Err(e) = validate_evaluator_names(&defs)
            .and_then(|()| validate_triggers(&defs))
            .and_then(|()| self.validate_llm_connections(&defs))
        {
            tracing::error!(
                revision_id = revision_id,
                error = %e,
                "persisted active evaluator revision failed validation on this host; runtime left empty"
            );
            return;
        }

        let (compiled, wasm_errors) = compile_wasm_map_with_errors(&defs);
        let (schemas, schema_errors) = try_compile_schemas(&defs);
        let errors = collect_errors(wasm_errors, schema_errors);

        let _activation = self.lock_activation();
        if !errors.is_empty() {
            tracing::error!(
                revision_id = revision_id,
                errors = %errors.join("; "),
                "persisted active evaluator revision failed to compile on this host; runtime left empty"
            );
            return;
        }

        self.install_snapshot(defs, compiled, schemas);
        tracing::info!(
            revision_id = revision_id,
            "restored active evaluator revision from persistence"
        );
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

    /// Seed the active snapshot directly, bypassing validation and revision
    /// recording.
    ///
    /// This is a TEST-ONLY helper. It compiles WASM and schemas leniently
    /// (dropping errors) and installs the definitions without recording a
    /// revision, so runtime state and revision history would diverge. It is
    /// gated behind the `test-util` feature and must never be used in
    /// production. The production path is [`Self::try_activate`], which
    /// validates, records a revision, and fails closed on any compilation
    /// error.
    #[cfg(any(test, feature = "test-util"))]
    pub fn load_evaluators(&self, defs: Vec<EvaluatorDef>) {
        let compiled = compile_wasm_map(&defs);
        let schemas = compile_schema_map(&defs);
        let _activation = self.lock_activation();
        self.install_snapshot(defs, compiled, schemas);
    }

    /// Validate, compile, and atomically activate a new evaluator
    /// configuration as a versioned revision.
    ///
    /// Validation and compilation run without holding the activation lock
    /// because they touch no shared state. The revision commit and the
    /// snapshot installation run under the activation lock as a single
    /// critical section, so revision metadata and the active snapshot are
    /// always replaced together.
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

        let (compiled_modules, wasm_errors) = compile_wasm_map_with_errors(&defs);
        let (compiled_schemas, schema_errors) = try_compile_schemas(&defs);

        let all_errors = collect_errors(wasm_errors, schema_errors);

        let _activation = self.lock_activation();

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

        // Commit the revision, install the runtime snapshot, then persist. The
        // in-memory commit and the snapshot install run back-to-back so readers
        // never see the new active revision before it is enforced; the disk
        // write happens afterward, still under the activation lock, so a slow
        // disk cannot widen that window.
        let revision = self.revisions.commit_revision(&RecordRevisionParams {
            evaluators: defs.clone(),
            origin,
            actor,
            expected_revision,
            activate: true,
            failure_reason: None,
        })?;

        self.install_snapshot(defs, compiled_modules, compiled_schemas);
        self.revisions.persist();
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

    /// Acquire the activation lock, recovering from poisoning. The critical
    /// section it guards performs no operation that can panic, so a poisoned
    /// lock indicates an unrelated panic elsewhere; the guarded data is `()`
    /// and remains valid, so recovery is safe.
    fn lock_activation(&self) -> MutexGuard<'_, ()> {
        match self.activation.lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        }
    }

    /// Return the current active snapshot. Cloning the `Arc` is cheap and lets
    /// a caller work against a stable, self-consistent configuration even if an
    /// activation swaps in a new snapshot concurrently.
    ///
    /// A request must capture the snapshot once at its start and resolve every
    /// per-request artifact (evaluator definition, result schema, WASM
    /// processor) from that same handle, so it never mixes artifacts from
    /// different revisions across an `await`.
    #[must_use]
    pub fn active_config(&self) -> Arc<ActiveSnapshot> {
        self.active
            .read()
            .map(|guard| Arc::clone(&guard))
            .unwrap_or_default()
    }

    /// Bundle the config-derived state into an immutable snapshot and replace
    /// the active one in a single `Arc` swap. Callers must hold the activation
    /// lock so the snapshot swap stays paired with its revision commit.
    fn install_snapshot(
        &self,
        evaluators: Vec<EvaluatorDef>,
        compiled: HashMap<PathBuf, Arc<CompiledEvaluator>>,
        schemas: HashMap<String, Arc<CompiledSchema>>,
    ) {
        let evaluator_count = evaluators.len() as u64;
        let wasm_count = compiled.len() as u64;
        let snapshot = Arc::new(ActiveSnapshot {
            evaluators,
            compiled,
            schemas,
        });
        if let Ok(mut guard) = self.active.write() {
            *guard = snapshot;
        }
        if let Some(ref store) = self.metrics {
            store.set_evaluators_loaded(evaluator_count);
            store.set_wasm_compiled(wasm_count);
        }
    }

    pub fn list_evaluators(&self) -> Vec<EvaluatorDef> {
        self.active_config().list_evaluators()
    }

    pub fn find_matching(&self, method: &str, namespace: &str) -> Option<EvaluatorDef> {
        self.active_config().find_matching(method, namespace)
    }

    pub fn get_compiled_schema(&self, evaluator_name: &str) -> Option<Arc<CompiledSchema>> {
        self.active_config().get_compiled_schema(evaluator_name)
    }

    pub fn get_compiled(&self, path: &Path) -> Option<Arc<CompiledEvaluator>> {
        self.active_config().get_compiled(path)
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

/// Best-effort WASM compilation, silently dropping any module that fails.
/// Test-only: serves [`EvaluatorState::load_evaluators`]. The production path
/// uses [`compile_wasm_map_with_errors`] so `try_activate` can fail closed.
#[cfg(any(test, feature = "test-util"))]
fn compile_wasm_map(defs: &[EvaluatorDef]) -> HashMap<PathBuf, Arc<CompiledEvaluator>> {
    let (compiled, _) = compile_wasm_map_with_errors(defs);
    compiled
}

/// Best-effort compilation of all result schemas, silently dropping any that
/// fail. Test-only: serves [`EvaluatorState::load_evaluators`]. The production
/// path uses [`try_compile_schemas`] so `try_activate` can surface errors.
#[cfg(any(test, feature = "test-util"))]
fn compile_schema_map(defs: &[EvaluatorDef]) -> HashMap<String, Arc<CompiledSchema>> {
    let (schemas, _) = try_compile_schemas(defs);
    schemas
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
