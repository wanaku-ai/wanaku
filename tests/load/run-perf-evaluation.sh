#!/bin/bash
set -euo pipefail

# =============================================================================
# Wanaku Performance Evaluation Script
#
# Generates baselines from CI (main branch), builds current branch, runs
# k6 load tests for both tools and resources, monitors system resources,
# and produces a comparison report.
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WANAKU_VERSION="0.1.0"
CI_BASE="http://integration-ci.usersys.redhat.com:8080/view/Wanaku/job/wanaku-automated-builds/job"
BRANCH="${BASELINE_BRANCH:-main}"
EVAL_DIR="${EVAL_DIR:-$HOME/perf-evaluation-$(date +%Y%m%d-%H%M%S)}"
K6_BIN="${K6_BIN:-$HOME/bin/k6}"
WANAKU_BIN="${WANAKU_BIN:-$HOME/bin/wanaku}"
JAVA_OPTS="${JAVA_OPTS:--XX:+UseNUMA -Xmx4G -Xms4G}"

# What to run: "all", "tools", or "resources"
TEST_SCOPE="${TEST_SCOPE:-all}"
# Whether to skip baseline or patched run
SKIP_BASELINE="${SKIP_BASELINE:-false}"
SKIP_PATCHED="${SKIP_PATCHED:-false}"
# Whether to skip building
SKIP_BUILD="${SKIP_BUILD:-false}"

# System monitoring interval (seconds)
MONITOR_INTERVAL=2

mkdir -p "$EVAL_DIR"

echo "=============================================="
echo "  Wanaku Performance Evaluation"
echo "=============================================="
echo "Evaluation directory: $EVAL_DIR"
echo "Baseline branch:      $BRANCH"
echo "Test scope:            $TEST_SCOPE"
echo "Project root:          $PROJECT_ROOT"
echo "=============================================="

# ---- CI artifact URLs ----
CI_ROUTER="${CI_BASE}/${BRANCH}/lastBuild/artifact/wanaku/apps/wanaku-router-backend/target/distributions/wanaku-router-backend-${WANAKU_VERSION}.tar.gz"
CI_TOOL_NOOP="${CI_BASE}/${BRANCH}/lastBuild/artifact/wanaku/capabilities/tools/wanaku-tool-performance-noop/target/distributions/wanaku-tool-performance-noop-${WANAKU_VERSION}.tar.gz"
CI_PROVIDER_STATIC="${CI_BASE}/${BRANCH}/lastBuild/artifact/wanaku/capabilities/providers/wanaku-provider-performance-static-file/target/distributions/wanaku-provider-performance-static-file-${WANAKU_VERSION}.tar.gz"

# ---- Local artifact paths ----
LOCAL_ROUTER="${PROJECT_ROOT}/apps/wanaku-router-backend/target/distributions/wanaku-router-backend-${WANAKU_VERSION}.tar.gz"
LOCAL_TOOL_NOOP="${PROJECT_ROOT}/capabilities/tools/wanaku-tool-performance-noop/target/distributions/wanaku-tool-performance-noop-${WANAKU_VERSION}.tar.gz"
LOCAL_PROVIDER_STATIC="${PROJECT_ROOT}/capabilities/providers/wanaku-provider-performance-static-file/target/distributions/wanaku-provider-performance-static-file-${WANAKU_VERSION}.tar.gz"

# ---- System resource monitoring ----
MONITOR_PIDS=()

start_system_monitor() {
    local label="$1"
    local output_dir="$2"
    mkdir -p "$output_dir"

    # vmstat monitoring
    vmstat "$MONITOR_INTERVAL" > "$output_dir/vmstat-${label}.log" 2>&1 &
    MONITOR_PIDS+=($!)

    # Per-process memory/CPU via top (batch mode)
    (while true; do
        echo "=== $(date '+%Y-%m-%d %H:%M:%S') ===" >> "$output_dir/top-${label}.log"
        top -bn1 -o '%MEM' 2>/dev/null | head -20 >> "$output_dir/top-${label}.log"
        sleep "$MONITOR_INTERVAL"
    done) &
    MONITOR_PIDS+=($!)

    # JVM-specific: track java processes
    (while true; do
        echo "=== $(date '+%Y-%m-%d %H:%M:%S') ===" >> "$output_dir/java-procs-${label}.log"
        ps aux 2>/dev/null | grep '[j]ava' | awk '{printf "PID=%s CPU=%.1f%% MEM=%.1f%% RSS=%sKB CMD=%s\n", $2, $3, $4, $6, $11}' >> "$output_dir/java-procs-${label}.log"
        sleep "$MONITOR_INTERVAL"
    done) &
    MONITOR_PIDS+=($!)

    echo "System monitoring started (label=$label)"
}

stop_system_monitor() {
    for pid in "${MONITOR_PIDS[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
    wait "${MONITOR_PIDS[@]}" 2>/dev/null || true
    MONITOR_PIDS=()
    echo "System monitoring stopped."
}

# ---- Build current branch ----
build_current_branch() {
    if [ "$SKIP_BUILD" = "true" ]; then
        echo "=== Skipping build (SKIP_BUILD=true) ==="
        return
    fi

    echo "=== Building current branch ($(git -C "$PROJECT_ROOT" branch --show-current)) ==="
    (cd "$PROJECT_ROOT" && mvn package -Pdist -DskipTests -T1C -q)
    echo "Build complete."

    # Verify artifacts exist
    for f in "$LOCAL_ROUTER" "$LOCAL_TOOL_NOOP" "$LOCAL_PROVIDER_STATIC"; do
        if [ ! -f "$f" ]; then
            echo "Error: expected artifact not found: $f" >&2
            exit 1
        fi
    done
    echo "All local artifacts verified."
}

# ---- Run a test suite ----
# Args: $1=label (baseline|patched), $2=router_source, $3=capability_source, $4=suite_name
run_test_suite() {
    local label="$1"
    local router_src="$2"
    local cap_src="$3"
    local suite_name="$4"
    local result_dir="$EVAL_DIR/${label}"

    echo ""
    echo "======================================================"
    echo "  Running: ${label} / ${suite_name}"
    echo "======================================================"

    start_system_monitor "${label}-${suite_name}" "$result_dir"

    "$SCRIPT_DIR/run-perf-test.sh" \
        --router-from "$router_src" \
        --capability-from "$cap_src" \
        --suite "$suite_name" \
        --test-name "$label" \
        --test-base-dir "$EVAL_DIR"

    stop_system_monitor

    echo "Results saved to: $result_dir"
}

# ---- Step 1: Build current branch ----
build_current_branch

# ---- Step 2: Run baseline tests (from CI / main branch) ----
if [ "$SKIP_BASELINE" != "true" ]; then
    echo ""
    echo "########################################"
    echo "# BASELINE (${BRANCH} branch from CI)"
    echo "########################################"

    if [ "$TEST_SCOPE" = "all" ] || [ "$TEST_SCOPE" = "tools" ]; then
        run_test_suite "baseline" "$CI_ROUTER" "$CI_TOOL_NOOP" "tools-sse"
    fi

    if [ "$TEST_SCOPE" = "all" ] || [ "$TEST_SCOPE" = "resources" ]; then
        run_test_suite "baseline" "$CI_ROUTER" "$CI_PROVIDER_STATIC" "resources-sse"
    fi
else
    echo "=== Skipping baseline (SKIP_BASELINE=true) ==="
fi

# ---- Step 3: Run patched tests (from local build) ----
if [ "$SKIP_PATCHED" != "true" ]; then
    echo ""
    echo "########################################"
    echo "# PATCHED (current branch: $(git -C "$PROJECT_ROOT" branch --show-current))"
    echo "########################################"

    if [ "$TEST_SCOPE" = "all" ] || [ "$TEST_SCOPE" = "tools" ]; then
        run_test_suite "patched" "$LOCAL_ROUTER" "$LOCAL_TOOL_NOOP" "tools-sse"
    fi

    if [ "$TEST_SCOPE" = "all" ] || [ "$TEST_SCOPE" = "resources" ]; then
        run_test_suite "patched" "$LOCAL_ROUTER" "$LOCAL_PROVIDER_STATIC" "resources-sse"
    fi
else
    echo "=== Skipping patched (SKIP_PATCHED=true) ==="
fi

# ---- Step 4: Generate comparison report ----
echo ""
echo "########################################"
echo "# Generating comparison report"
echo "########################################"

python3 "$SCRIPT_DIR/generate-perf-report.py" \
    --eval-dir "$EVAL_DIR" \
    --test-scope "$TEST_SCOPE"

echo ""
echo "=============================================="
echo "  Evaluation complete!"
echo "  Results: $EVAL_DIR"
echo "  Report:  $EVAL_DIR/perf-report.md"
echo "=============================================="
