#!/bin/bash
set -euo pipefail

# ---- Defaults ----
ROUTER_FROM=""
TEST_FILE_FROM=""
TEST_NAME=""
TEST_BASE_DIR=""
CAPABILITY_SOURCES=()
CAPABILITY_ARGS=()
KEYCLOAK_IMAGE="quay.io/keycloak/keycloak:26.3.5"
K6_BIN="${K6_BIN:-$HOME/bin/k6}"
WANAKU_BIN="${WANAKU_BIN:-$HOME/bin/wanaku}"
JAVA_OPTS="${JAVA_OPTS:--XX:+UseNUMA -Xmx4G -Xms4G}"
VU_LEVELS=(1 10 500 1000 2000 30000)
TEST_DURATION="30s"
GRPC_PORT_START=9190

# ---- Tracked PIDs & temp dir ----
PIDS=()
TMPDIR_PATH=""
KEYCLOAK_STARTED=false

usage() {
    cat <<'EOF'
Usage: run-perf-test.sh [OPTIONS]

Options:
  --router-from URL|PATH       Router archive URL or local path (required)
  --capability-from URL|PATH   Capability archive URL or local path (repeatable)
  --capability-args 'ARGS'     Extra CLI args for the preceding --capability-from (repeatable, paired)
  --test-file-from URL|PATH    k6 test file URL or local path (required)
  --test-name NAME             Test name, used in output directory (required)
  --test-base-dir DIR          Override default test results directory
  --help                       Show this help message

Environment variables:
  K6_BIN          Path to k6 binary (default: $HOME/bin/k6)
  WANAKU_BIN      Path to wanaku CLI binary (default: $HOME/bin/wanaku)
  JAVA_OPTS       JVM options for router and capabilities (default: -XX:+UseNUMA -Xmx4G -Xms4G)

Examples:
  # From URLs:
  ./run-perf-test.sh \
    --router-from https://example.com/router.tar.gz \
    --capability-from https://example.com/cap1.tar.gz \
    --capability-args '--name mock-provider' \
    --test-file-from https://example.com/test.js \
    --test-name smoke

  # From local paths:
  ./run-perf-test.sh \
    --router-from /path/to/router.tar.gz \
    --capability-from /path/to/capability.zip \
    --capability-args '--name mock-provider' \
    --test-file-from ./tests/load/mcp-resources-read-sse.js \
    --test-name smoke
EOF
    exit 0
}

# ---- Cleanup ----
cleanup() {
    echo "Cleaning up..."

    if [ ${#PIDS[@]} -gt 0 ]; then
        for pid in "${PIDS[@]}"; do
            if kill -0 "$pid" 2>/dev/null; then
                echo "Stopping process $pid (SIGTERM)"
                kill "$pid" 2>/dev/null || true
            fi
        done
        # Give processes time to shut down gracefully
        sleep 2
        for pid in "${PIDS[@]}"; do
            if kill -0 "$pid" 2>/dev/null; then
                echo "Force killing process $pid (SIGKILL)"
                kill -9 "$pid" 2>/dev/null || true
            fi
        done
        for pid in "${PIDS[@]}"; do
            wait "$pid" 2>/dev/null || true
        done
    fi

    if [ "$KEYCLOAK_STARTED" = true ]; then
        echo "Stopping keycloak container"
        podman stop keycloak 2>/dev/null || true
    fi

    if [ -n "$TMPDIR_PATH" ] && [ -d "$TMPDIR_PATH" ]; then
        echo "Removing temp dir $TMPDIR_PATH"
        rm -rf "$TMPDIR_PATH"
    fi

    echo "Cleanup complete."
}

trap cleanup EXIT
trap 'exit 1' INT TERM

# ---- Arg parsing ----
while [ $# -gt 0 ]; do
    case "$1" in
        --router-from)
            ROUTER_FROM="$2"; shift 2 ;;
        --capability-from)
            CAPABILITY_SOURCES+=("$2"); shift 2 ;;
        --capability-args)
            # Pad the args array to align with the most recent capability source
            while [ ${#CAPABILITY_ARGS[@]} -lt $(( ${#CAPABILITY_SOURCES[@]} - 1 )) ]; do
                CAPABILITY_ARGS+=("")
            done
            CAPABILITY_ARGS+=("$2"); shift 2 ;;
        --test-file-from)
            TEST_FILE_FROM="$2"; shift 2 ;;
        --test-name)
            TEST_NAME="$2"; shift 2 ;;
        --test-base-dir)
            TEST_BASE_DIR="$2"; shift 2 ;;
        --help)
            usage ;;
        *)
            echo "Unknown option: $1" >&2; exit 1 ;;
    esac
done

# Pad capability args array to match capability sources length
while [ ${#CAPABILITY_ARGS[@]} -lt ${#CAPABILITY_SOURCES[@]} ]; do
    CAPABILITY_ARGS+=("")
done

# ---- Validate required args ----
if [ -z "$ROUTER_FROM" ]; then
    echo "Error: --router-from is required" >&2; exit 1
fi
if [ -z "$TEST_FILE_FROM" ]; then
    echo "Error: --test-file-from is required" >&2; exit 1
fi
if [ -z "$TEST_NAME" ]; then
    echo "Error: --test-name is required" >&2; exit 1
fi

# ---- Resolve output directory ----
if [ -z "$TEST_BASE_DIR" ]; then
    TEST_DATA_DIR="$HOME/Sync/Data/test-results/wanaku/$TEST_NAME"
else
    TEST_DATA_DIR="$TEST_BASE_DIR/$TEST_NAME"
fi
mkdir -p "$TEST_DATA_DIR"
echo "Test results will be saved to: $TEST_DATA_DIR"

# ---- Create temp dir ----
TMPDIR_PATH=$(mktemp -d)
echo "Using temp dir: $TMPDIR_PATH"

# ---- Resolve source (URL or local path) & unpack helper ----
# Checks if source is a URL or local path, fetches/copies accordingly, then unpacks archives.
resolve_source() {
    local source="$1"
    local dest="$2"
    local filename
    filename=$(basename "$source")

    case "$source" in
        http://*|https://*)
            echo "Downloading $source ..."
            curl -fsSL -o "$dest/$filename" "$source"
            ;;
        *)
            if [ ! -e "$source" ]; then
                echo "Error: local path does not exist: $source" >&2
                return 1
            fi
            echo "Copying from local path $source ..."
            cp -r "$source" "$dest/$filename"
            ;;
    esac

    case "$filename" in
        *.tar.gz|*.tgz)
            tar xzf "$dest/$filename" -C "$dest"
            rm -f "$dest/$filename"
            ;;
        *.zip)
            unzip -qo "$dest/$filename" -d "$dest"
            rm -f "$dest/$filename"
            ;;
    esac
}

# ---- Detect app type ----
# Prints "type:path" to stdout (e.g. "quarkus:/tmp/x/quarkus-run.jar")
detect_app_type() {
    local dir="$1"
    local quarkus_jar
    quarkus_jar=$(find "$dir" -name 'quarkus-run.jar' -print -quit 2>/dev/null || true)
    if [ -n "$quarkus_jar" ]; then
        echo "quarkus:$quarkus_jar"
        return
    fi

    local fat_jar
    fat_jar=$(find "$dir" -name '*-jar-with-dependencies.jar' -print -quit 2>/dev/null || true)
    if [ -n "$fat_jar" ]; then
        echo "fatjar:$fat_jar"
        return
    fi

    echo "unknown:"
}

# ---- Wait for HTTP endpoint ----
wait_for_http() {
    local url="$1"
    local label="$2"
    local max_attempts="${3:-60}"
    local attempt=0

    echo "Waiting for $label at $url ..."
    while [ $attempt -lt $max_attempts ]; do
        if curl -fsSo /dev/null "$url" 2>/dev/null; then
            echo "$label is ready."
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 2
    done

    echo "Error: $label did not become ready after $((max_attempts * 2)) seconds" >&2
    return 1
}

# ---- Step 1: Resolve router ----
echo "=== Resolving router ==="
ROUTER_DIR="$TMPDIR_PATH/router"
mkdir -p "$ROUTER_DIR"
resolve_source "$ROUTER_FROM" "$ROUTER_DIR"

ROUTER_DETECT=$(detect_app_type "$ROUTER_DIR")
ROUTER_TYPE="${ROUTER_DETECT%%:*}"
ROUTER_JAR="${ROUTER_DETECT#*:}"
if [ "$ROUTER_TYPE" = "unknown" ] || [ -z "$ROUTER_JAR" ]; then
    echo "Error: could not detect router application type in $ROUTER_DIR" >&2
    exit 1
fi
echo "Router detected as $ROUTER_TYPE: $ROUTER_JAR"

# ---- Step 2: Resolve capabilities ----
CAP_DIRS=()
CAP_TYPES=()
CAP_JARS=()
for i in "${!CAPABILITY_SOURCES[@]}"; do
    echo "=== Resolving capability $((i + 1)) ==="
    cap_dir="$TMPDIR_PATH/capability-$i"
    mkdir -p "$cap_dir"
    resolve_source "${CAPABILITY_SOURCES[$i]}" "$cap_dir"

    cap_detect=$(detect_app_type "$cap_dir")
    cap_type="${cap_detect%%:*}"
    cap_jar="${cap_detect#*:}"
    if [ "$cap_type" = "unknown" ] || [ -z "$cap_jar" ]; then
        echo "Error: could not detect capability type in $cap_dir" >&2
        exit 1
    fi
    echo "Capability $((i + 1)) detected as $cap_type: $cap_jar"
    CAP_DIRS+=("$cap_dir")
    CAP_TYPES+=("$cap_type")
    CAP_JARS+=("$cap_jar")
done

# ---- Step 3: Resolve test file ----
echo "=== Resolving k6 test file ==="
TEST_FILE="$TMPDIR_PATH/test.js"
case "$TEST_FILE_FROM" in
    http://*|https://*)
        echo "Downloading $TEST_FILE_FROM ..."
        curl -fsSL -o "$TEST_FILE" "$TEST_FILE_FROM"
        ;;
    *)
        if [ ! -f "$TEST_FILE_FROM" ]; then
            echo "Error: test file does not exist: $TEST_FILE_FROM" >&2
            exit 1
        fi
        echo "Copying from local path $TEST_FILE_FROM ..."
        cp "$TEST_FILE_FROM" "$TEST_FILE"
        ;;
esac
echo "Test file ready at $TEST_FILE"

# ---- Resolve hostnames ----
HOSTNAME_FQDN=$(hostname -f)
ROUTER_URL="http://${HOSTNAME_FQDN}:8080"
KEYCLOAK_URL="http://${HOSTNAME_FQDN}:8543"

# ---- Stop router & capabilities ----
stop_services() {
    echo "Stopping router and capabilities..."
    if [ ${#PIDS[@]} -gt 0 ]; then
        for pid in "${PIDS[@]}"; do
            if kill -0 "$pid" 2>/dev/null; then
                echo "Stopping process $pid (SIGTERM)"
                kill "$pid" 2>/dev/null || true
            fi
        done
        sleep 2
        for pid in "${PIDS[@]}"; do
            if kill -0 "$pid" 2>/dev/null; then
                echo "Force killing process $pid (SIGKILL)"
                kill -9 "$pid" 2>/dev/null || true
            fi
        done
        for pid in "${PIDS[@]}"; do
            wait "$pid" 2>/dev/null || true
        done
    fi
    PIDS=()
}

# ---- Start router, get credentials, start capabilities ----
start_services() {
    echo "=== Launching router ==="
    java $JAVA_OPTS -Dquarkus.profile=perf \
        -Dquarkus.http.host=0.0.0.0 \
        -Dauth.server="$KEYCLOAK_URL" \
        -jar "$ROUTER_JAR" &
    ROUTER_PID=$!
    PIDS+=("$ROUTER_PID")
    echo "Router started with PID $ROUTER_PID"
    wait_for_http "http://localhost:8080" "Router"

    echo "=== Obtaining credentials ==="
    CRED_OUTPUT=$("$WANAKU_BIN" admin credentials show \
        --admin-username admin --admin-password admin \
        --client-id wanaku-service --show-secret 2>&1)
    # Strip ANSI escape codes before parsing
    CRED_CLEAN=$(echo "$CRED_OUTPUT" | sed 's/\x1b\[[0-9;]*m//g')
    CLIENT_SECRET=$(echo "$CRED_CLEAN" | sed -n 's/.*Client Secret: \([^ ]*\).*/\1/p')
    if [ -z "$CLIENT_SECRET" ]; then
        echo "Error: could not parse client secret from wanaku output:" >&2
        echo "$CRED_OUTPUT" >&2
        exit 1
    fi
    echo "Client secret obtained."

    echo "=== Launching capabilities ==="
    GRPC_PORT=$GRPC_PORT_START
    for i in "${!CAP_JARS[@]}"; do
        cap_jar="${CAP_JARS[$i]}"
        cap_type="${CAP_TYPES[$i]}"
        cap_args="${CAPABILITY_ARGS[$i]}"

        echo "Starting capability $((i + 1)) ($cap_type) on gRPC port $GRPC_PORT ..."

        if [ "$cap_type" = "quarkus" ]; then
            java $JAVA_OPTS -Dquarkus.profile=perf \
                -Dquarkus.grpc.server.port="$GRPC_PORT" \
                -Dwanaku.service.registration.uri="$ROUTER_URL" \
                -Dquarkus.oidc-client.auth-server-url="$KEYCLOAK_URL/realms/wanaku" \
                -Dquarkus.oidc-client.client-id=wanaku-service \
                -Dquarkus.oidc-client.credentials.secret="$CLIENT_SECRET" \
                -jar "$cap_jar" \
                $cap_args &
        else
            java $JAVA_OPTS -jar "$cap_jar" \
                --grpc-port "$GRPC_PORT" \
                --router-url "$ROUTER_URL" \
                --client-id wanaku-service --client-secret "$CLIENT_SECRET" \
                $cap_args &
        fi

        cap_pid=$!
        PIDS+=("$cap_pid")
        echo "Capability $((i + 1)) started with PID $cap_pid"
        GRPC_PORT=$((GRPC_PORT + 1))
    done

    # Allow capabilities to start up and register with the router
    echo "Waiting for capabilities to register with the router..."
    sleep 15
}

# ---- Step 4: Keycloak ----
echo "=== Starting Keycloak ==="
if podman ps --filter name=keycloak --format '{{.Names}}' 2>/dev/null | grep -q '^keycloak$'; then
    echo "Keycloak container already running."
else
    echo "Starting keycloak container..."
    podman run -d --name keycloak --rm -p 0.0.0.0:8543:8080 \
        -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
        -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
        -v keycloak-dev:/opt/keycloak/data \
        "$KEYCLOAK_IMAGE" start-dev
    KEYCLOAK_STARTED=true
    wait_for_http "http://localhost:8543" "Keycloak" 90
fi

# ---- Step 5: Run k6 tests (restart services each iteration) ----
echo "=== Running k6 tests ==="
for vus in "${VU_LEVELS[@]}"; do
    echo "===== Iteration: $vus VUs ====="

    start_services

    echo "--- Running k6 with $vus VUs ---"
    "$K6_BIN" run \
        --no-usage-report \
        --tag "name=$TEST_NAME" \
        --out "csv=$TEST_DATA_DIR/test-results-vus-${vus}.csv" \
        --summary-export "$TEST_DATA_DIR/test-summary-vus-${vus}.json" \
        --vus "$vus" \
        --duration "$TEST_DURATION" \
        --console-output "$TEST_DATA_DIR/test-output-vus-${vus}.log" \
        --no-color \
        "$TEST_FILE" || echo "Warning: k6 run with $vus VUs exited with non-zero status"
    echo "--- Completed $vus VUs ---"

    stop_services
done

echo "=== All tests complete ==="
echo "Results saved to: $TEST_DATA_DIR"
