#!/usr/bin/env bash
#
# Gracefully stops a Wanaku local stack started with "wanaku start local".
#
# Usage:
#   ./tests/wanaku-start-local-stop.sh
#   WANAKU_PID=12345 ./tests/wanaku-start-local-stop.sh
#

set -euo pipefail

TERM_TIMEOUT=${TERM_TIMEOUT:-10}
RETRY_TIMEOUT=${RETRY_TIMEOUT:-5}

find_cli_pid() {
    if [ -n "${WANAKU_PID:-}" ]; then
        if kill -0 "${WANAKU_PID}" 2>/dev/null; then
            echo "${WANAKU_PID}"
            return
        fi
        echo "WARNING: WANAKU_PID=${WANAKU_PID} is not running, searching..." >&2
    fi

    local pids
    pids=$(pgrep -f "quarkus-run.jar start local" 2>/dev/null || true)
    if [ -z "$pids" ]; then
        return
    fi
    echo "$pids"
}

find_children() {
    pgrep -P "$1" 2>/dev/null || true
}

collect_all_pids() {
    local cli_pids="$1"
    local all_pids="$cli_pids"

    for pid in $cli_pids; do
        local children
        children=$(find_children "$pid")
        if [ -n "$children" ]; then
            all_pids="$all_pids $children"
        fi
    done
    echo "$all_pids"
}

any_alive() {
    for pid in $1; do
        if kill -0 "$pid" 2>/dev/null; then
            return 0
        fi
    done
    return 1
}

wait_for_exit() {
    local pids="$1"
    local timeout="$2"
    local elapsed=0

    while [ "$elapsed" -lt "$timeout" ]; do
        if ! any_alive "$pids"; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    return 1
}

main() {
    local cli_pids
    cli_pids=$(find_cli_pid)

    if [ -z "$cli_pids" ]; then
        echo "No wanaku start local process found."
        exit 0
    fi

    local all_pids
    all_pids=$(collect_all_pids "$cli_pids")

    echo "Found wanaku processes:"
    for pid in $all_pids; do
        local cmd
        cmd=$(ps -p "$pid" -o args= 2>/dev/null || echo "(exited)")
        echo "  PID $pid: $cmd"
    done

    # Phase 1: SIGTERM the CLI parent(s) — shutdown hooks propagate to children
    echo "Sending SIGTERM to CLI process(es)..."
    for pid in $cli_pids; do
        kill "$pid" 2>/dev/null || true
    done

    if wait_for_exit "$all_pids" "$TERM_TIMEOUT"; then
        echo "All processes stopped gracefully."
        exit 0
    fi

    # Phase 2: SIGTERM any remaining processes individually
    echo "Some processes still running after ${TERM_TIMEOUT}s, sending SIGTERM to remaining..."
    for pid in $all_pids; do
        if kill -0 "$pid" 2>/dev/null; then
            echo "  SIGTERM -> PID $pid"
            kill "$pid" 2>/dev/null || true
        fi
    done

    if wait_for_exit "$all_pids" "$RETRY_TIMEOUT"; then
        echo "All processes stopped."
        exit 0
    fi

    # Phase 3: SIGKILL as last resort
    for pid in $all_pids; do
        if kill -0 "$pid" 2>/dev/null; then
            echo "WARNING: PID $pid did not respond to SIGTERM, sending SIGKILL..."
            kill -9 "$pid" 2>/dev/null || true
        fi
    done

    echo "Stop complete."
}

main
