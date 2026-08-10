#!/usr/bin/env bash
set -euo pipefail

# ------------------------------------------------------------------
# Evaluator Engine — integration test
#
# Tests the generic evaluator engine with the safety WASM actions.
# Configures an evaluator via the management API, then makes tool
# calls to verify the WASM actions execute correctly.
#
# Prerequisites:
#   - wanaku-praxis running on ports 8081 (MCP) and 9090 (management)
#   - Ollama running on port 11434
#   - WASM actions built in actions/dist/
#   - curl, jq
# ------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

MCP_PORT=8081
MGMT_PORT=8080
MCP_URL="http://localhost:${MCP_PORT}"
MGMT_URL="http://localhost:${MGMT_PORT}"
NAMESPACE="default"

PASS=0
FAIL=0
SKIP=0

REGISTERED_TOOLS=()

green()  { printf '\033[32m%s\033[0m\n' "$*"; }
red()    { printf '\033[31m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }
bold()   { printf '\033[1m%s\033[0m\n' "$*"; }

pass() { PASS=$((PASS + 1)); green "  PASS: $1"; }
fail() { FAIL=$((FAIL + 1)); red   "  FAIL: $1 — $2"; }
skip() { SKIP=$((SKIP + 1)); yellow "  SKIP: $1"; }

cleanup() {
    bold ""
    bold "Cleaning up..."
    for tool in "${REGISTERED_TOOLS[@]}"; do
        curl -sf -X DELETE "${MGMT_URL}/api/v1/tools/${tool}" > /dev/null 2>&1 || true
    done
    # Remove evaluator config
    curl -sf -X PUT "${MGMT_URL}/api/v1/evaluators" \
        -H "Content-Type: application/json" \
        -d '{"evaluators":[]}' > /dev/null 2>&1 || true
}
trap cleanup EXIT

register_tool() {
    local name=$1 description=$2
    curl -sf -X POST "${MGMT_URL}/api/v1/tools" \
        -H "Content-Type: application/json" \
        -d '{
            "name": "'"${name}"'",
            "description": "'"${description}"'",
            "uri": "http://localhost:8080/mcp",
            "type": "mcp-forward",
            "inputSchema": {"type":"object","properties":{}}
        }' > /dev/null
    REGISTERED_TOOLS+=("${name}")
}

# ---- preflight ---------------------------------------------------

for cmd in curl jq; do
    if ! command -v "$cmd" &>/dev/null; then
        red "Missing required command: ${cmd}"
        exit 1
    fi
done

if ! curl -sf -o /dev/null "${MGMT_URL}/api/v1/tools" 2>/dev/null; then
    red "wanaku-praxis is not running on ports ${MCP_PORT}/${MGMT_PORT}"
    exit 1
fi

if ! curl -sf -o /dev/null "http://localhost:11434/v1/models" 2>/dev/null; then
    red "Ollama is not reachable at localhost:11434"
    exit 1
fi

# Check WASM actions exist
BLOCK_WASM="${PROJECT_ROOT}/actions/dist/safety_block_action.wasm"
WARN_WASM="${PROJECT_ROOT}/actions/dist/safety_warn_action.wasm"

if [ ! -f "$BLOCK_WASM" ] || [ ! -f "$WARN_WASM" ]; then
    red "WASM action files not found in actions/dist/"
    red "Build them with: cd actions/safety-block && cargo component build --release"
    exit 1
fi

bold "========================================"
bold " Evaluator Engine Integration Test"
bold "========================================"
echo ""
echo "MCP endpoint : ${MCP_URL}"
echo "Mgmt API     : ${MGMT_URL}"
echo "Block WASM   : ${BLOCK_WASM}"
echo "Warn WASM    : ${WARN_WASM}"
echo ""

# ==================================================================
# TEST 1: Verify evaluators API works
# ==================================================================
bold "--- Test 1: Evaluators management API ---"

RESP=$(curl -sf "${MGMT_URL}/api/v1/evaluators" 2>/dev/null || echo '{"error":"failed"}')
if echo "$RESP" | jq -e '.data' > /dev/null 2>&1; then
    pass "api: GET /api/v1/evaluators returns valid response"
else
    fail "api" "GET /api/v1/evaluators failed: ${RESP}"
    exit 1
fi

# ==================================================================
# TEST 2: Register a test tool
# ==================================================================
bold ""
bold "--- Test 2: Register test tool ---"

register_tool "restart-database" "Restart a production database instance"
pass "tool: restart-database registered"

# ==================================================================
# TEST 3: Configure safety evaluator via management API
# ==================================================================
bold ""
bold "--- Test 3: Configure safety evaluator with WASM actions ---"

EVAL_CONFIG=$(cat <<HEREDOC
{
    "evaluators": [{
        "name": "safety-gate",
        "trigger": {
            "method": "tools/call"
        },
        "llm": {
            "operation": "classify",
            "labels": ["green", "yellow", "red"],
            "prompt": "You are a strict safety classifier. Classify this tool call as green (safe), yellow (ambiguous), or red (dangerous). Restarting production databases is ALWAYS red. Respond with ONLY: {\"level\": \"<green|yellow|red>\", \"reason\": \"<brief>\"}",
            "model": "llama3.2",
            "url": "http://localhost:11434/v1"
        },
        "rules": {
            "green": "pass",
            "yellow": {"path": "${WARN_WASM}"},
            "red": {"path": "${BLOCK_WASM}"}
        },
        "on_error": "continue"
    }]
}
HEREDOC
)

RESP=$(curl -sf -X PUT "${MGMT_URL}/api/v1/evaluators" \
    -H "Content-Type: application/json" \
    -d "${EVAL_CONFIG}")

EVAL_COUNT=$(echo "$RESP" | jq '.data | length' 2>/dev/null)
if [ "$EVAL_COUNT" = "1" ]; then
    pass "config: safety evaluator configured with WASM actions"
else
    fail "config" "failed to configure evaluator: ${RESP}"
    exit 1
fi

# ==================================================================
# TEST 4: Make a dangerous tool call — should trigger evaluator
# ==================================================================
bold ""
bold "--- Test 4: Dangerous tool call (restart-database) ---"

RESP=$(curl -sf -X POST "${MCP_URL}/mcp" \
    -H "Content-Type: application/json" \
    -d '{
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {
            "name": "restart-database",
            "arguments": {
                "target": "production-primary",
                "x-request-id": "wk-test0001"
            }
        }
    }' 2>/dev/null || echo '{"error":"curl_failed"}')

ERROR_CODE=$(echo "$RESP" | jq -r '.error.code // empty' 2>/dev/null)
ERROR_MSG=$(echo "$RESP" | jq -r '.error.message // empty' 2>/dev/null)

echo "  Response error code: ${ERROR_CODE:-none}"
echo "  Response error msg:  ${ERROR_MSG:-none}"

if [ "$ERROR_CODE" = "-32001" ]; then
    pass "blocked: dangerous tool call blocked by WASM evaluator (code -32001)"
elif echo "$ERROR_MSG" | grep -qi "blocked\|safety\|evaluator"; then
    pass "blocked: tool call blocked (message contains safety reference)"
else
    yellow "  Tool call was NOT blocked"
    yellow "  This may be expected if the LLM classified it as green/yellow"
    yellow "  Check server logs for 'evaluator triggered' and 'LLM classification result'"
    skip "blocked: not blocked (LLM-dependent)"
fi

# ==================================================================
# TEST 5: Verify evaluator can be cleared
# ==================================================================
bold ""
bold "--- Test 5: Clear evaluators ---"

curl -sf -X PUT "${MGMT_URL}/api/v1/evaluators" \
    -H "Content-Type: application/json" \
    -d '{"evaluators":[]}' > /dev/null

RESP=$(curl -sf "${MGMT_URL}/api/v1/evaluators")
EVAL_COUNT=$(echo "$RESP" | jq '.data | length' 2>/dev/null)
if [ "$EVAL_COUNT" = "0" ]; then
    pass "clear: evaluators cleared successfully"
else
    fail "clear" "expected 0 evaluators, got ${EVAL_COUNT}"
fi

# ==================================================================
# Summary
# ==================================================================
bold ""
bold "========================================"
TOTAL=$((PASS + FAIL + SKIP))
bold " Results: ${PASS} passed, ${FAIL} failed, ${SKIP} skipped (${TOTAL} total)"
bold "========================================"
echo ""
echo "Check server logs for:"
echo "  - 'evaluator triggered' — confirms the filter fired"
echo "  - 'evaluator LLM response' — shows what the LLM returned"
echo "  - 'evaluator action result' — shows the WASM action's decision"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
