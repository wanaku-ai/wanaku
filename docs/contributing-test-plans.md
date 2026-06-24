# Writing Test Plans

Test plans are Markdown documents under `tests/plans/` designed for execution by both humans and AI agents.

## Structure

A test plan has three layers:

1. **Main plan** (`tests/plans/<name>.md`) — the test scenario with phases and assertions.
2. **Common steps** (`tests/plans/common/*.md`) — reusable procedures shared across plans (Keycloak setup, namespace creation, cleanup).
3. **Environment variables** — all configurable values declared upfront so the same plan works across versions and clusters.

## Phases, not scripts

Organize tests into numbered phases that run sequentially. Each phase groups related assertions. This makes it easy to skip phases, resume after failure, or run a subset.

```text
Phase 0: Manual prerequisites
Phase 1: Environment setup
Phase 2-N: Test scenarios
Phase N+1: Cleanup
```

## Every step must be verifiable

Each step needs: a command, an expected outcome, and a PASS/FAIL assertion. Avoid steps that only run a command without checking the result.

```bash
# Bad — runs but doesn't verify
oc apply -f my-resource.yaml

# Good — verifies the outcome
oc apply -f my-resource.yaml
oc wait myresource/name --for=condition=Ready --timeout=120s -n "${NAMESPACE}"
```

## Prefer polling over sleeping

Use `oc wait`, `oc rollout status`, or a polling loop instead of fixed `sleep` calls. Clusters vary in speed — a 10s sleep that works locally may fail in CI and waste time on fast clusters.

```bash
# Bad
sleep 15
oc get deployment foo

# Good — deterministic wait
oc wait --for=condition=Available deployment/foo --timeout=120s -n "${NAMESPACE}"

# Good — polling for deletion
while oc get deployment foo -n "${NAMESPACE}" > /dev/null 2>&1; do
  sleep 3
done
```

## Parametrize images and versions

Never hard-code image tags. Define environment variables with sensible defaults so the same plan can test different releases.

```bash
export MY_IMAGE="${MY_IMAGE:-quay.io/org/component:latest}"
```

Then use `${MY_IMAGE}` in CR specs and image assertions.

## Extract reusable steps into common docs

If a procedure appears in more than one plan (or is likely to), move it to `tests/plans/common/`. The common doc should:

- List its prerequisites and required environment variables.
- Document output variables it sets for subsequent steps.
- Be self-contained — runnable without reading the main plan.

Reference common docs from the main plan with a link and a brief note on what variables must be set before and after:

```markdown
Follow [common/keycloak-setup.md](common/keycloak-setup.md). After completion,
`KEYCLOAK_URL` and `WANAKU_OIDC_SECRET` must be set.
```

## Keep the main plan lean

The main plan should only contain logic unique to that test scenario. If a step duplicates a common doc, replace it with a reference. This prevents drift between documents.

## Use the Wanaku CLI, not raw API clients

Test plans **must** use the Wanaku CLI for all interactions with the router and Keycloak. Use either a native binary installed on the system or — preferably — the CLI jar file built from the codebase (`java -jar apps/wanaku-cli/target/quarkus-app/quarkus-run.jar`).

Do **not** use `curl`, `wget`, `httpie`, or other HTTP clients to call Wanaku or Keycloak APIs unless the test is specifically oriented to verify HTTP-level behavior (e.g., checking raw HTTP status codes on health endpoints, testing SSE streaming headers, or verifying endpoint reachability before the CLI can connect).

```bash
# Bad — uses curl to list tools
curl -s "${WANAKU_ROUTER_URL}/api/v1/tools" | jq '.data[].name'

# Good — uses the CLI
wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain

# Acceptable — specifically testing HTTP endpoint reachability
curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/health/ready"
```

## Shell compatibility

Use POSIX-compatible constructs. Avoid bash-only features like `${!VAR}` (indirect expansion) since the executor may use zsh or sh. If bash is required, wrap the block in `bash -c '...'`.

## Include negative tests

Verify that invalid inputs are rejected gracefully — missing required fields, references to non-existent resources, malformed data. These often catch real bugs.

## Cleanup must be idempotent

Use `--ignore-not-found=true` on all delete commands and `2>/dev/null || true` on wait commands. A cleanup phase that fails on missing resources makes re-runs painful.

## Checklist for new plans

- [ ] All configurable values are environment variables with defaults
- [ ] Common procedures reference shared docs, not inline copies
- [ ] Every step has a PASS/FAIL assertion
- [ ] No fixed `sleep` — use `oc wait`, `oc rollout status`, or polling
- [ ] Uses the Wanaku CLI — no `curl`/`wget`/`httpie` against Wanaku or Keycloak unless specifically testing HTTP behavior
- [ ] Negative tests are included
- [ ] Cleanup is idempotent
- [ ] Shell constructs are POSIX-compatible

## Feature Matrix

- OpenShift/Kubernetes
  - Does not support noauth (i.e.: running the router and capabilities without authentication, due to the need of re-augmentation).
