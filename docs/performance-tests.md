# Performance Tests

This document describes how to run Wanaku performance tests. All test infrastructure lives under `tests/load/`.

## Prerequisites

- Java 21+ (25 recommended)
- Maven 3.9+
- [k6](https://grafana.com/docs/k6/) with the [xk6-mcp](https://github.com/nicholasgasior/xk6-mcp) extension
- Podman (for Keycloak)
- Python 3 (for report generation)

Ensure `k6` is on your `PATH` or set `K6_BIN` to its location.

## Architecture Overview

There are two test paths, each targeting a different bridge type:

```
                          ┌─────────────────────────────────┐
                          │         Wanaku Router            │
  k6 ──SSE──►  /public/mcp/sse  ──┬── gRPC bridge ──► capability (tool-noop / static-file)
                                   └── MCP bridge  ──► mock MCP server (SSE forward)
                          └─────────────────────────────────┘
```

| Bridge | Backend | Tool name | Resource URI | Capability module |
|--------|---------|-----------|--------------|-------------------|
| gRPC | `wanaku-tool-performance-noop`, `wanaku-provider-performance-static-file` | `performancenoop` | `in-memory-file.txt` | `capabilities/tools/wanaku-tool-performance-noop`, `capabilities/providers/wanaku-provider-performance-static-file` |
| MCP (forward) | `wanaku-performance-test-mock-mcp` | `mockTool` | `file:///mock/data` | `tests/mcp-servers/wanaku-performance-test-mock-mcp` |

## Quick Reference

### File Layout

```
tests/load/
├── run-perf-test.sh          # Single-run test runner (any bridge)
├── run-perf-evaluation.sh    # Full baseline-vs-patched evaluation (gRPC bridge, CI-based)
├── generate-perf-report.py   # Comparison report generator
├── mcp-tools-invoke-sse.js   # k6 script: tool invocation via SSE
└── mcp-resources-read-sse.js # k6 script: resource read via SSE

tests/mcp-servers/wanaku-performance-test-mock-mcp/   # Mock MCP server for MCP bridge tests
```

### Key Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `K6_BIN` | `$HOME/bin/k6` | Path to k6 binary |
| `WANAKU_BIN` | `$HOME/bin/wanaku` | Path to wanaku CLI |
| `JAVA_OPTS` | `-XX:+UseNUMA -Xmx4G -Xms4G` | JVM options for router and capabilities |
| `EVAL_DIR` | `$HOME/perf-evaluation-<timestamp>` | Output directory for evaluation results |
| `TEST_SCOPE` | `all` | `all`, `tools`, or `resources` |
| `SKIP_BASELINE` | `false` | Skip the baseline run in evaluations |
| `SKIP_PATCHED` | `false` | Skip the patched run in evaluations |
| `SKIP_BUILD` | `false` | Skip the Maven build step |
| `BASELINE_BRANCH` | `main` | Branch to use as baseline |

## Test Scenarios

### 1. gRPC Bridge Tests (via `run-perf-test.sh`)

Tests the gRPC bridge path using standalone capability providers. Requires pre-built distribution archives.

#### Build

```bash
mvn package -Pdist -DskipTests -T1C -q
```

This produces `.tar.gz` archives under each module's `target/distributions/` directory.

#### Run

```bash
tests/load/run-perf-test.sh \
  --router-from wanaku-router/wanaku-router-backend/target/distributions/wanaku-router-backend-0.1.0-SNAPSHOT.tar.gz \
  --capability-from capabilities/tools/wanaku-tool-performance-noop/target/distributions/wanaku-tool-performance-noop-0.1.0-SNAPSHOT.tar.gz \
  --suite tools-sse \
  --test-name my-run \
  --test-base-dir /tmp/perf-results
```

The script handles Keycloak startup, credential acquisition, router launch, capability registration, and k6 execution at VU levels 1, 10, 500, 1000, 2000, 30000.

Available suites: `sse` (both), `tools-sse`, `resources-sse`.

You can also pass custom k6 scripts with `--test NAME PATH`:

```bash
--test my-custom-test ./my-script.js
```

#### Artifacts

`run-perf-test.sh` accepts `--router-from` and `--capability-from` as either local paths or HTTP URLs. This allows testing against CI-built artifacts directly.

### 2. MCP Bridge Tests (via mock MCP server)

Tests the MCP bridge path where the router forwards requests to a remote MCP server over SSE.

#### Build

```bash
mvn package -pl wanaku-router/wanaku-router-backend,tests/mcp-servers/wanaku-performance-test-mock-mcp -am -DskipTests -T1C -q
```

#### Run Manually

Start the components in this order:

```bash
# 1. Ensure Keycloak is running (port 8543)
podman run -d --name keycloak --rm -p 0.0.0.0:8543:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -v keycloak-dev:/opt/keycloak/data \
  quay.io/keycloak/keycloak:26.3.5 start-dev

# 2. Start the router
java -XX:+UseNUMA -Xmx4G -Xms4G \
  -Dquarkus.http.host=0.0.0.0 \
  -Dauth.server="http://$(hostname -f):8543" \
  -jar wanaku-router/wanaku-router-backend/target/quarkus-app/quarkus-run.jar &

# 3. Wait for router to be ready
until curl -fsSo /dev/null http://localhost:8080 2>/dev/null; do sleep 2; done

# 4. Start the mock MCP server
java -XX:+UseNUMA -Xmx1G -Xms1G \
  -Dwanaku.service.registration.uri="http://localhost:8080" \
  -Dwanaku.service.performance.delay=0 \
  -Dwanaku.mcp.service.namespace=public \
  -jar tests/mcp-servers/wanaku-performance-test-mock-mcp/target/quarkus-app/quarkus-run.jar &

# 5. Wait for registration (~15 seconds)
sleep 15

# 6. Verify registration
curl -s http://localhost:8080/api/v1/forwards/list | python3 -m json.tool

# 7. Run k6
k6 run --vus 10 --duration 30s tests/load/mcp-tools-invoke-sse.js
k6 run --vus 10 --duration 30s tests/load/mcp-resources-read-sse.js
```

#### Mock MCP Server Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `wanaku.service.performance.delay` | `100` | Artificial delay in ms added to each tool/resource response. Set to `0` for pure throughput tests. |
| `wanaku.mcp.service.namespace` | `test` | Namespace the forward registers under. Set to `public` to expose tools/resources on the unauthenticated `/public/mcp/sse` endpoint. |
| `wanaku.service.registration.uri` | `http://localhost:8080` | Router URL for forward registration. |
| `wanaku.service.registration.mcp-forward-address` | `http://localhost:8181/mcp/sse` | SSE endpoint the router will use to reach this server. |

All properties can be overridden via `-D` flags on the command line.

**Important:** The `namespace` determines which SSE endpoint exposes the tools. If set to `test`, tools appear under an authenticated namespace (e.g., `/ns-9/mcp/sse`). Set to `public` for unauthenticated access at `/public/mcp/sse`, which is what the k6 scripts target.

#### Data Store

The router persists forwards in `~/.wanaku/router/`. If you see stale data between runs, clear it:

```bash
rm -rf ~/.wanaku/router/forward/{data,index}/*
rm -rf ~/.wanaku/router/namespace/{data,index}/*
rm -rf ~/.wanaku/router/tool/{data,index}/*
rm -rf ~/.wanaku/router/resource/{data,index}/*
```

### 3. Full Baseline vs Patched Evaluation

#### gRPC Bridge Evaluation (CI-based)

Uses `run-perf-evaluation.sh` to download baseline artifacts from CI (main branch), build the current branch, run both, and generate a comparison report:

```bash
tests/load/run-perf-evaluation.sh
```

Override defaults with environment variables:

```bash
TEST_SCOPE=tools SKIP_BASELINE=true tests/load/run-perf-evaluation.sh
```

#### MCP Bridge Evaluation (local build)

For comparing main vs a feature branch through the MCP bridge, build and test each branch separately:

```bash
# 1. Build baseline from main
git checkout main
mvn package -pl wanaku-router/wanaku-router-backend,tests/mcp-servers/wanaku-performance-test-mock-mcp -am -DskipTests -T1C -q
# Copy baseline jars to a safe location
cp -r wanaku-router/wanaku-router-backend/target/quarkus-app /tmp/baseline-router
cp -r tests/mcp-servers/wanaku-performance-test-mock-mcp/target/quarkus-app /tmp/baseline-mock

# 2. Build patched from feature branch
git checkout my-feature-branch
mvn package -pl wanaku-router/wanaku-router-backend,tests/mcp-servers/wanaku-performance-test-mock-mcp -am -DskipTests -T1C -q

# 3. Run baseline tests, then patched tests (same VU levels, same duration)
#    Save results to: $EVAL_DIR/baseline/tools-invoke-sse/test-summary-vus-*.json
#                     $EVAL_DIR/patched/tools-invoke-sse/test-summary-vus-*.json
#    (and similarly for resources-read-sse)

# 4. Generate comparison report
python3 tests/load/generate-perf-report.py --eval-dir $EVAL_DIR --test-scope all
```

## Report Generation

`generate-perf-report.py` produces a Markdown comparison report from k6 JSON summary files.

### Expected Directory Structure

```
$EVAL_DIR/
├── baseline/
│   ├── tools-invoke-sse/
│   │   ├── test-summary-vus-1.json
│   │   ├── test-summary-vus-10.json
│   │   ├── vmstat-baseline-tools-invoke-sse.log    # optional
│   │   └── java-procs-baseline-tools-invoke-sse.log # optional
│   └── resources-read-sse/
│       └── test-summary-vus-*.json
└── patched/
    ├── tools-invoke-sse/
    │   └── test-summary-vus-*.json
    └── resources-read-sse/
        └── test-summary-vus-*.json
```

### Usage

```bash
python3 tests/load/generate-perf-report.py \
  --eval-dir /path/to/eval-dir \
  --test-scope all \
  --output /path/to/report.md
```

Options:
- `--eval-dir` (required): directory containing `baseline/` and `patched/` subdirectories
- `--test-scope`: `all` (default), `tools`, or `resources`
- `--output`: output file path (defaults to `$EVAL_DIR/perf-report.md`)

### Key Metrics

The report tracks these metrics per VU level:

| Metric | Direction | Description |
|--------|-----------|-------------|
| `mcp_request_duration` (avg, med, p90, p95, max) | Lower is better | End-to-end MCP request latency |
| `mcp_request_count` (rate, count) | Higher is better | Throughput |
| `mcp_request_errors` (rate, count) | Lower is better | Error count |
| `iterations` (rate, count) | Higher is better | Full iteration throughput |
| `iteration_duration` (avg, p95) | Higher is better | Full iteration time (includes all MCP calls per iteration) |
| `data_sent` / `data_received` (rate) | Higher is better | Network throughput |

The report uses indicators: green circle for >5% improvement, red circle for >10% regression.

## Writing Custom k6 Scripts

k6 scripts use the `k6/x/mcp` extension. Each iteration creates an SSE client, performs MCP operations, and the extension tracks `mcp_request_duration`, `mcp_request_count`, and `mcp_request_errors` automatically.

Template:

```javascript
import mcp from 'k6/x/mcp';

export default function () {
    const client = new mcp.SSEClient({
        base_url: 'http://localhost:8080/public/mcp/sse',
        timeout: 5
    });

    client.ping();

    // Your MCP operations here:
    // client.listAllTools()
    // client.callTool({ name: 'toolName', arguments: { key: 'value' } })
    // client.listAllResources()
    // client.readResource({ uri: 'resource-uri' })
}
```

Register custom scripts in `run-perf-test.sh` by adding to the `SUITES` map, or run them ad-hoc:

```bash
tests/load/run-perf-test.sh \
  --router-from /path/to/router.tar.gz \
  --test my-test ./my-script.js
```

## Troubleshooting

**Tools/resources not listed via SSE**: Check the namespace. The k6 scripts target `/public/mcp/sse`. If the forward registered with a non-public namespace, tools will only appear under the corresponding authenticated namespace endpoint (e.g., `/ns-9/mcp/sse`). Override with `-Dwanaku.mcp.service.namespace=public`.

**Stale forward registrations**: The router persists forwards in `~/.wanaku/router/`. Clear the data store (see [Data Store](#data-store) above) and restart.

**High latency at 500+ VUs**: Expected. The SSE transport creates a new connection per iteration. At high concurrency, connection queuing dominates. The median latency stays low but P95 increases significantly.

**Mock MCP server config parse failure**: Do not pass `-Dwanaku.mcp.service.namespace=""` (empty string). Quarkus cannot parse this. Either omit the flag (uses default from `application.properties`) or set it to an explicit value like `public`.
