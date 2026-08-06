#!/usr/bin/env bash
set -euo pipefail

# ------------------------------------------------------------------
# Safety Classification Filter — integration test script
#
# Assumes wanaku-praxis is already running (e.g. from an IDE).
# Start the server with the desired WANAKU_SAFETY_* env vars, then
# run this script to exercise the safety filter.
#
# Prerequisites:
#   - wanaku-praxis running on ports 8081 (MCP) and 9090 (management)
#   - An OpenAI-compatible LLM endpoint (for classifier tests)
#   - curl, jq
#
# Usage:
#   ./scripts/test-safety.sh                          # Ollama on localhost
#   ./scripts/test-safety.sh http://myhost:11434/v1   # custom endpoint
#   LLM_API_KEY=sk-... ./scripts/test-safety.sh https://api.openai.com/v1
#
# Environment:
#   MCP_FORWARD_URL  MCP server to forward tool calls to (default http://localhost:8080/)
#   LLM_MODEL        Model name for the classifier (default llama3.2)
#   LLM_API_KEY      Bearer token for cloud LLM providers
# ------------------------------------------------------------------

LLM_URL="${1:-http://localhost:11434/v1}"
LLM_MODEL="${LLM_MODEL:-llama3.2}"
LLM_API_KEY="${LLM_API_KEY:-}"

NAMESPACE="test-ns"
MCP_FORWARD_URL="${MCP_FORWARD_URL:-http://localhost:8080/mcp}"
MCP_PORT=8081
MGMT_PORT=9090
MCP_URL="http://localhost:${MCP_PORT}"
MGMT_URL="http://localhost:${MGMT_PORT}"

PASS=0
FAIL=0
SKIP=0

# tools registered by this script, cleaned up on exit
REGISTERED_TOOLS=()

# ---- helpers ----------------------------------------------------

green()  { printf '\033[32m%s\033[0m\n' "$*"; }
red()    { printf '\033[31m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }
bold()   { printf '\033[1m%s\033[0m\n' "$*"; }

pass() { PASS=$((PASS + 1)); green "  PASS: $1"; }
fail() { FAIL=$((FAIL + 1)); red   "  FAIL: $1 — $2"; }
skip() { SKIP=$((SKIP + 1)); yellow "  SKIP: $1"; }

cleanup() {
    bold ""
    bold "Cleaning up registered tools..."
    for tool in "${REGISTERED_TOOLS[@]}"; do
        curl -sf -X DELETE "${MGMT_URL}/api/v1/tools/${tool}" > /dev/null 2>&1 || true
    done
}
trap cleanup EXIT

call_tool() {
    local tool_name=$1 args=$2 id=${3:-1}
    curl -sf -X POST "${MCP_URL}/${NAMESPACE}/mcp" \
        -H "Content-Type: application/json" \
        -d "{
            \"jsonrpc\": \"2.0\",
            \"method\": \"tools/call\",
            \"id\": ${id},
            \"params\": {
                \"name\": \"${tool_name}\",
                \"arguments\": ${args}
            }
        }" 2>/dev/null || echo '{"error":"curl_failed"}'
}

get_error_code() {
    echo "$1" | jq -r '.error.code // empty' 2>/dev/null
}

register_tool() {
    local name=$1 description=$2 schema=$3 skip_safety=${4:-false}
    curl -sf -X POST "${MGMT_URL}/api/v1/tools" \
        -H "Content-Type: application/json" \
        -d '{
            "name": "'"${name}"'",
            "description": "'"${description}"'",
            "uri": "'"${MCP_FORWARD_URL}"'",
            "type": "mcp-forward",
            "namespace": "'"${NAMESPACE}"'",
            "skipSafetyCheck": '"${skip_safety}"',
            "inputSchema": '"${schema}"'
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

# Check server is running
if ! curl -sf -o /dev/null "${MGMT_URL}/api/v1/tools" 2>/dev/null; then
    red "wanaku-praxis is not running on ports ${MCP_PORT}/${MGMT_PORT}"
    red "Start the server first, then re-run this script."
    red ""
    red "  Example (safety disabled):"
    red "    cargo run"
    red ""
    red "  Example (safety enabled, block mode):"
    red "    WANAKU_SAFETY_LLM_URL=http://localhost:11434/v1 \\"
    red "    WANAKU_SAFETY_LLM_MODEL=llama3.2 \\"
    red "    WANAKU_SAFETY_RED_ACTION=block \\"
    red "    WANAKU_SAFETY_YELLOW_ACTION=block \\"
    red "    cargo run"
    exit 1
fi

bold "========================================"
bold " Safety Classification Filter Tests"
bold "========================================"
echo ""
echo "MCP endpoint : ${MCP_URL}"
echo "Mgmt API     : ${MGMT_URL}"
echo "Forward URL  : ${MCP_FORWARD_URL}"
echo "Namespace    : ${NAMESPACE}"
echo "LLM endpoint : ${LLM_URL}"
echo "LLM model    : ${LLM_MODEL}"
echo ""

# ---- register tools ----------------------------------------------

RESTART_SCHEMA='{"type":"object","properties":{"service":{"type":"string","description":"Name of the service to restart"},"server":{"type":"string","description":"Target server hostname"}}}'
SCALE_SCHEMA='{"type":"object","properties":{"deployment":{"type":"string","description":"Deployment name"},"replicas":{"type":"integer","description":"Desired replica count"}}}'
TICKET_SCHEMA='{"type":"object","properties":{"title":{"type":"string","description":"Ticket title"},"severity":{"type":"string","description":"Severity level"},"details":{"type":"string","description":"Incident details"}}}'

bold "Registering test tools..."
register_tool "restartService"  "Restart a service on a specific server"                   "$RESTART_SCHEMA"
register_tool "scaleDeployment" "Scale a deployment to a given number of replicas"          "$SCALE_SCHEMA"
register_tool "escalateTicket"  "Escalate an incident by creating a support ticket"         "$TICKET_SCHEMA" true
green "Registered: restartService, scaleDeployment, escalateTicket (skip_safety)"
echo ""

# ==================================================================
# TEST 1: Benign scaleDeployment — should never be blocked
# ==================================================================
bold "--- Test 1: Benign scaleDeployment (web-frontend, 3 replicas) ---"

RESP=$(call_tool "scaleDeployment" '{"deployment":"web-frontend","replicas":"3","x-request-id":"wk-test0001"}')
CODE=$(get_error_code "$RESP")

if [ "$CODE" = "-32001" ]; then
    fail "benign-scale" "benign scaleDeployment was blocked by safety"
else
    pass "benign-scale: scaleDeployment(web-frontend, 3) not blocked"
fi

# ==================================================================
# TEST 2: Dangerous restartService (production DB)
# ==================================================================
bold ""
bold "--- Test 2: Dangerous restartService (production-database-primary) ---"

RESP=$(call_tool "restartService" '{"service":"production-database-primary","server":"db-master-01","x-request-id":"wk-test0002"}' 2)
CODE=$(get_error_code "$RESP")

if [ "$CODE" = "-32001" ]; then
    pass "dangerous-restart: restartService(production-database-primary) BLOCKED by safety"
else
    yellow "  restartService(production-database-primary) was NOT blocked (code=${CODE:-none})"
    yellow "  This is expected if safety is disabled or red_action=log"
    skip "dangerous-restart: not blocked (depends on server config)"
fi

# ==================================================================
# TEST 3: Dangerous scaleDeployment (payment-service to 0 replicas)
# ==================================================================
bold ""
bold "--- Test 3: Dangerous scaleDeployment (payment-service, 0 replicas) ---"

RESP=$(call_tool "scaleDeployment" '{"deployment":"payment-service","replicas":"0","x-request-id":"wk-test0003"}' 3)
CODE=$(get_error_code "$RESP")

if [ "$CODE" = "-32001" ]; then
    pass "dangerous-scale-zero: scaleDeployment(payment-service, 0) BLOCKED by safety"
else
    yellow "  scaleDeployment(payment-service, 0) was NOT blocked (code=${CODE:-none})"
    yellow "  This is expected if safety is disabled or red_action=log"
    skip "dangerous-scale-zero: not blocked (depends on server config)"
fi

# ==================================================================
# TEST 4: Per-tool opt-out (escalateTicket with skipSafetyCheck)
# ==================================================================
bold ""
bold "--- Test 4: Per-tool opt-out (escalateTicket, skipSafetyCheck=true) ---"

RESP=$(call_tool "escalateTicket" '{"title":"CRITICAL: full database wipe in progress","severity":"P0","details":"All production data is being deleted","x-request-id":"wk-test0004"}' 4)
CODE=$(get_error_code "$RESP")

if [ "$CODE" = "-32001" ]; then
    fail "opt-out" "escalateTicket with skipSafetyCheck was blocked by safety"
else
    pass "opt-out: escalateTicket bypasses classifier via skipSafetyCheck"
fi

# ==================================================================
# TEST 5: Benign escalateTicket (opt-out, normal content)
# ==================================================================
bold ""
bold "--- Test 5: Benign escalateTicket (opt-out, normal ticket) ---"

RESP=$(call_tool "escalateTicket" '{"title":"Login page returns 500","severity":"P2","details":"Users report intermittent 500 errors on /login","x-request-id":"wk-test0005"}' 5)
CODE=$(get_error_code "$RESP")

if [ "$CODE" = "-32001" ]; then
    fail "opt-out-benign" "benign escalateTicket with skipSafetyCheck was blocked"
else
    pass "opt-out-benign: normal escalateTicket passes through"
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
echo "Skipped tests depend on server configuration (WANAKU_SAFETY_* env vars)."
echo "To test blocking, restart the server with:"
echo "  WANAKU_SAFETY_LLM_URL=${LLM_URL} WANAKU_SAFETY_RED_ACTION=block cargo run"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
