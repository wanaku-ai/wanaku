//! Transient evaluator preparation and execution without external calls or activation.
use std::collections::{HashMap, HashSet};
use std::sync::Arc;

use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::interactions::InMemoryInteractionStore;
use wanaku_types::mcp::McpContext;

use crate::action::ActionResult;
use crate::config::{EvaluationEngine, EvaluatorDef};
pub use crate::engine::SimulationLimits;
use crate::engine::{CompiledEvaluator, ExecutionContext};
use crate::schema::CompiledSchema;
use crate::state::EvaluatorState;

#[derive(Debug, Clone)]
pub struct ValidationIssue {
    pub evaluator_index: usize,
    pub reason_code: &'static str,
}

struct PreparedEvaluator {
    definition: EvaluatorDef,
    processor: Arc<CompiledEvaluator>,
    schema: Option<Arc<CompiledSchema>>,
}

pub struct PreparedEvaluators {
    evaluators: Vec<PreparedEvaluator>,
    bindings: HashMap<String, String>,
    limits: SimulationLimits,
}

pub struct SimulationInput<'a> {
    pub method: &'a str,
    pub namespace: &'a str,
    pub target: Option<&'a str>,
    pub arguments: &'a HashMap<String, String>,
}

#[derive(Debug)]
pub struct SimulationOutcome {
    pub evaluator: Option<String>,
    pub engine: Option<&'static str>,
    pub action: Option<ActionResult>,
    pub reason_code: &'static str,
    pub side_effects_suppressed: bool,
}

fn validation_codes(state: &EvaluatorState, definition: &EvaluatorDef) -> Vec<&'static str> {
    let definitions = std::slice::from_ref(definition);
    let mut codes = Vec::new();
    if crate::state::validate_evaluator_names(definitions).is_err() {
        codes.push("evaluator_name_invalid");
    }
    if crate::state::validate_triggers(definitions).is_err() {
        codes.push("evaluator_trigger_invalid");
    }
    if state.validate_engines(definitions).is_err() {
        codes.push("evaluator_connection_unavailable");
    }
    codes
}

fn prepare_schema(
    engine: &EvaluationEngine,
    codes: &mut Vec<&'static str>,
) -> Option<Arc<CompiledSchema>> {
    match engine {
        EvaluationEngine::Llm(llm) => llm.result_schema.as_ref().and_then(|value| {
            let compiled = CompiledSchema::compile_offline(value).map(Arc::new);
            if compiled.is_none() {
                codes.push("evaluator_schema_invalid");
            }
            compiled
        }),
        _ => None,
    }
}

fn prepare_definition(definition: EvaluatorDef) -> Result<PreparedEvaluator, Vec<&'static str>> {
    let mut codes = Vec::new();
    let schema = prepare_schema(&definition.engine, &mut codes);
    let processor = std::fs::metadata(&definition.processor.path)
        .ok()
        .filter(|metadata| metadata.is_file() && metadata.len() <= 16 * 1024 * 1024)
        .and_then(|_| {
            CompiledEvaluator::from_file(&definition.name, &definition.processor.path).ok()
        });
    if matches!(definition.engine, EvaluationEngine::Passthrough)
        && processor
            .as_ref()
            .is_some_and(|processor| !processor.supports_simulation())
    {
        codes.push("evaluator_processor_capability_unsupported");
    }
    if processor.is_none() {
        codes.push("evaluator_processor_invalid");
    }
    match processor {
        Some(processor) if codes.is_empty() => Ok(PreparedEvaluator {
            definition,
            processor: Arc::new(processor),
            schema,
        }),
        _ => Err(codes),
    }
}

fn reuse_active(
    state: &EvaluatorState,
    definitions: &[EvaluatorDef],
) -> Option<PreparedEvaluators> {
    let snapshot = state.try_active_config().ok()?;
    if snapshot.invalid_reason.is_some()
        || serde_json::to_value(definitions).ok()?
            != serde_json::to_value(snapshot.list_evaluators()).ok()?
    {
        return None;
    }
    let evaluators = definitions
        .iter()
        .map(|definition| {
            let processor = snapshot.get_compiled(&definition.processor.path)?;
            if matches!(definition.engine, EvaluationEngine::Passthrough)
                && !processor.supports_simulation()
            {
                return None;
            }
            Some(PreparedEvaluator {
                definition: definition.clone(),
                processor,
                schema: snapshot.get_compiled_schema(&definition.name),
            })
        })
        .collect::<Option<Vec<_>>>()?;
    Some(PreparedEvaluators {
        evaluators,
        bindings: state.list_bindings(),
        limits: SimulationLimits::default(),
    })
}

/// Validate every definition and compile transient processors. Never activate a revision.
pub fn prepare(
    state: &EvaluatorState,
    definitions: Vec<EvaluatorDef>,
) -> Result<PreparedEvaluators, Vec<ValidationIssue>> {
    if let Some(prepared) = reuse_active(state, &definitions) {
        return Ok(prepared);
    }
    let mut issues = Vec::new();
    let mut names = HashSet::new();
    let mut evaluators = Vec::new();
    for (index, definition) in definitions.into_iter().enumerate() {
        let mut codes = validation_codes(state, &definition);
        if !names.insert(definition.name.clone()) {
            codes.push("evaluator_name_invalid");
        }
        match prepare_definition(definition) {
            Ok(evaluator) => evaluators.push(evaluator),
            Err(preparation) => codes.extend(preparation),
        }
        issues.extend(codes.into_iter().map(|reason_code| ValidationIssue {
            evaluator_index: index,
            reason_code,
        }));
    }
    if issues.is_empty() {
        Ok(PreparedEvaluators {
            evaluators,
            bindings: state.list_bindings(),
            limits: SimulationLimits::default(),
        })
    } else {
        Err(issues)
    }
}

impl SimulationOutcome {
    fn new(evaluator: Option<&EvaluatorDef>) -> Self {
        Self {
            evaluator: evaluator.map(|definition| definition.name.clone()),
            engine: evaluator.map(|definition| definition.engine.kind()),
            action: None,
            side_effects_suppressed: false,
            reason_code: if evaluator.is_some() {
                "evaluator_external_engine_skipped"
            } else {
                "evaluator_not_matched"
            },
        }
    }
}

impl PreparedEvaluators {
    #[must_use]
    pub const fn with_limits(mut self, limits: SimulationLimits) -> Self {
        self.limits = limits;
        self
    }

    /// Execute the first matching processor with external engines suppressed.
    pub fn evaluate(
        &self,
        input: &SimulationInput<'_>,
        registry: &InMemoryRegistry,
    ) -> SimulationOutcome {
        self.evaluate_with_limits(input, registry, self.limits)
    }

    pub fn evaluate_with_limits(
        &self,
        input: &SimulationInput<'_>,
        registry: &InMemoryRegistry,
        limits: SimulationLimits,
    ) -> SimulationOutcome {
        let evaluator = self.evaluators.iter().find(|evaluator| {
            evaluator
                .definition
                .trigger
                .matches(input.method, input.namespace)
        });
        let outcome = SimulationOutcome::new(evaluator.map(|entry| &entry.definition));
        let Some(evaluator) = evaluator else {
            return outcome;
        };
        if !matches!(evaluator.definition.engine, EvaluationEngine::Passthrough) {
            return outcome;
        }
        self.run_processor(evaluator, input, registry, limits)
    }

    fn run_processor(
        &self,
        evaluator: &PreparedEvaluator,
        input: &SimulationInput<'_>,
        registry: &InMemoryRegistry,
        limits: SimulationLimits,
    ) -> SimulationOutcome {
        let mut outcome = SimulationOutcome::new(Some(&evaluator.definition));
        let Some(context) = self.processor_context(input) else {
            outcome.reason_code = "evaluator_internal_error";
            return outcome;
        };
        let execution = ExecutionContext {
            registry: registry.clone(),
            interactions: InMemoryInteractionStore::new(1),
            compiled_schema: evaluator.schema.clone(),
            allow_side_effects: false,
            mode: crate::engine::ExecutionMode::Simulation,
            limits: Some(limits),
        };
        match evaluator.processor.evaluate(execution, context) {
            Ok(result) => {
                outcome.reason_code = "evaluator_simulated";
                outcome.action = Some(result.action);
                outcome.side_effects_suppressed = result.side_effects_suppressed;
            }
            Err(_) => outcome.reason_code = "evaluator_processor_failed",
        }
        outcome
    }

    fn processor_context(
        &self,
        input: &SimulationInput<'_>,
    ) -> Option<crate::wit_types::EvaluationContext> {
        let mcp = McpContext::new(input.method, input.target, input.arguments, &[], &[]);
        Some(crate::wit_types::EvaluationContext {
            method: input.method.to_owned(),
            namespace: input.namespace.to_owned(),
            tool_name: input.target.map(str::to_owned),
            arguments: input
                .arguments
                .iter()
                .map(|(key, value)| (key.clone(), value.clone()))
                .collect(),
            llm_result: crate::evaluation::passthrough_input(&mcp).ok()?,
            conversation_id: input
                .arguments
                .get(wanaku_types::correlation::REQUEST_ID_ARG)
                .cloned()
                .or_else(|| self.bindings.get(input.namespace).cloned()),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::{ProcessorRef, TriggerDef};

    fn definition(method: &str) -> EvaluatorDef {
        EvaluatorDef {
            name: "test".to_owned(),
            trigger: TriggerDef {
                method: method.to_owned(),
                namespace: None,
            },
            engine: EvaluationEngine::Passthrough,
            processor: ProcessorRef {
                path: std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
                    .join("tests/fixtures/fuel-exhaustion.wat"),
            },
        }
    }

    #[test]
    fn preparation_aggregates_safe_issues_without_activation() {
        let state = EvaluatorState::new();
        let before = state.active_config().revision_id;
        let mut invalid = definition("");
        invalid.name.clear();
        invalid.processor.path = "/secret/config/invalid.wasm".into();
        let issues = prepare(&state, vec![invalid])
            .err()
            .expect("invalid configuration rejected");
        assert_eq!(issues.len(), 3);
        assert!(issues.iter().all(|issue| issue.evaluator_index == 0));
        assert!(!format!("{issues:?}").contains("secret"));
        assert_eq!(state.active_config().revision_id, before);
        assert!(state.list_evaluators().is_empty());
    }

    #[test]
    fn external_engine_is_skipped_before_processor_execution() {
        let state = EvaluatorState::new();
        state
            .load_llm_connections(vec![crate::config::LlmConnection {
                name: "remote".to_owned(),
                model: "test".to_owned(),
                url: "http://127.0.0.1:1".to_owned(),
                api_key: "secret".to_owned(),
            }])
            .unwrap();
        let mut def = definition("resources/read");
        def.engine = EvaluationEngine::Llm(crate::config::LlmDef {
            connection: "remote".to_owned(),
            prompt: "secret".to_owned(),
            operation: crate::config::LlmOperation::Classify,
            result_schema: None,
        });
        let prepared = prepare(&state, vec![def]).expect("valid evaluator");
        let arguments = HashMap::new();
        let result = prepared.evaluate(
            &SimulationInput {
                method: "resources/read",
                namespace: "default",
                target: Some("uri"),
                arguments: &arguments,
            },
            &InMemoryRegistry::new(),
        );
        assert_eq!(result.reason_code, "evaluator_external_engine_skipped");
        assert!(result.action.is_none());
    }

    #[test]
    fn passthrough_prompt_processor_is_fuel_bounded() {
        let prepared = prepare(&EvaluatorState::new(), vec![definition("prompts/get")])
            .expect("valid evaluator");
        let arguments = HashMap::new();
        let result = prepared.evaluate(
            &SimulationInput {
                method: "prompts/get",
                namespace: "default",
                target: Some("prompt"),
                arguments: &arguments,
            },
            &InMemoryRegistry::new(),
        );
        assert_eq!(result.reason_code, "evaluator_processor_failed");
    }

    #[test]
    fn resource_passthrough_proposes_pass_without_changing_state() {
        let state = EvaluatorState::new();
        state.bind_namespace("default", "conversation");
        let mut def = definition("resources/read");
        def.processor.path =
            std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/pass.wat");
        let prepared = prepare(&state, vec![def]).expect("valid evaluator");
        let arguments = HashMap::new();
        let result = prepared.evaluate(
            &SimulationInput {
                method: "resources/read",
                namespace: "default",
                target: Some("test://uri"),
                arguments: &arguments,
            },
            &InMemoryRegistry::new(),
        );
        assert_eq!(result.reason_code, "evaluator_simulated");
        assert!(matches!(result.action, Some(ActionResult::Pass)));
        assert_eq!(
            state.get_binding("default").as_deref(),
            Some("conversation")
        );
        assert!(state.list_evaluators().is_empty());
        assert_eq!(state.active_config().revision_id, None);
    }

    #[test]
    fn configurable_memory_limit_rejects_processor_instantiation() {
        let mut def = definition("tools/call");
        def.processor.path =
            std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/pass.wat");
        let prepared = prepare(&EvaluatorState::new(), vec![def])
            .expect("valid evaluator")
            .with_limits(SimulationLimits {
                fuel: 10_000,
                memory_bytes: 0,
                timeout_ms: 1000,
            });
        let arguments = HashMap::new();
        let result = prepared.evaluate(
            &SimulationInput {
                method: "tools/call",
                namespace: "default",
                target: Some("tool"),
                arguments: &arguments,
            },
            &InMemoryRegistry::new(),
        );
        assert_eq!(result.reason_code, "evaluator_processor_failed");
    }

    #[test]
    fn wall_budget_stops_loop_even_with_unlimited_fuel() {
        let prepared = prepare(&EvaluatorState::new(), vec![definition("tools/call")])
            .expect("valid evaluator")
            .with_limits(SimulationLimits {
                fuel: u64::MAX,
                memory_bytes: 64 * 1024 * 1024,
                timeout_ms: 10,
            });
        let arguments = HashMap::new();
        let start = std::time::Instant::now();
        let result = prepared.evaluate(
            &SimulationInput {
                method: "tools/call",
                namespace: "default",
                target: Some("tool"),
                arguments: &arguments,
            },
            &InMemoryRegistry::new(),
        );
        assert_eq!(result.reason_code, "evaluator_processor_failed");
        assert!(start.elapsed() < std::time::Duration::from_secs(5));
    }

    fn timed_loop(prepared: &PreparedEvaluators, timeout_ms: u64) -> std::time::Duration {
        let arguments = HashMap::new();
        let start = std::time::Instant::now();
        let result = prepared.evaluate_with_limits(
            &SimulationInput {
                method: "tools/call",
                namespace: "default",
                target: Some("tool"),
                arguments: &arguments,
            },
            &InMemoryRegistry::new(),
            SimulationLimits {
                fuel: u64::MAX,
                memory_bytes: 64 * 1024 * 1024,
                timeout_ms,
            },
        );
        assert_eq!(result.reason_code, "evaluator_processor_failed");
        start.elapsed()
    }

    #[test]
    fn shared_processor_concurrent_calls_keep_independent_wall_deadlines() {
        let prepared = prepare(&EvaluatorState::new(), vec![definition("tools/call")])
            .expect("valid evaluator");
        let barrier = std::sync::Barrier::new(2);
        let (short, long) = std::thread::scope(|scope| {
            let short = scope.spawn(|| {
                barrier.wait();
                timed_loop(&prepared, 30)
            });
            let long = scope.spawn(|| {
                barrier.wait();
                timed_loop(&prepared, 180)
            });
            (short.join().unwrap(), long.join().unwrap())
        });
        assert!(short >= std::time::Duration::from_millis(30));
        assert!(long >= std::time::Duration::from_millis(180));
        assert!(long < std::time::Duration::from_secs(5));
    }

    #[test]
    fn wasm_host_mutation_is_suppressed() {
        use wanaku_types::registry::ToolRegistry as _;
        let registry = InMemoryRegistry::new();
        let tool = serde_json::from_value(serde_json::json!({
            "name": "tool", "description": "test", "uri": "test://tool", "type": "mcp-forward",
            "input_schema": {}, "namespace": "default"
        }))
        .expect("tool");
        registry.register_tool(tool);
        let mut def = definition("tools/call");
        def.processor.path = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("tests/fixtures/side-effects.wat");
        let prepared = prepare(&EvaluatorState::new(), vec![def]).expect("valid evaluator");
        let arguments = HashMap::new();
        let result = prepared.evaluate(
            &SimulationInput {
                method: "tools/call",
                namespace: "default",
                target: Some("tool"),
                arguments: &arguments,
            },
            &registry,
        );
        assert_eq!(result.reason_code, "evaluator_simulated");
        assert!(result.side_effects_suppressed);
        assert_eq!(
            registry.get_tool("tool").unwrap().namespace.as_deref(),
            Some("default")
        );
    }

    #[test]
    fn active_compiled_processor_survives_file_replacement() {
        let state = EvaluatorState::new();
        let directory = tempfile::tempdir().expect("directory");
        let path = directory.path().join("processor.wat");
        std::fs::write(&path, include_str!("../tests/fixtures/pass.wat")).unwrap();
        let mut definition = definition("tools/call");
        definition.processor.path = path.clone();
        state
            .try_activate(
                vec![definition.clone()],
                crate::revision::RevisionOrigin::Api,
                None,
                None,
            )
            .expect("activate");
        std::fs::write(&path, "changed invalid component").unwrap();
        let prepared = prepare(&state, vec![definition]).expect("active processor reused");
        let arguments = HashMap::new();
        let result = prepared.evaluate(
            &SimulationInput {
                method: "tools/call",
                namespace: "default",
                target: Some("tool"),
                arguments: &arguments,
            },
            &InMemoryRegistry::new(),
        );
        assert_eq!(result.reason_code, "evaluator_simulated");
        assert!(matches!(result.action, Some(ActionResult::Pass)));
    }

    #[test]
    fn blocking_wasi_import_is_rejected_before_execution() {
        let file = tempfile::NamedTempFile::new().expect("file");
        let component = include_str!("../tests/fixtures/pass.wat").replacen(
            "(component",
            r#"(component (import "wasi:io/poll@0.2.6" (instance))"#,
            1,
        );
        std::fs::write(file.path(), component).unwrap();
        let mut definition = definition("tools/call");
        definition.processor.path = file.path().to_path_buf();
        let issues = prepare(&EvaluatorState::new(), vec![definition])
            .err()
            .expect("unsupported capability");
        assert!(
            issues
                .iter()
                .any(|issue| issue.reason_code == "evaluator_processor_capability_unsupported")
        );
    }

    #[test]
    fn candidate_schema_never_retrieves_external_reference() {
        assert!(
            CompiledSchema::compile_offline(&serde_json::json!({
                "$ref": "http://127.0.0.1:1/secret"
            }))
            .is_none()
        );
        assert!(
            CompiledSchema::compile_offline(&serde_json::json!({
                "$defs": {"flag": {"type": "boolean"}}, "$ref": "#/$defs/flag"
            }))
            .is_some()
        );
    }
}
