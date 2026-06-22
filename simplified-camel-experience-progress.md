# Simplified Camel Experience — Work Stream Progress

## Branch & PR

- **Branch:** `ci-simplified-camel-experience`
- **PR:** [#1372](https://github.com/wanaku-ai/wanaku/pull/1372)
- **Repo (fork):** `orpiske/wanaku`
- **Upstream:** `wanaku-ai/wanaku`
- **CI images:** `quay.io/orpiske/wanaku-operator:ci-simplified-camel-experience` and `quay.io/orpiske/wanaku-router-backend:ci-simplified-camel-experience`

## Issues Addressed

| Issue | Title | Status |
|-------|-------|--------|
| #1132 | WanakuCamelRoute CRD model | Implemented |
| #1133 | CamelRoutePackager utility | Implemented |
| #1134 | WanakuCamelRouteReconciler | Implemented |
| #1135 | Helm chart updates (RBAC, CRD, values) | Implemented |
| #1136 | Unit tests for CamelRoutePackager | Implemented (8 tests) |
| #1137 | E2E test resources / example CRs | Implemented |
| #1138 | CRD documentation | Implemented |
| #1387 | Router startupProbe missing | Fixed |
| #1388 | Operator REST client unauthenticated to OIDC router | Fixed (2 commits — initial + event loop fix) |
| #1389 | Validation errors not in CR status conditions | Fixed |

## Commits (oldest to newest)

1. `b4181933c` — Fix #1132-#1138: WanakuCamelRoute CRD (main feature)
2. `5ded64951` — E2E test plan (`tests/plans/operator-camel-route.md`)
3. `70107e073` — Fix #1387: startupProbe on router deployment (305s window)
4. `d186d4238` — Fix test plan: label selectors (`app=` not `app.kubernetes.io/name=`) and reduced timeouts
5. `ee5580842` — Fix #1388: OperatorAuthHelper + BearerTokenFilter for OIDC auth
6. `a793a9c1d` — Fix #1389: setErrorStatus() for validation/deployment errors
7. `51a84f666` — Fix #1388 (follow-up): fetch token on reconciler thread, not Vert.x event loop

## Key Files

### New files

| File | Purpose |
|------|---------|
| `apps/wanaku-operator/src/main/java/ai/wanaku/operator/wanaku/WanakuCamelRoute.java` | CRD class |
| `apps/wanaku-operator/src/main/java/ai/wanaku/operator/wanaku/WanakuCamelRouteSpec.java` | Spec with `routerRef`, `route` (JsonNode), `mcp` (McpSpec), `properties` |
| `apps/wanaku-operator/src/main/java/ai/wanaku/operator/wanaku/WanakuCamelRouteStatus.java` | Status with conditions, deployedCatalogName, registeredTools/Resources |
| `apps/wanaku-operator/src/main/java/ai/wanaku/operator/wanaku/WanakuCamelRouteReconciler.java` | Reconciler: validate → package → deploy → set status |
| `apps/wanaku-operator/src/main/java/ai/wanaku/operator/util/CamelRoutePackager.java` | Packages spec into Base64-encoded service catalog ZIP |
| `apps/wanaku-operator/src/main/java/ai/wanaku/operator/util/OperatorAuthHelper.java` | OIDC token acquisition/caching for operator-to-router calls |
| `apps/wanaku-operator/src/test/java/ai/wanaku/operator/util/CamelRoutePackagerTest.java` | 8 unit tests |
| `apps/wanaku-operator/src/test/java/ai/wanaku/operator/util/OperatorAuthHelperTest.java` | 14 unit tests |
| `apps/wanaku-operator/deploy/helm/wanaku-operator/crds/wanakucamelroutes.wanaku.ai-v1.yml` | CRD manifest |
| `apps/wanaku-operator/deploy/helm/wanaku-operator/templates/wanaku-camel-route-crd-role-binding.yaml` | RBAC RoleBinding template |
| `tests/plans/operator-camel-route.md` | E2E test plan (11 phases, 30+ test cases) |
| `docs/camel-route-crd.md` | CRD reference documentation |
| `docs/examples/camel-route/wanaku-camel-route-simple.yaml` | Example: simple greeting tool |
| `docs/examples/camel-route/wanaku-camel-route-kafka.yaml` | Example: Kafka request-reply tool |

### Modified files

| File | Change |
|------|--------|
| `apps/wanaku-operator/deploy/helm/wanaku-operator/templates/clusterrole.yaml` | Added `wanaku-camel-route-cluster-role` |
| `apps/wanaku-operator/deploy/helm/wanaku-operator/values.yaml` | Added JOSDK controller namespace config |
| `apps/wanaku-operator/pom.xml` | Added antrun replace for camel-route ClusterRole |
| `apps/wanaku-operator/src/main/resources/application.properties` | Added Helm expression indices 13/14 |
| `apps/wanaku-operator/src/main/resources/ai/wanaku/operator/wanaku/wanaku-router-deployment.yaml` | Added startupProbe |
| `apps/wanaku-operator/src/main/java/ai/wanaku/operator/wanaku/WanakuServiceCatalogReconciler.java` | Added OIDC auth + error status handling |

## QA Test Results (4 rounds)

### Round 4 (2026-06-22, all fixes applied)

| Phase | Result | Notes |
|-------|--------|-------|
| 0 | **SKIP** | Pre-completed (already logged in) |
| 1 | **PASS** | Namespace + Keycloak deployed and realm imported |
| 2 | **PASS** | CRD registered, RBAC created, operator healthy |
| 3 | **PASS** | Router pod ready, deployment available, REST API reachable |
| 4 | **PASS** | Tool CR → Ready, status fields correct, catalog in router, OIDC auth working |
| 5 | **PASS** | Resource CR → Ready, status fields correct, catalog in router |
| 6 | **PASS** | Combined CR (tools+resources+properties) → Ready, all status fields correct |
| 7 | **PASS** | Patch CR → re-reconciled, registeredTools updated (4 reconciliations) |
| 8 | **PASS** | All 5 negative tests pass (missing routerRef/route/mcp, empty mcp, non-existent router) |
| 9 | **PASS** | All 3 CRs deleted, catalogs removed from router, other catalogs unaffected |
| 10 | **PASS** | Operator running, 0 restarts, no CamelRoutes remain, no unexpected errors |
| 11 | **PASS** | Full cleanup completed |

**ALL 30+ TESTS PASSED.**

### Test plan fixes applied after Round 4

- Updated `query_router_api()` helper to obtain and pass a Bearer token (OIDC auth required even from localhost)
- Added `get_router_token()` helper to obtain tokens from Keycloak via `oc exec`
- Added workaround step to `keycloak-setup.md` for literal `${WANAKU_SERVICE_SECRET:mypasswd}` issue

### Round 3 (after #1387 + #1388 + #1389 fixes)

| Phase | Result | Notes |
|-------|--------|-------|
| 0-2 | **PASS** | Login, namespace, Keycloak, operator, CRD, RBAC |
| 3 | **PASS** | Router starts (1 restart under startupProbe, OK) |
| 4 | **FAIL** | BUG: BearerTokenFilter calls getToken() on Vert.x event loop → silent failure → HTTP 302 |
| 5-7 | **BLOCKED** | Blocked by Phase 4 auth failure |
| 8 | **PASS** | All 5 negative validation tests pass |
| 9 | **BLOCKED** | No catalogs deployed to test deletion |
| 10-11 | **PASS** | Operator healthy, cleanup successful |

### What was fixed after Round 3

- Commit `51a84f666`: Token is now fetched on the reconciler worker thread (before building the REST client) and passed as a pre-resolved String to the BearerTokenFilter. This avoids the Vert.x event loop blocking issue.

## Known Issues (not in this PR's scope)

| Issue | Title | Status |
|-------|-------|--------|
| #1386 | WanakuRouterReconciler throws NoEventSourceForClassException when no existing deployment | Open, assigned to orpiske |
| — | Keycloak realm import stores literal `${WANAKU_SERVICE_SECRET:mypasswd}` instead of resolved value | Not filed (pre-existing, workaround: update secret via admin API) |

## Test Environment

- **Cluster:** IBM Cloud ROKS (OpenShift 4.18.40, K8s 1.31.14)
- **Namespace:** `wanaku-test`
- **Auth:** Keycloak with `wanaku` realm, `wanaku-service` client (client_credentials grant)
- **Operator env var:** `WANAKU_OIDC_CLIENT_SECRET=mypasswd` must be set on the operator deployment
- **Test plan:** `tests/plans/operator-camel-route.md`
- **Helpers script:** `/tmp/wanaku-test-helpers.sh` (source before every `oc`/`helm` command)

## How to Continue

1. ~~Wait for CI build to pass after latest push~~ ✓ (all green)
2. ~~Run `/qa-verify operator-camel-route` to execute the test plan~~ ✓ (all 30+ tests pass)
3. Commit the test plan fixes and push
4. PR is ready for review
