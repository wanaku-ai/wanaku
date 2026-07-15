# WanakuCamelCodeExecutionEngine CRD

The `WanakuCamelCodeExecutionEngine` custom resource deploys and manages the Camel Code Execution Engine. It supports two deployment modes: **in-cluster** (the operator creates a Kubernetes Deployment) and **remote** (the operator creates an ExternalName service pointing to an existing engine endpoint).

## When to use in-cluster vs remote

| Feature | in-cluster | remote |
|---------|-----------|--------|
| Deployment | Operator creates Deployment | Operator creates ExternalName Service |
| Image | Required (`spec.image`) | Not used |
| Remote host | Not used | Required (`spec.remote.host`) |
| Lifecycle | Operator manages pod restarts/scaling | Operator only manages service registration |
| Use case | Self-contained engine in the cluster | External engine (different cluster, managed service, etc.) |

## Spec Fields

### `routerRef` (required)

Name of the `WanakuRouter` CR in the same namespace. The engine registers with this router.

```yaml
spec:
  routerRef: my-router
```

### `languageName` (required)

Language identifier for the code execution engine (e.g., `yaml`, `java`).

```yaml
spec:
  languageName: yaml
```

### `engineType` (optional, default: `"camel"`)

Engine type identifier. Currently only `"camel"` is supported.

```yaml
spec:
  engineType: camel
```

### `deploymentMode` (optional, default: `"in-cluster"`)

Deployment mode: `"in-cluster"` or `"remote"`.

```yaml
spec:
  deploymentMode: in-cluster
```

### `image` (required for in-cluster)

Container image for the code execution engine. Only used when `deploymentMode=in-cluster`.

```yaml
spec:
  image: quay.io/wanaku/camel-code-execution-engine:latest
```

### `port` (optional, default: 9190)

gRPC port for the engine.

```yaml
spec:
  port: 9190
```

### `remote` (required for remote mode)

Remote engine connection settings. Only used when `deploymentMode=remote`.

```yaml
spec:
  remote:
    host: engine.example.com
    port: 9443
    scheme: https
    path: /mcp
```

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `host` | Yes (remote) | — | Hostname of the remote engine |
| `port` | No | `9190` | Port of the remote engine |
| `scheme` | No | `"http"` | URL scheme: `http` or `https` |
| `path` | No | `""` | Path prefix (normalized to start with `/`) |

### `security` (optional)

Security controls for the engine. Allowlists and blocklists cannot contain overlapping entries.

```yaml
spec:
  security:
    componentAllowlist:
      - direct
      - log
    componentBlocklist:
      - netty
    endpointAllowlist:
      - "http:*"
    endpointBlocklist:
      - "netty4:*"
    routeAllowlist:
      - my-route
    routeBlocklist:
      - dangerous-route
```

### `dependencyCache` (optional)

Dependency caching configuration.

```yaml
spec:
  dependencyCache:
    enabled: true
    strategy: infinispan
    cacheName: code-cache
    templateNamespace: wanaku
    templatePrefix: templates/
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `enabled` | bool | `true` | Enable dependency caching |
| `strategy` | string | `"inmemory"` | Cache strategy: `inmemory`, `infinispan`, or `disabled` |
| `cacheName` | string | `""` | Infinispan cache name (when strategy=infinispan) |
| `templateNamespace` | string | `""` | Namespace for template lookup |
| `templatePrefix` | string | `""` | Prefix for template keys |

### `resources` (optional)

Resource requests and limits for the in-cluster deployment.

```yaml
spec:
  resources:
    cpuRequest: "100m"
    memoryRequest: "128Mi"
    cpuLimit: "500m"
    memoryLimit: "256Mi"
```

### `auth` (optional)

Authentication configuration shared with the router.

```yaml
spec:
  auth:
    authServer: http://keycloak:8080
    authRealm: wanaku
```

### `secrets` (optional)

OIDC credentials secret name.

```yaml
spec:
  secrets:
    oidcCredentialsSecret: wanaku-oidc-secret
```

### `env` (optional)

Additional environment variables.

```yaml
spec:
  env:
    - name: MY_VAR
      value: my-value
```

## Status Fields

| Field | Description |
|-------|-------------|
| `deploymentState` | `"IN_CLUSTER_READY"` or `"REMOTE_READY"` |
| `serviceUrl` | Resolved service URL for the engine |
| `activeRoutes` | List of active route identifiers (format: `{engineType}/{languageName}`) |
| `healthChecks` | Health check results with name, status, message, and timestamp |
| `conditions` | Standard Kubernetes conditions. `Ready=True` when the engine is deployed. |

## Examples

### In-cluster example

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCamelCodeExecutionEngine
metadata:
  name: my-code-engine
spec:
  routerRef: wanaku-dev
  languageName: yaml
  image: quay.io/wanaku/camel-code-execution-engine:latest
  deploymentMode: in-cluster
  security:
    componentAllowlist:
      - direct
      - log
    componentBlocklist:
      - netty
```

### Remote example

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCamelCodeExecutionEngine
metadata:
  name: my-remote-engine
spec:
  routerRef: wanaku-dev
  languageName: yaml
  deploymentMode: remote
  remote:
    host: engine.example.com
    port: 9443
    scheme: https
    path: /mcp
```

### With Infinispan cache

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCamelCodeExecutionEngine
metadata:
  name: my-cached-engine
spec:
  routerRef: wanaku-dev
  languageName: java
  image: quay.io/wanaku/camel-code-execution-engine:latest
  dependencyCache:
    enabled: true
    strategy: infinispan
    cacheName: code-cache
    templateNamespace: wanaku
    templatePrefix: templates/
  resources:
    cpuRequest: "200m"
    memoryRequest: "256Mi"
    cpuLimit: "1000m"
    memoryLimit: "512Mi"
```

## Troubleshooting

### "routerRef must be specified"

The `routerRef` field is missing or empty. Set it to the name of an existing `WanakuRouter` CR.

### "Referenced WanakuRouter not found"

The `WanakuRouter` CR specified by `routerRef` does not exist in the same namespace. Create the router first:

```bash
kubectl get wanakurouters -n <namespace>
```

### "image must be specified when deploymentMode=in-cluster"

When using `deploymentMode=in-cluster`, you must provide a valid container image in `spec.image`.

### "remote.host must be specified when deploymentMode=remote"

When using `deploymentMode=remote`, you must provide the target host in `spec.remote.host`.

### "Component allowlist and blocklist cannot contain the same entries"

The `spec.security.componentAllowlist` and `spec.security.componentBlocklist` (or endpoint/route equivalents) share at least one entry. Remove the overlapping entry from one of the lists.

### CR stuck without Ready condition

Check operator logs for validation errors:

```bash
kubectl logs -l app.kubernetes.io/name=wanaku-operator -n <namespace>
```
