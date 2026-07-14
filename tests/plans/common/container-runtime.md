# Common: Container Runtime Detection

Reusable steps for detecting and configuring a local container runtime (Docker or Podman). Use this when a test plan needs to run containers locally (e.g., databases, Keycloak, supporting services).

## Prerequisites

- Either `docker` or `podman` must be installed and on the `PATH`.

## Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `CONTAINER_RUNTIME` | Override auto-detection with an explicit runtime | _(auto-detected)_ |

## Output variables

| Variable | Description |
|----------|-------------|
| `CONTAINER_RUNTIME` | The detected runtime command (`docker` or `podman`) |

## Steps

### 1. Detect container runtime

If `CONTAINER_RUNTIME` is already set (e.g., by the caller), skip auto-detection. Otherwise, prefer `docker` over `podman`.

```bash
if [ -n "${CONTAINER_RUNTIME}" ]; then
  if command -v "${CONTAINER_RUNTIME}" > /dev/null 2>&1; then
    echo "PASS: ${CONTAINER_RUNTIME} (pre-set) found at $(command -v ${CONTAINER_RUNTIME})"
  else
    echo "FAIL: CONTAINER_RUNTIME is set to '${CONTAINER_RUNTIME}' but it is not installed"
    exit 1
  fi
else
  if command -v docker > /dev/null 2>&1; then
    CONTAINER_RUNTIME="docker"
  elif command -v podman > /dev/null 2>&1; then
    CONTAINER_RUNTIME="podman"
  fi

  if [ -z "${CONTAINER_RUNTIME}" ]; then
    echo "FAIL: neither docker nor podman is installed"
    exit 1
  fi
  echo "PASS: ${CONTAINER_RUNTIME} found at $(command -v ${CONTAINER_RUNTIME})"
fi

export CONTAINER_RUNTIME
```

### 2. Verify the runtime is functional

```bash
${CONTAINER_RUNTIME} info > /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: ${CONTAINER_RUNTIME} is functional"
else
  echo "FAIL: ${CONTAINER_RUNTIME} is installed but not functional (daemon may not be running)"
  exit 1
fi
```

## Helper functions

### Start a container (idempotent)

Starts a named container if it is not already running. Skips gracefully if the container exists.

```bash
start_container_if_absent() {
  local NAME="$1"
  shift
  # remaining args are the "docker/podman run" arguments

  if ${CONTAINER_RUNTIME} ps --filter "name=^${NAME}$" --format '{{.Names}}' 2>/dev/null | grep -q "^${NAME}$"; then
    echo "PASS: container '${NAME}' is already running"
    return 0
  fi

  ${CONTAINER_RUNTIME} run -d --name "${NAME}" "$@"
  if [ $? -eq 0 ]; then
    echo "PASS: container '${NAME}' started"
  else
    echo "FAIL: could not start container '${NAME}'"
    return 1
  fi
}
```

### Stop and remove a container (idempotent)

Stops and removes a named container. No-ops if it does not exist.

```bash
stop_container() {
  local NAME="$1"

  ${CONTAINER_RUNTIME} stop "${NAME}" > /dev/null 2>&1 || true
  ${CONTAINER_RUNTIME} rm "${NAME}" > /dev/null 2>&1 || true
  echo "PASS: container '${NAME}' stopped and removed (or was not running)"
}
```

## Usage

Reference this file from a test plan:

```markdown
Follow [common/container-runtime.md](common/container-runtime.md). After completion,
`CONTAINER_RUNTIME` is set and exported. The `start_container_if_absent` and
`stop_container` helper functions are available.
```

Then use `${CONTAINER_RUNTIME}` in place of hardcoded `docker` or `podman` commands:

```bash
${CONTAINER_RUNTIME} run -d --name my-service -p 8080:8080 my-image:latest
# ...
${CONTAINER_RUNTIME} stop my-service && ${CONTAINER_RUNTIME} rm my-service
```

Or use the helper functions:

```bash
start_container_if_absent my-service -p 8080:8080 my-image:latest
# ...
stop_container my-service
```
