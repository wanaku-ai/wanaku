# Test Plan: Basic UI Tests

## Overview

This test plan runs the Playwright e2e tests against a Wanaku router instance — either local or remote. It verifies the core CRUD operations of the admin UI: Dashboard, Tools, Resources, and Prompts pages.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `node` | 18+ | `node --version` |
| `yarn` | 1.22+ | `yarn --version` |
| `npx` | any | `npx --version` |
| `curl` | any | `curl --version` |

### Prerequisite check

```bash
node --version || { echo "FAIL: node not found"; exit 1; }
yarn --version || { echo "FAIL: yarn not found"; exit 1; }
npx --version || { echo "FAIL: npx not found"; exit 1; }
curl --version > /dev/null || { echo "FAIL: curl not found"; exit 1; }
echo "PASS: all prerequisites met"
```

### Environment variables

```bash
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-http://localhost:8080}"
```

| Variable | Default | Description |
|----------|---------|-------------|
| `WANAKU_ROUTER_URL` | `http://localhost:8080` | Base URL of the Wanaku router (no trailing slash) |

### Remote instance requirements

The router at `WANAKU_ROUTER_URL` must:
- Be running and healthy (`/q/health/ready` returns 200)
- Have auth disabled (`WANAKU_HTTP_AUTH=none`) or the test user must be authenticated
- Serve the admin UI at `/admin/`

---

## Phase 0: Verify Router Connectivity

### Test 0.1: Router health check

```bash
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/health/ready")
if [ "${HTTP_STATUS}" -eq 200 ]; then
  echo "PASS: router healthy at ${WANAKU_ROUTER_URL}"
else
  echo "FAIL: router not reachable (HTTP ${HTTP_STATUS})"
  exit 1
fi
```

### Test 0.2: Admin UI accessible

```bash
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/admin/")
if [ "${HTTP_STATUS}" -eq 200 ]; then
  echo "PASS: admin UI accessible"
else
  echo "FAIL: admin UI not accessible (HTTP ${HTTP_STATUS})"
  exit 1
fi
```

---

## Phase 1: Install Dependencies

### Step 1.1: Install Node.js dependencies

```bash
cd tests/e2e/ui
yarn install
if [ $? -eq 0 ]; then
  echo "PASS: dependencies installed"
else
  echo "FAIL: yarn install failed"
  exit 1
fi
```

### Step 1.2: Install Playwright browser

```bash
cd tests/e2e/ui
npx playwright install chromium
if [ $? -eq 0 ]; then
  echo "PASS: Chromium installed"
else
  echo "FAIL: Playwright browser install failed"
  exit 1
fi
```

---

## Phase 2: Run Test Suite

### Test 2.1: Execute all Playwright tests

```bash
cd tests/e2e/ui
WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL}" npx playwright test --reporter=line 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: all Playwright tests passed"
else
  echo "FAIL: Playwright tests failed (exit code ${EXIT_CODE})"
  echo "Run 'npx playwright show-report' in tests/e2e/ui/ for details"
  exit 1
fi
```

### Test 2.2 (optional): Run tests in headed mode for visual verification

```bash
cd tests/e2e/ui
WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL}" npx playwright test --headed
```

---

## Phase 3: Verify Results

### Test 3.1: Check test report was generated

```bash
if [ -d "tests/e2e/ui/playwright-report" ]; then
  echo "PASS: test report generated"
else
  echo "FAIL: no test report found"
fi
```

### Test 3.2: View HTML report (optional)

```bash
cd tests/e2e/ui
npx playwright show-report
```

---

## Test Summary

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1 | Router health check | Critical |
| 0 | 0.2 | Admin UI accessible | Critical |
| 1 | 1.1 | Install Node.js dependencies | Critical |
| 1 | 1.2 | Install Playwright browser | Critical |
| 2 | 2.1 | Execute all Playwright tests | Critical |
| 2 | 2.2 | Headed mode visual verification | Medium |
| 3 | 3.1 | Check test report generated | Medium |
| 3 | 3.2 | View HTML report | Medium |

### Covered UI scenarios (via Playwright)

| Page | Scenario | Priority |
|------|----------|----------|
| Dashboard | Page loads with title and description | High |
| Dashboard | Statistics tiles show numeric values | High |
| Dashboard | Capability sections visible | High |
| Dashboard | Refresh button reloads stats | Medium |
| Tools | Page displays title | High |
| Tools | Add tool via modal | Critical |
| Tools | Edit tool via modal | Critical |
| Tools | Delete tool | Critical |
| Resources | Page displays title | High |
| Resources | Add resource via modal | Critical |
| Resources | Edit resource via modal | Critical |
| Resources | Delete resource | Critical |
| Prompts | Page displays title | High |
| Prompts | Add prompt via modal | Critical |
| Prompts | Delete prompt | Critical |
