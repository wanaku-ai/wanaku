# Common: Wait for Resource Deletion

Reusable helper that polls for Kubernetes resource deletion instead of using a fixed sleep.

## Prerequisites

- `oc` CLI installed and logged in
- `WANAKU_NAMESPACE` environment variable set

## Helper function

Define this function before use:

```bash
wait_for_deletion() {
  local RESOURCE_TYPE="$1"
  local RESOURCE_NAME="$2"
  local NAMESPACE="$3"
  local TIMEOUT="${4:-60}"
  local INTERVAL=3
  local ELAPSED=0

  while oc get "${RESOURCE_TYPE}" "${RESOURCE_NAME}" -n "${NAMESPACE}" > /dev/null 2>&1; do
    if [ "${ELAPSED}" -ge "${TIMEOUT}" ]; then
      echo "FAIL: ${RESOURCE_TYPE}/${RESOURCE_NAME} still exists after ${TIMEOUT}s"
      return 1
    fi
    sleep ${INTERVAL}
    ELAPSED=$((ELAPSED + INTERVAL))
  done
  echo "PASS: ${RESOURCE_TYPE}/${RESOURCE_NAME} deleted (${ELAPSED}s)"
  return 0
}
```

## Usage

```bash
wait_for_deletion deployment my-deployment "${WANAKU_NAMESPACE}" 60
wait_for_deletion route my-route "${WANAKU_NAMESPACE}" 30
```
