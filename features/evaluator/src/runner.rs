//! Bounded production processor execution.
//!
//! The runner moves synchronous Wasmtime execution off the async request task
//! onto the Tokio blocking pool. A semaphore bounds concurrent invocations. The
//! admission permit moves into the blocking closure, so the permit stays held
//! until the blocking work exits, also when the request future is dropped.

use std::sync::Arc;

use tokio::sync::Semaphore;

use crate::engine::{
    CompiledEvaluator, EvaluationResult, EvaluatorExecutionError, ExecutionContext, ProcessorLimits,
};
use crate::host::types::EvaluationContext;

pub const CONCURRENCY_ENV: &str = "WANAKU_EVALUATOR_PROCESSOR_CONCURRENCY";
pub const TIMEOUT_MS_ENV: &str = "WANAKU_EVALUATOR_PROCESSOR_TIMEOUT_MS";
pub const MEMORY_BYTES_ENV: &str = "WANAKU_EVALUATOR_PROCESSOR_MEMORY_BYTES";
pub const FUEL_ENV: &str = "WANAKU_EVALUATOR_PROCESSOR_FUEL";

/// Runs production processors with admission control and resource limits.
#[derive(Debug, Clone)]
pub struct ProcessorRunner {
    permits: Arc<Semaphore>,
    max_concurrent: usize,
    limits: ProcessorLimits,
}

impl Default for ProcessorRunner {
    fn default() -> Self {
        Self::new(default_concurrency(), ProcessorLimits::default())
    }
}

impl ProcessorRunner {
    #[must_use]
    pub fn new(max_concurrent: usize, limits: ProcessorLimits) -> Self {
        Self {
            permits: Arc::new(Semaphore::new(max_concurrent)),
            max_concurrent,
            limits,
        }
    }

    /// Build a runner from the `WANAKU_EVALUATOR_PROCESSOR_*` variables.
    /// A missing, invalid, or zero value keeps the default.
    #[must_use]
    pub fn from_env() -> Self {
        Self::from_env_with(|name| std::env::var(name).ok())
    }

    fn from_env_with(env: impl Fn(&str) -> Option<String>) -> Self {
        let defaults = ProcessorLimits::default();
        let limits = ProcessorLimits {
            fuel: env_value(&env, FUEL_ENV).unwrap_or(defaults.fuel),
            memory_bytes: env_value(&env, MEMORY_BYTES_ENV).unwrap_or(defaults.memory_bytes),
            timeout_ms: env_value(&env, TIMEOUT_MS_ENV).unwrap_or(defaults.timeout_ms),
        };
        let max_concurrent = env_value(&env, CONCURRENCY_ENV).unwrap_or_else(default_concurrency);
        tracing::info!(
            max_concurrent,
            fuel = limits.fuel,
            memory_bytes = limits.memory_bytes,
            timeout_ms = limits.timeout_ms,
            "evaluator processor runner configured"
        );
        Self::new(max_concurrent, limits)
    }

    #[must_use]
    pub const fn limits(&self) -> ProcessorLimits {
        self.limits
    }

    #[must_use]
    pub const fn max_concurrent(&self) -> usize {
        self.max_concurrent
    }

    /// Invocations that hold an admission permit now.
    #[must_use]
    pub fn in_flight(&self) -> usize {
        self.max_concurrent
            .saturating_sub(self.permits.available_permits())
    }

    /// Run one processor invocation on the blocking pool.
    ///
    /// Returns a governance reason code on failure. The runner rejects the
    /// invocation with `processor_overloaded` when all permits are in use.
    pub async fn run(
        &self,
        processor: Arc<CompiledEvaluator>,
        mut execution: ExecutionContext,
        ctx: EvaluationContext,
    ) -> Result<EvaluationResult, &'static str> {
        let permit = self
            .permits
            .clone()
            .try_acquire_owned()
            .map_err(|_| "processor_overloaded")?;
        let runtime =
            tokio::runtime::Handle::try_current().map_err(|_| "processor_execution_failed")?;
        execution.limits = Some(self.limits);
        let work = runtime.spawn_blocking(move || {
            let result = processor.evaluate(execution, ctx);
            drop(permit);
            result
        });
        match work.await {
            Ok(Ok(result)) => Ok(result),
            Ok(Err(EvaluatorExecutionError::Instantiation)) => {
                Err("processor_instantiation_failed")
            }
            Ok(Err(EvaluatorExecutionError::Execution)) | Err(_) => {
                Err("processor_execution_failed")
            }
        }
    }
}

fn default_concurrency() -> usize {
    std::thread::available_parallelism().map_or(1, std::num::NonZeroUsize::get)
}

fn env_value<T: std::str::FromStr + PartialEq + Default>(
    env: &impl Fn(&str) -> Option<String>,
    name: &str,
) -> Option<T> {
    let raw = env(name)?;
    match raw.trim().parse::<T>() {
        Ok(value) if value != T::default() => Some(value),
        _ => {
            tracing::warn!(variable = name, value = %raw, "invalid evaluator processor limit, using default");
            None
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::Path;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::time::{Duration, Instant};
    use wanaku_infra::registry::InMemoryRegistry;
    use wanaku_types::interactions::InMemoryInteractionStore;

    use crate::action::ActionResult;
    use crate::engine::ExecutionMode;

    fn fixture(name: &str) -> Arc<CompiledEvaluator> {
        let path = Path::new(env!("CARGO_MANIFEST_DIR")).join(format!("tests/fixtures/{name}.wat"));
        Arc::new(CompiledEvaluator::from_file(name, &path).unwrap())
    }

    fn execution() -> ExecutionContext {
        ExecutionContext {
            registry: InMemoryRegistry::new(),
            interactions: InMemoryInteractionStore::new(1),
            compiled_schema: None,
            allow_side_effects: true,
            mode: ExecutionMode::Production,
            limits: None,
        }
    }

    fn context() -> EvaluationContext {
        EvaluationContext {
            method: "tools/call".to_owned(),
            namespace: "default".to_owned(),
            tool_name: Some("test".to_owned()),
            arguments: Vec::new(),
            llm_result: "{}".to_owned(),
            conversation_id: None,
        }
    }

    /// Fuel stays far above what the timeout allows, so only the wall-clock
    /// deadline stops the looping fixture.
    fn timed_runner(max_concurrent: usize, timeout_ms: u64) -> ProcessorRunner {
        ProcessorRunner::new(
            max_concurrent,
            ProcessorLimits {
                fuel: u64::MAX / 4,
                timeout_ms,
                ..ProcessorLimits::default()
            },
        )
    }

    async fn wait_for(runner: &ProcessorRunner, in_flight: usize) {
        let deadline = Instant::now() + Duration::from_secs(10);
        while runner.in_flight() != in_flight {
            assert!(
                Instant::now() < deadline,
                "in-flight count did not reach {in_flight}"
            );
            tokio::time::sleep(Duration::from_millis(5)).await;
        }
    }

    async fn assert_pass(runner: &ProcessorRunner) {
        let result = runner
            .run(fixture("pass"), execution(), context())
            .await
            .unwrap();
        assert!(matches!(result.action, ActionResult::Pass));
    }

    #[tokio::test(flavor = "current_thread")]
    async fn unrelated_task_progresses_on_single_worker_runtime() {
        let runner = timed_runner(1, 300);
        let ticks = Arc::new(AtomicUsize::new(0));
        let counter = ticks.clone();
        let ticker = tokio::spawn(async move {
            loop {
                counter.fetch_add(1, Ordering::Relaxed);
                tokio::time::sleep(Duration::from_millis(5)).await;
            }
        });
        let result = runner
            .run(fixture("fuel-exhaustion"), execution(), context())
            .await;
        ticker.abort();
        assert!(matches!(result, Err("processor_execution_failed")));
        assert!(
            ticks.load(Ordering::Relaxed) > 5,
            "the async task must run while the processor executes"
        );
    }

    #[tokio::test(flavor = "current_thread")]
    async fn looping_fixture_stops_at_wall_clock_budget() {
        let runner = timed_runner(1, 200);
        let started = Instant::now();
        let result = runner
            .run(fixture("fuel-exhaustion"), execution(), context())
            .await;
        assert!(matches!(result, Err("processor_execution_failed")));
        assert!(started.elapsed() >= Duration::from_millis(200));
        assert!(started.elapsed() < Duration::from_secs(5));
        assert_eq!(runner.in_flight(), 0);
        assert_pass(&runner).await;
    }

    #[tokio::test(flavor = "current_thread")]
    async fn looping_fixture_stops_at_fuel_budget() {
        let runner = ProcessorRunner::new(
            1,
            ProcessorLimits {
                fuel: 1_000_000,
                timeout_ms: 60_000,
                ..ProcessorLimits::default()
            },
        );
        let started = Instant::now();
        let result = runner
            .run(fixture("fuel-exhaustion"), execution(), context())
            .await;
        assert!(matches!(result, Err("processor_execution_failed")));
        assert!(started.elapsed() < Duration::from_secs(5));
        assert_pass(&runner).await;
    }

    #[tokio::test(flavor = "current_thread")]
    async fn memory_growth_fixture_stops_at_memory_budget() {
        let runner = ProcessorRunner::new(
            1,
            ProcessorLimits {
                fuel: u64::MAX / 4,
                memory_bytes: 2 * 1024 * 1024,
                timeout_ms: 60_000,
            },
        );
        let started = Instant::now();
        let result = runner
            .run(fixture("memory-growth"), execution(), context())
            .await;
        assert!(matches!(result, Err("processor_execution_failed")));
        assert!(started.elapsed() < Duration::from_secs(5));
        assert_pass(&runner).await;
    }

    #[tokio::test(flavor = "current_thread")]
    async fn concurrency_stays_bounded_after_cancellation() {
        let runner = timed_runner(2, 500);
        let requests: Vec<_> = (0..2)
            .map(|_| {
                let runner = runner.clone();
                tokio::spawn(async move {
                    runner
                        .run(fixture("fuel-exhaustion"), execution(), context())
                        .await
                })
            })
            .collect();
        wait_for(&runner, 2).await;
        assert!(matches!(
            runner.run(fixture("pass"), execution(), context()).await,
            Err("processor_overloaded")
        ));
        for request in &requests {
            request.abort();
        }
        for request in requests {
            assert!(request.await.is_err_and(|error| error.is_cancelled()));
        }
        // Cancelled requests do not release permits before the work exits.
        assert_eq!(runner.in_flight(), 2);
        assert!(matches!(
            runner.run(fixture("pass"), execution(), context()).await,
            Err("processor_overloaded")
        ));
        wait_for(&runner, 0).await;
        assert_pass(&runner).await;
    }

    #[tokio::test(flavor = "current_thread")]
    async fn request_timeout_keeps_permit_until_work_exits() {
        let runner = timed_runner(1, 300);
        let timed_out = tokio::time::timeout(
            Duration::from_millis(20),
            runner.run(fixture("fuel-exhaustion"), execution(), context()),
        )
        .await;
        assert!(timed_out.is_err());
        assert_eq!(runner.in_flight(), 1);
        wait_for(&runner, 0).await;
        assert_pass(&runner).await;
    }

    #[test]
    fn environment_overrides_limits_and_rejects_invalid_values() {
        let runner = ProcessorRunner::from_env_with(|name| match name {
            CONCURRENCY_ENV => Some("3".to_owned()),
            TIMEOUT_MS_ENV => Some("250".to_owned()),
            MEMORY_BYTES_ENV => Some("0".to_owned()),
            FUEL_ENV => Some("many".to_owned()),
            _ => None,
        });
        let defaults = ProcessorLimits::default();
        assert_eq!(runner.max_concurrent(), 3);
        assert_eq!(runner.limits().timeout_ms, 250);
        assert_eq!(runner.limits().memory_bytes, defaults.memory_bytes);
        assert_eq!(runner.limits().fuel, defaults.fuel);
        assert_eq!(
            ProcessorRunner::from_env_with(|_| None).max_concurrent(),
            default_concurrency()
        );
    }
}
