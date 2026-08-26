use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::Duration;

use dashmap::DashMap;
use serde::Serialize;

#[derive(Clone, Default)]
pub struct MetricsStore(Arc<StoreInner>);

#[derive(Default)]
struct StoreInner {
    filters: DashMap<String, FilterCounters>,
    evaluators: DashMap<String, EvaluatorCounters>,
    pipeline: PipelineCounters,
    gauges: GaugeValues,
}

// ---------------------------------------------------------------------------
// Atomic building blocks
// ---------------------------------------------------------------------------

#[derive(Default)]
struct AtomicCounter(AtomicU64);

impl AtomicCounter {
    fn increment(&self) {
        self.0.fetch_add(1, Ordering::Relaxed);
    }
    fn get(&self) -> u64 {
        self.0.load(Ordering::Relaxed)
    }
}

#[derive(Default)]
struct AtomicGauge(AtomicU64);

impl AtomicGauge {
    fn set(&self, value: u64) {
        self.0.store(value, Ordering::Relaxed);
    }
    fn get(&self) -> u64 {
        self.0.load(Ordering::Relaxed)
    }
}

#[derive(Default)]
struct DurationAccumulator {
    count: AtomicU64,
    sum_ns: AtomicU64,
}

impl DurationAccumulator {
    fn record(&self, duration: Duration) {
        #[expect(clippy::cast_possible_truncation, reason = "sub-second durations fit in u64 nanoseconds")]
        let ns = duration.as_nanos() as u64;
        self.count.fetch_add(1, Ordering::Relaxed);
        self.sum_ns.fetch_add(ns, Ordering::Relaxed);
    }
    fn snapshot(&self) -> DurationSnapshot {
        let count = self.count.load(Ordering::Relaxed);
        let sum_ns = self.sum_ns.load(Ordering::Relaxed);
        let avg_ms = if count > 0 {
            (sum_ns as f64) / (count as f64) / 1_000_000.0
        } else {
            0.0
        };
        DurationSnapshot {
            count,
            total_ms: (sum_ns as f64) / 1_000_000.0,
            avg_ms,
        }
    }
}

// ---------------------------------------------------------------------------
// Per-filter counters
// ---------------------------------------------------------------------------

#[derive(Default)]
struct FilterCounters {
    requests_continue: AtomicCounter,
    requests_reject: AtomicCounter,
    requests_other: AtomicCounter,
    errors: AtomicCounter,
    duration: DurationAccumulator,
}

// ---------------------------------------------------------------------------
// Per-evaluator counters
// ---------------------------------------------------------------------------

#[derive(Default)]
struct EvaluatorCounters {
    decisions_pass: AtomicCounter,
    decisions_block: AtomicCounter,
    decisions_reject_malformed: AtomicCounter,
    decisions_warn: AtomicCounter,
    decisions_filter_tools: AtomicCounter,
    decisions_set_metadata: AtomicCounter,

    llm_calls_success: AtomicCounter,
    llm_calls_failure: AtomicCounter,
    llm_empty_results: AtomicCounter,
    llm_duration: DurationAccumulator,

    schema_validations_pass: AtomicCounter,
    schema_validations_fail: AtomicCounter,
    schema_retries_success: AtomicCounter,
    schema_retries_failure: AtomicCounter,

    wasm_executions: AtomicCounter,
    wasm_duration: DurationAccumulator,
    wasm_not_found: AtomicCounter,

    pipeline_duration: DurationAccumulator,
}

// ---------------------------------------------------------------------------
// Pipeline-level counters (not per-evaluator)
// ---------------------------------------------------------------------------

#[derive(Default)]
struct PipelineCounters {
    trigger_matches: AtomicCounter,
    trigger_misses: AtomicCounter,
    skipped_no_method: AtomicCounter,
    skipped_no_state: AtomicCounter,
    skipped_no_match: AtomicCounter,
    skipped_no_registry: AtomicCounter,
    skipped_no_interactions: AtomicCounter,
}

// ---------------------------------------------------------------------------
// Gauge values
// ---------------------------------------------------------------------------

#[derive(Default)]
struct GaugeValues {
    evaluators_loaded: AtomicGauge,
    wasm_compiled: AtomicGauge,
    namespace_bindings: AtomicGauge,
}

// ---------------------------------------------------------------------------
// Recording API
// ---------------------------------------------------------------------------

pub enum FilterResult {
    Continue,
    Reject,
    Other,
    Error,
}

pub enum SkipReason {
    MissingMethod,
    MissingState,
    Unmatched,
    MissingRegistry,
    MissingInteractions,
}

impl MetricsStore {
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    pub fn record_filter_result(
        &self,
        filter: &str,
        result: &FilterResult,
        duration: Duration,
    ) {
        let counters = self.0.filters.entry(filter.to_owned()).or_default();
        match result {
            FilterResult::Continue => counters.requests_continue.increment(),
            FilterResult::Reject => counters.requests_reject.increment(),
            FilterResult::Other => counters.requests_other.increment(),
            FilterResult::Error => counters.errors.increment(),
        }
        counters.duration.record(duration);
    }

    pub fn record_evaluator_decision(&self, evaluator: &str, action: &str) {
        let counters = self.0.evaluators.entry(evaluator.to_owned()).or_default();
        match action {
            "pass" => counters.decisions_pass.increment(),
            "block" => counters.decisions_block.increment(),
            "reject_malformed" => counters.decisions_reject_malformed.increment(),
            "warn" => counters.decisions_warn.increment(),
            "filter_tools" => counters.decisions_filter_tools.increment(),
            "set_metadata" => counters.decisions_set_metadata.increment(),
            _ => {}
        }
    }

    pub fn record_llm_call(&self, evaluator: &str, success: bool, duration: Duration) {
        let counters = self.0.evaluators.entry(evaluator.to_owned()).or_default();
        if success {
            counters.llm_calls_success.increment();
        } else {
            counters.llm_calls_failure.increment();
        }
        counters.llm_duration.record(duration);
    }

    pub fn record_llm_empty_result(&self, evaluator: &str) {
        let counters = self.0.evaluators.entry(evaluator.to_owned()).or_default();
        counters.llm_empty_results.increment();
    }

    pub fn record_schema_validation(&self, evaluator: &str, passed: bool) {
        let counters = self.0.evaluators.entry(evaluator.to_owned()).or_default();
        if passed {
            counters.schema_validations_pass.increment();
        } else {
            counters.schema_validations_fail.increment();
        }
    }

    pub fn record_schema_retry(&self, evaluator: &str, success: bool) {
        let counters = self.0.evaluators.entry(evaluator.to_owned()).or_default();
        if success {
            counters.schema_retries_success.increment();
        } else {
            counters.schema_retries_failure.increment();
        }
    }

    pub fn record_wasm_execution(&self, evaluator: &str, duration: Duration) {
        let counters = self.0.evaluators.entry(evaluator.to_owned()).or_default();
        counters.wasm_executions.increment();
        counters.wasm_duration.record(duration);
    }

    pub fn record_wasm_not_found(&self, evaluator: &str) {
        let counters = self.0.evaluators.entry(evaluator.to_owned()).or_default();
        counters.wasm_not_found.increment();
    }

    pub fn record_pipeline_duration(&self, evaluator: &str, duration: Duration) {
        let counters = self.0.evaluators.entry(evaluator.to_owned()).or_default();
        counters.pipeline_duration.record(duration);
    }

    pub fn record_trigger_match(&self, matched: bool) {
        if matched {
            self.0.pipeline.trigger_matches.increment();
        } else {
            self.0.pipeline.trigger_misses.increment();
        }
    }

    pub fn record_skip(&self, reason: &SkipReason) {
        match reason {
            SkipReason::MissingMethod => self.0.pipeline.skipped_no_method.increment(),
            SkipReason::MissingState => self.0.pipeline.skipped_no_state.increment(),
            SkipReason::Unmatched => self.0.pipeline.skipped_no_match.increment(),
            SkipReason::MissingRegistry => self.0.pipeline.skipped_no_registry.increment(),
            SkipReason::MissingInteractions => self.0.pipeline.skipped_no_interactions.increment(),
        }
    }

    pub fn set_evaluators_loaded(&self, count: u64) {
        self.0.gauges.evaluators_loaded.set(count);
    }

    pub fn set_wasm_compiled(&self, count: u64) {
        self.0.gauges.wasm_compiled.set(count);
    }

    pub fn set_namespace_bindings(&self, count: u64) {
        self.0.gauges.namespace_bindings.set(count);
    }

    #[must_use]
    #[expect(clippy::too_many_lines, reason = "snapshot assembly maps all metric groups")]
    pub fn snapshot(&self) -> MetricsSnapshot {
        let filters = self
            .0
            .filters
            .iter()
            .map(|entry| {
                let c = entry.value();
                (
                    entry.key().clone(),
                    FilterSnapshot {
                        requests_continue: c.requests_continue.get(),
                        requests_reject: c.requests_reject.get(),
                        requests_other: c.requests_other.get(),
                        errors: c.errors.get(),
                        duration: c.duration.snapshot(),
                    },
                )
            })
            .collect();

        let evaluators = self
            .0
            .evaluators
            .iter()
            .map(|entry| {
                let c = entry.value();
                (
                    entry.key().clone(),
                    EvaluatorSnapshot {
                        decisions: DecisionSnapshot {
                            pass: c.decisions_pass.get(),
                            block: c.decisions_block.get(),
                            reject_malformed: c.decisions_reject_malformed.get(),
                            warn: c.decisions_warn.get(),
                            filter_tools: c.decisions_filter_tools.get(),
                            set_metadata: c.decisions_set_metadata.get(),
                        },
                        llm: LlmSnapshot {
                            calls_success: c.llm_calls_success.get(),
                            calls_failure: c.llm_calls_failure.get(),
                            empty_results: c.llm_empty_results.get(),
                            duration: c.llm_duration.snapshot(),
                        },
                        schema: SchemaSnapshot {
                            validations_pass: c.schema_validations_pass.get(),
                            validations_fail: c.schema_validations_fail.get(),
                            retries_success: c.schema_retries_success.get(),
                            retries_failure: c.schema_retries_failure.get(),
                        },
                        wasm: WasmSnapshot {
                            executions: c.wasm_executions.get(),
                            not_found: c.wasm_not_found.get(),
                            duration: c.wasm_duration.snapshot(),
                        },
                        pipeline_duration: c.pipeline_duration.snapshot(),
                    },
                )
            })
            .collect();

        let p = &self.0.pipeline;
        let pipeline = PipelineSnapshot {
            trigger_matches: p.trigger_matches.get(),
            trigger_misses: p.trigger_misses.get(),
            skipped_no_method: p.skipped_no_method.get(),
            skipped_no_state: p.skipped_no_state.get(),
            skipped_no_match: p.skipped_no_match.get(),
            skipped_no_registry: p.skipped_no_registry.get(),
            skipped_no_interactions: p.skipped_no_interactions.get(),
        };

        let g = &self.0.gauges;
        let gauges = GaugeSnapshot {
            evaluators_loaded: g.evaluators_loaded.get(),
            wasm_compiled: g.wasm_compiled.get(),
            namespace_bindings: g.namespace_bindings.get(),
        };

        MetricsSnapshot {
            filters,
            evaluators,
            pipeline,
            gauges,
        }
    }
}

// ---------------------------------------------------------------------------
// Serializable snapshots
// ---------------------------------------------------------------------------

#[derive(Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct MetricsSnapshot {
    pub filters: HashMap<String, FilterSnapshot>,
    pub evaluators: HashMap<String, EvaluatorSnapshot>,
    pub pipeline: PipelineSnapshot,
    pub gauges: GaugeSnapshot,
}

#[derive(Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct FilterSnapshot {
    pub requests_continue: u64,
    pub requests_reject: u64,
    pub requests_other: u64,
    pub errors: u64,
    pub duration: DurationSnapshot,
}

#[derive(Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct DurationSnapshot {
    pub count: u64,
    pub total_ms: f64,
    pub avg_ms: f64,
}

#[derive(Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct EvaluatorSnapshot {
    pub decisions: DecisionSnapshot,
    pub llm: LlmSnapshot,
    pub schema: SchemaSnapshot,
    pub wasm: WasmSnapshot,
    pub pipeline_duration: DurationSnapshot,
}

#[derive(Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct DecisionSnapshot {
    pub pass: u64,
    pub block: u64,
    pub reject_malformed: u64,
    pub warn: u64,
    pub filter_tools: u64,
    pub set_metadata: u64,
}

#[derive(Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct LlmSnapshot {
    pub calls_success: u64,
    pub calls_failure: u64,
    pub empty_results: u64,
    pub duration: DurationSnapshot,
}

#[derive(Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct SchemaSnapshot {
    pub validations_pass: u64,
    pub validations_fail: u64,
    pub retries_success: u64,
    pub retries_failure: u64,
}

#[derive(Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct WasmSnapshot {
    pub executions: u64,
    pub not_found: u64,
    pub duration: DurationSnapshot,
}

#[derive(Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct PipelineSnapshot {
    pub trigger_matches: u64,
    pub trigger_misses: u64,
    pub skipped_no_method: u64,
    pub skipped_no_state: u64,
    pub skipped_no_match: u64,
    pub skipped_no_registry: u64,
    pub skipped_no_interactions: u64,
}

#[derive(Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct GaugeSnapshot {
    pub evaluators_loaded: u64,
    pub wasm_compiled: u64,
    pub namespace_bindings: u64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn filter_result_recording() {
        let store = MetricsStore::new();
        store.record_filter_result("test_filter", &FilterResult::Continue, Duration::from_millis(1));
        store.record_filter_result("test_filter", &FilterResult::Continue, Duration::from_millis(2));
        store.record_filter_result("test_filter", &FilterResult::Reject, Duration::from_millis(3));

        let snap = store.snapshot();
        let f = &snap.filters["test_filter"];
        assert_eq!(f.requests_continue, 2);
        assert_eq!(f.requests_reject, 1);
        assert_eq!(f.errors, 0);
        assert_eq!(f.duration.count, 3);
    }

    #[test]
    fn evaluator_decision_recording() {
        let store = MetricsStore::new();
        store.record_evaluator_decision("eval-1", "pass");
        store.record_evaluator_decision("eval-1", "pass");
        store.record_evaluator_decision("eval-1", "block");
        store.record_evaluator_decision("eval-1", "warn");

        let snap = store.snapshot();
        let e = &snap.evaluators["eval-1"];
        assert_eq!(e.decisions.pass, 2);
        assert_eq!(e.decisions.block, 1);
        assert_eq!(e.decisions.warn, 1);
        assert_eq!(e.decisions.filter_tools, 0);
    }

    #[test]
    fn llm_call_recording() {
        let store = MetricsStore::new();
        store.record_llm_call("eval-1", true, Duration::from_millis(500));
        store.record_llm_call("eval-1", false, Duration::from_millis(100));
        store.record_llm_empty_result("eval-1");

        let snap = store.snapshot();
        let e = &snap.evaluators["eval-1"];
        assert_eq!(e.llm.calls_success, 1);
        assert_eq!(e.llm.calls_failure, 1);
        assert_eq!(e.llm.empty_results, 1);
        assert_eq!(e.llm.duration.count, 2);
    }

    #[test]
    fn schema_and_wasm_recording() {
        let store = MetricsStore::new();
        store.record_schema_validation("eval-1", true);
        store.record_schema_validation("eval-1", false);
        store.record_schema_retry("eval-1", true);
        store.record_wasm_execution("eval-1", Duration::from_millis(5));
        store.record_wasm_not_found("eval-2");

        let snap = store.snapshot();
        let e1 = &snap.evaluators["eval-1"];
        assert_eq!(e1.schema.validations_pass, 1);
        assert_eq!(e1.schema.validations_fail, 1);
        assert_eq!(e1.schema.retries_success, 1);
        assert_eq!(e1.wasm.executions, 1);

        let e2 = &snap.evaluators["eval-2"];
        assert_eq!(e2.wasm.not_found, 1);
    }

    #[test]
    fn pipeline_skip_recording() {
        let store = MetricsStore::new();
        store.record_skip(&SkipReason::MissingMethod);
        store.record_skip(&SkipReason::Unmatched);
        store.record_skip(&SkipReason::Unmatched);
        store.record_trigger_match(true);
        store.record_trigger_match(false);

        let snap = store.snapshot();
        assert_eq!(snap.pipeline.skipped_no_method, 1);
        assert_eq!(snap.pipeline.skipped_no_match, 2);
        assert_eq!(snap.pipeline.trigger_matches, 1);
        assert_eq!(snap.pipeline.trigger_misses, 1);
    }

    #[test]
    fn gauge_values() {
        let store = MetricsStore::new();
        store.set_evaluators_loaded(3);
        store.set_wasm_compiled(2);
        store.set_namespace_bindings(1);

        let snap = store.snapshot();
        assert_eq!(snap.gauges.evaluators_loaded, 3);
        assert_eq!(snap.gauges.wasm_compiled, 2);
        assert_eq!(snap.gauges.namespace_bindings, 1);
    }

    #[test]
    fn duration_avg_calculation() {
        let store = MetricsStore::new();
        store.record_filter_result("f", &FilterResult::Continue, Duration::from_millis(10));
        store.record_filter_result("f", &FilterResult::Continue, Duration::from_millis(20));

        let snap = store.snapshot();
        let d = &snap.filters["f"].duration;
        assert_eq!(d.count, 2);
        assert!((d.avg_ms - 15.0).abs() < 0.1);
    }

    #[test]
    fn empty_snapshot() {
        let store = MetricsStore::new();
        let snap = store.snapshot();
        assert!(snap.filters.is_empty());
        assert!(snap.evaluators.is_empty());
        assert_eq!(snap.gauges.evaluators_loaded, 0);
    }

    #[test]
    fn separate_filters_tracked_independently() {
        let store = MetricsStore::new();
        store.record_filter_result("filter_a", &FilterResult::Continue, Duration::from_millis(1));
        store.record_filter_result("filter_b", &FilterResult::Reject, Duration::from_millis(2));

        let snap = store.snapshot();
        assert_eq!(snap.filters["filter_a"].requests_continue, 1);
        assert_eq!(snap.filters["filter_a"].requests_reject, 0);
        assert_eq!(snap.filters["filter_b"].requests_continue, 0);
        assert_eq!(snap.filters["filter_b"].requests_reject, 1);
    }
}
