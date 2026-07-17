# Kubernetes Operator Guide

This guide covers deploying and managing Wanaku using the Kubernetes Operator. The operator automates the creation, configuration, and lifecycle management of Wanaku routers, capabilities, and service catalogs on Kubernetes and OpenShift clusters.

## Overview

The Wanaku Operator manages the following custom resource definitions (CRDs):

- **WanakuRouter** — deploys and configures the MCP router gateway
- **WanakuCapability** — deploys capability services (HTTP tools, Camel integrations, etc.) and connects them to a router
- **WanakuCamelRoute** — packages inline Camel routes into service catalogs and deploys them to a router
- **WanakuServiceCatalog** — deploys packaged service catalogs (Camel routes + Wanaku rules) to a router
- **WanakuCamelCodeExecutionEngine** — deploys the Camel Code Execution Engine in-cluster or targets a remote engine endpoint

When you create these custom resources, the operator automatically provisions:

- Deployments with health probes
- Services for internal and external access
- ConfigMaps for configuration
- Secrets for OIDC credentials
- Routes (OpenShift) or Ingress (Kubernetes)
- ServiceAccounts and RBAC policies

## Prerequisites

Before installing the operator, ensure you have:

- Kubernetes 1.27+ or OpenShift 4.12+
- `kubectl` or `oc` CLI installed and configured
- `helm` CLI (version 3.x or later)
- Cluster admin or namespace admin permissions
- **Keycloak instance** (for authentication) — see [Keycloak Setup](usage.md#keycloak-setup-for-wanaku)
  - Or plan to use `wanaku.http.auth=none` environment variable for unauthenticated access (development only)

## Installation

### 1. Create a Namespace

```shell
kubectl create namespace wanaku
```

### 2. Install the Operator via Helm

```shell
helm install wanaku-operator ./apps/wanaku-operator/deploy/helm/wanaku-operator \
  --namespace wanaku \
  --set operatorNamespace=wanaku
```

The operator watches for `WanakuRouter`, `WanakuCapability`, `WanakuCamelRoute`, and `WanakuServiceCatalog` resources in the namespace specified by `operatorNamespace`. By default, it watches only the namespace where it's installed (current-namespace scope).

To watch all namespaces, override the environment variables during Helm install:

```shell
helm install wanaku-operator ./apps/wanaku-operator/deploy/helm/wanaku-operator \
  --namespace wanaku \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_ROUTER_NAMESPACES=JOSDK_ALL_NAMESPACES \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_CAPABILITY_NAMESPACES=JOSDK_ALL_NAMESPACES \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_SERVICE_CATALOG_NAMESPACES=JOSDK_ALL_NAMESPACES \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_CAMEL_CODE_EXECUTION_ENGINE_NAMESPACES=JOSDK_ALL_NAMESPACES
```

### 3. Verify the Operator

```shell
kubectl get pods -n wanaku
```

You should see the operator pod running. Check its logs to confirm startup:

```shell
kubectl logs -n wanaku -l app=wanaku-operator
```

## CRD Reference

### WanakuRouter (`wanaku.ai/v1alpha1`)

Defines a Wanaku router instance.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `spec.auth.authServer` | string | No | `""` | Keycloak server address (format: `http://address`). Leave empty if running without auth. |
| `spec.auth.authRealm` | string | No | `"wanaku"` | Keycloak realm name. |
| `spec.auth.authProxy` | string | No | `""` | OIDC proxy address. Use `"auto"` to enable the built-in OIDC proxy, or set to Keycloak's address directly. Empty inherits Keycloak's address. |
| `spec.imagePullPolicy` | string | No | `"IfNotPresent"` | Global image pull policy for all operator-managed deployments (`Always`, `IfNotPresent`, `Never`). |
| `spec.ingress.host` | string | No | `""` | Ingress hostname for Kubernetes clusters. OpenShift auto-generates Routes; set this only on vanilla Kubernetes. |
| `spec.router.image` | string | No | `quay.io/wanaku/wanaku-router-backend:latest` | Router container image. |
| `spec.router.env` | list | No | `[]` | List of `{name, value}` environment variables for the router (e.g., to set `wanaku.http.auth=none`). |
| `spec.router.imagePullPolicy` | string | No | inherits `spec.imagePullPolicy` | Override pull policy for router pod only. |

The status section reports:

| Field | Type | Description |
|-------|------|-------------|
| `status.host` | string | External hostname assigned to the router (from OpenShift Route or Ingress). |
| `status.sseEndpoint` | string | SSE stream endpoint URL. |
| `status.streamableEndpoint` | string | Streamable HTTP endpoint URL. |
| `status.conditions` | list | Standard Kubernetes condition array (each entry: `status`, `reason`, `message`, `lastTransitionTime`, `observedGeneration`). |

**Example (minimal, with Keycloak):**

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuRouter
metadata:
  name: wanaku-dev
spec:
  auth:
    authServer: http://keycloak:8080
```

> [!NOTE]
> `WanakuRouter` does not define `spec.secrets`. OIDC client secrets are configured on `WanakuCapability` resources only.

**Example (unauthenticated, development only):**

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuRouter
metadata:
  name: wanaku-dev-noauth
spec:
  router:
    env:
      - name: wanaku.http.auth
        value: none
```

### WanakuCapability (`wanaku.ai/v1alpha1`)

Defines capability services that register with a router.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `spec.auth.authServer` | string | No | `""` | Keycloak server address (same as WanakuRouter). |
| `spec.auth.authProxy` | string | No | `""` | OIDC proxy address (same as WanakuRouter). |
| `spec.auth.authRealm` | string | No | `"wanaku"` | Keycloak realm name. |
| `spec.secrets.oidcCredentialsSecret` | string | No | `""` | OIDC client secret. Required if using Keycloak. |
| `spec.imagePullPolicy` | string | No | `"IfNotPresent"` | Global image pull policy for all capabilities. |
| `spec.routerRef` | string | **Yes** | — | Name of the `WanakuRouter` CR this capability registers with. Must exist in the same namespace. |
| `spec.capabilities[].name` | string | **Yes** | — | Unique capability service name. Used as deployment/service name. |
| `spec.capabilities[].image` | string | **Yes** | — | Container image for this capability. |
| `spec.capabilities[].type` | string | No | `""` | Capability type identifier (e.g., `camel-integration-capability`). |
| `spec.capabilities[].serviceCatalog` | string | No | `""` | Service catalog name (must be deployed via `WanakuServiceCatalog` or `wanaku service deploy`). |
| `spec.capabilities[].serviceCatalogSystem` | string | No | `""` | System name within the service catalog (subdirectory inside the catalog ZIP). |
| `spec.capabilities[].env` | list | No | `[]` | List of `{name, value}` environment variables for this capability. |
| `spec.capabilities[].imagePullPolicy` | string | No | inherits `spec.imagePullPolicy` | Override pull policy for this capability only. |

**Example:**

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCapability
metadata:
  name: wanaku-capabilities
spec:
  auth:
    authServer: http://keycloak:8080
  secrets:
    oidcCredentialsSecret: wanaku-oidc-secret
  routerRef: wanaku-dev
  capabilities:
    # HTTP capability for generic HTTP tools
    - name: wanaku-http
      image: quay.io/wanaku/wanaku-tool-service-http:latest

    # Camel integration capability with service catalog reference
    - name: employee-system
      type: camel-integration-capability
      image: quay.io/wanaku/camel-integration-capability:latest
      serviceCatalog: employee-system-v2
      serviceCatalogSystem: employee-system
```

> [!NOTE]
> The operator constructs the router's internal service URL as `http://internal-{routerRef}:8080`. Capabilities use this to register themselves.

### WanakuServiceCatalog (`wanaku.ai/v1alpha1`)

Deploys packaged service catalogs (Base64-encoded ZIPs containing Camel routes and Wanaku rules) to a router via its REST API.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `spec.routerRef` | string | **Yes** | — | Name of the `WanakuRouter` CR to deploy catalogs to. Must exist in the same namespace. |
| `spec.catalogs[].name` | string | **Yes** | — | Catalog name (used as identifier in the router's catalog store). |
| `spec.catalogs[].configMapRef` | string | **Yes** | — | Name of the ConfigMap containing the Base64-encoded ZIP under key `catalog.zip`. |

**Example:**

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuServiceCatalog
metadata:
  name: my-service-catalogs
spec:
  routerRef: wanaku-dev
  catalogs:
    - name: finance-catalog
      configMapRef: finance-catalog-data
    - name: hr-catalog
      configMapRef: hr-catalog-data
```

**How to create the ConfigMap:**

```shell
# Package your service catalog (creates finance-catalog.b64)
wanaku service package --path=finance-catalog

# Create ConfigMap from the Base64-encoded file
kubectl create configmap finance-catalog-data \
  --from-file=catalog.zip=finance-catalog.b64 \
  -n wanaku
```

> [!TIP]
> See [Service Catalogs](usage.md#service-catalogs) and [Service Templates](service-templates.md) for details on creating and packaging catalogs.

### WanakuCamelCodeExecutionEngine (`wanaku.ai/v1alpha1`)

Deploys the Camel Code Execution Engine. Supports two modes: **in-cluster** (Kubernetes Deployment) and **remote** (ExternalName service pointing to an existing endpoint).

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `spec.auth.authServer` | string | No | `""` | Keycloak server address (format: `http://address`). |
| `spec.auth.authProxy` | string | No | `""` | OIDC proxy address. Use `"auto"` to enable the built-in proxy. Empty inherits `authServer`. |
| `spec.auth.authRealm` | string | No | `"wanaku"` | Keycloak realm name. |
| `spec.secrets.oidcCredentialsSecret` | string | No | `""` | OIDC client secret. |
| `spec.routerRef` | string | **Yes** | — | Name of the `WanakuRouter` CR this engine registers with. |
| `spec.deploymentMode` | string | No | `"in-cluster"` | Deployment mode: `in-cluster` or `remote`. |
| `spec.engineType` | string | No | `"camel"` | Engine type identifier. |
| `spec.languageName` | string | **Yes** | — | Language name for the engine (e.g., `yaml`, `java`). |
| `spec.image` | string | **Yes** (in-cluster) | — | Container image. Required when `deploymentMode=in-cluster`. |
| `spec.port` | int | No | `9190` | gRPC port. |
| `spec.remote.host` | string | **Yes** (remote) | — | Hostname of the remote engine. Required when `deploymentMode=remote`. |
| `spec.remote.port` | int | No | `9190` | Port of the remote engine. |
| `spec.remote.scheme` | string | No | `"http"` | URL scheme: `http` or `https`. |
| `spec.remote.path` | string | No | `""` | Optional path prefix for the remote engine URL. |
| `spec.security.componentAllowlist` | list | No | `[]` | Allowed Camel component names. |
| `spec.security.componentBlocklist` | list | No | `[]` | Blocked Camel component names. |
| `spec.security.endpointAllowlist` | list | No | `[]` | Allowed endpoint URI patterns. |
| `spec.security.endpointBlocklist` | list | No | `[]` | Blocked endpoint URI patterns. |
| `spec.security.routeAllowlist` | list | No | `[]` | Allowed route IDs. |
| `spec.security.routeBlocklist` | list | No | `[]` | Blocked route IDs. |
| `spec.dependencyCache.enabled` | bool | No | `true` | Enable dependency caching. |
| `spec.dependencyCache.strategy` | string | No | `"inmemory"` | Cache strategy: `inmemory`, `infinispan`, or `disabled`. |
| `spec.dependencyCache.cacheName` | string | No | `""` | Infinispan cache name (when strategy=infinispan). |
| `spec.dependencyCache.templateNamespace` | string | No | `""` | Namespace for template lookup. |
| `spec.dependencyCache.templatePrefix` | string | No | `""` | Prefix for template keys. |
| `spec.resources.cpuRequest` | string | No | `""` | CPU request (e.g., `"100m"`). |
| `spec.resources.memoryRequest` | string | No | `""` | Memory request (e.g., `"128Mi"`). |
| `spec.resources.cpuLimit` | string | No | `""` | CPU limit (e.g., `"500m"`). |
| `spec.resources.memoryLimit` | string | No | `""` | Memory limit (e.g., `"256Mi"`). |
| `spec.imagePullPolicy` | string | No | `"IfNotPresent"` | Image pull policy. |
| `spec.env` | list | No | `[]` | Additional environment variables. |

**Status fields** (read-only — populated by the operator):

| Field | Type | Description |
|-------|------|-------------|
| `status.deploymentState` | string | Operator reconciliation state (e.g., `REMOTE_READY`, error states). |
| `status.serviceUrl` | string | Final service URL of the engine (in-cluster) or the resolved remote URL. |
| `status.activeRoutes` | list of string | Route IDs currently running inside the engine. |
| `status.healthChecks[]` | list of object | Per-check results. Each entry: `name` (string), `status` (string), `message` (string), `timestamp` (string, ISO-8601). |
| `status.conditions` | list of object | Standard Kubernetes condition array. Each entry: `status` (string), `reason` (string), `message` (string), `lastTransitionTime` (string), `observedGeneration` (int). |

**Example (in-cluster):**

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
```

**Example (remote):**

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

> [!IMPORTANT]
> When `deploymentMode=in-cluster`, `spec.image` is required. When `deploymentMode=remote`, `spec.remote.host` is required.

### WanakuCamelRoute (`wanaku.ai/v1alpha1`)

Packages inline Camel routes and MCP metadata in a single CR, then deploys the resulting capability to a router. The reconciler builds a Base64 ZIP and pushes it via the router's REST API, and also provisions a dedicated Deployment + Service for the Camel Integration Capability instance.

> [!NOTE]
> Use `spec.route` to hold the raw Camel route definition (XML or YAML — validates as opaque JSON). Use `spec.mcp` to declare which routes become visible MCP tools and resources. See the dedicated [WanakuCamelRoute CRD guide](camel-route-crd.md) for a walkthrough with end-to-end examples.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `spec.routerRef` | string | **Yes** | — | Name of the `WanakuRouter` CR to deploy to. Must exist in the same namespace. |
| `spec.image` | string | No | `quay.io/wanaku/camel-integration-capability:latest` | Container image for the CIC sidecar created by this CR. Override to pin a specific version. |
| `spec.route` | object (opaque) | No | — | Raw Camel route definition (YAML or XML). The CRD schema sets `x-kubernetes-preserve-unknown-fields: true` so any structure passes validation. |
| `spec.mcp` | object | No | — | MCP exposure configuration. |
| `spec.mcp.tools[]` | list | No | — | Tools exposed to agents. Each entry: `name`, `routeId` (must match the Camel route `id`), `description`, `properties[]`. The inner `properties[]` fields are `name`, `type`, `description`, `required`. |
| `spec.mcp.resources[]` | list | No | — | Resources exposed to agents. Each entry: `name`, `routeId`, `description`, `uri`, `mimeType`. |
| `spec.mcp.properties` | map\<string,string\> | No | — | Template variable substitutions applied when packaging the catalog (key → value replaces `${key}` in the route). |
| `spec.properties` | map\<string,string\> | No | — | Arbitrary key/value pairs forwarded by the operator (no effect on runtime, available for downstream tooling or annotations). |

**Status fields** (read-only — populated by the operator):

| Field | Type | Description |
|-------|------|-------------|
| `status.conditions` | list of object | Standard Kubernetes condition array. |
| `status.deployedCatalogName` | string | Name of the catalog pushed to the router. |
| `status.registeredTools` | list of string | Tool names successfully registered with the router. |
| `status.registeredResources` | list of string | Resource names successfully registered with the router. |

**Shared type: `spec.mcp.tools[].properties[]`**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | **Yes** | Parameter name as seen by the agent (e.g., `employeeId`). |
| `type` | string | No | MCP tool parameter type (`string`, `int`, `boolean`, etc.). |
| `description` | string | No | Human-readable description shown to the agent. |
| `required` | bool | No | Whether the agent must supply this parameter. |

**Example — Tavily search tool:**

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCamelRoute
metadata:
  name: tavily-search
  labels:
    app: wanaku-search
spec:
  routerRef: wanaku-dev
  image: quay.io/wanaku/camel-integration-capability:0.1.3
  route: |
    - route:
        id: tavily-search
        from:
          uri: "direct:tavily-search"
        steps:
          - setHeader:
              constant: GET
              name: CamelHttpMethod
          - toD:
              uri: "https://api.tavily.com/search?query=${header.query}"
  mcp:
    tools:
      - name: tavily_search
        routeId: tavily-search
        description: "Search the web using Tavily"
        properties:
          - name: query
            type: string
            description: The search query
            required: true
  properties:
    TAVILY_API_KEY: "<from-secret>"
```

> [!IMPORTANT]
> `spec.route` is opaque to the CRD validator (no nested schema is enforced). If your YAML fails validation, suspect an encoding or indentation issue — the correct field is `spec.route`, not `spec.route.id`. Run `kubectl apply --dry-run=server` to get the precise rejection message.
>
> [!NOTE]
> The dedicated [WanakuCamelRoute CRD guide](camel-route-crd.md) provides a deeper dive on Camel endpoint patterns, header mapping, parameter passing, and packaging workflows.

## Deployment Examples

### Minimal Router + HTTP Capability

```yaml
# router.yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuRouter
metadata:
  name: wanaku-dev
spec:
  auth:
    authServer: http://keycloak:8080
---
# capabilities.yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCapability
metadata:
  name: wanaku-capabilities
spec:
  auth:
    authServer: http://keycloak:8080
  secrets:
    oidcCredentialsSecret: wanaku-oidc-secret
  routerRef: wanaku-dev
  capabilities:
    - name: wanaku-http
      image: quay.io/wanaku/wanaku-tool-service-http:latest
```

Apply:

```shell
kubectl apply -f router.yaml -n wanaku
kubectl wait wanakurouter/wanaku-dev --for=condition=Ready --timeout=120s

kubectl apply -f capabilities.yaml -n wanaku
kubectl wait wanakucapability/wanaku-capabilities --for=condition=Ready --timeout=120s
```

### Router + Service Catalog

```yaml
# router.yaml (same as above)
---
# service-catalog.yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuServiceCatalog
metadata:
  name: my-catalogs
spec:
  routerRef: wanaku-dev
  catalogs:
    - name: employee-system-v2
      configMapRef: employee-catalog-data
```

Create the ConfigMap first:

```shell
wanaku service package --path=employee-system -o employee-system.b64
kubectl create configmap employee-catalog-data --from-file=catalog.zip=employee-system.b64 -n wanaku
```

Then apply:

```shell
kubectl apply -f router.yaml -n wanaku
kubectl apply -f service-catalog.yaml -n wanaku
```

## Common Deployment Patterns

### Resource Limits for Router and Capabilities

JVM-based containers can OOMKill without explicit requests. Set resource requests and limits using `spec.router.resources.*` (router) or `spec.dependencyCache.resources.*` (CIC). For standard HTTP capabilities, pass `JAVA_OPTS` via `env`:

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuRouter
metadata:
  name: wanaku-router
spec:
  auth:
    authServer: http://keycloak:8080
  router:
    image: quay.io/wanaku/wanaku-router-backend:0.1.3
    env:
      - name: JAVA_OPTS
        value: "-Xmx512m -XX:MaxDirectMemorySize=256m"
```

For the Camel Code Execution Engine, use the dedicated `resources` block (Kubernetes-native):

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCamelCodeExecutionEngine
metadata:
  name: code-engine
spec:
  routerRef: wanaku-router
  deploymentMode: in-cluster
  languageName: yaml
  image: quay.io/wanaku/camel-code-execution-engine:latest
  resources:
    cpuRequest: "200m"
    memoryRequest: "512Mi"
    cpuLimit: "1"
    memoryLimit: "1Gi"
```

> [!TIP]
> Setting values in both Quarkus `quarkus.kubernetes.resources` (application config) and K8s resource requests (environment variable or CR field) is redundant — the operator uses the CR fields you supply. The operator constructs the Deployment's `resources.requests` and `resources.limits` from these values; if both are empty, the Deployment has no resource constraints (Kubernetes uses the namespace `LimitRange`, if defined).

### High-Availability (HA) Replicas

The operator currently creates Deployments with a single replica. For HA you must manually scale after creation:

```shell
kubectl scale deployment wanaku-dev --replicas=2 -n wanaku
kubectl scale deployment wanaku-http --replicas=2 -n wanaku
```

> [!NOTE]
> Scaled replicas share the same `Service`, so capabilities discover routers via the ClusterIP. The operator does not yet reconcile replica counts from a CR field — plan to manage replication in your GitOps pipeline until this feature is added.

### Referencing Secrets and Configuring OIDC Credentials

The `spec.secrets.oidcCredentialsSecret` field references a Kubernetes `Secret` by name. The Secret must live in the same namespace as the `WanakuCapability` or `WanakuCamelCodeExecutionEngine`:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: wanaku-oidc-secret
  namespace: wanaku
type: Opaque
data:
  client-secret: <base64-of-your-keycloak-client-secret>
```

Then reference it:

```yaml
spec:
  secrets:
    oidcCredentialsSecret: wanaku-oidc-secret
```

> [!IMPORTANT]
> The `client-secret` key in the Secret **must** be named `client-secret` (lowercase, hyphenated). The operator reads this key verbatim — different names cause a reconciliation error.

### Annotations on Operator-Managed Resources

`spec.annotations` (top-level on the CR) passes arbitrary key/value pairs to the operator-managed Deployment and Service. Use this to add sidecar injection markers, prometheus scraping annotations, or pod security labels:

```yaml
spec:
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
    sidecar.istio.io/inject: "true"
```

### DeploymentMode for the Code Execution Engine (in-cluster vs remote)

The `WanakuCamelCodeExecutionEngine` CR supports two modes:

- **`in-cluster` (default):** the operator provisions a K8s Deployment and Service from `spec.image`.
- **`remote`:** the operator provisions an `ExternalName` Service pointing to the existing endpoint described in `spec.remote`. No Deployment is created.

Use `remote` when your code engine is already deployed (e.g., a managed Kafka Connect cluster, a separate Camel runtime, or legacy VMs):

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCamelCodeExecutionEngine
metadata:
  name: external-yaml-engine
spec:
  routerRef: wanaku-dev
  deploymentMode: remote
  engineType: camel
  languageName: yaml
  remote:
    host: camel-engine.internal.example.com
    port: 9190
    scheme: http
    path: /mcp
```

## Lifecycle Operations

### Checking Status

**List all Wanaku resources:**

```shell
kubectl get wanakurouter,wanakucapability,wanakucamelroute,wanakuservicecatalog,wanakucamelcodeexecutionengine -n wanaku
```

**Get detailed status:**

```shell
kubectl describe wanakurouter wanaku-dev -n wanaku
kubectl describe wanakucapability wanaku-capabilities -n wanaku
kubectl describe wanakuservicecatalog my-catalogs -n wanaku
kubectl describe wanakucamelcodeexecutionengine my-code-engine -n wanaku
```

The status section shows:

- `Ready` condition (true/false)
- Deployed capabilities or catalogs
- Error messages (if reconciliation failed)

**Check router logs:**

```shell
kubectl logs -n wanaku deployment/wanaku-dev
```

**Check capability logs:**

```shell
kubectl logs -n wanaku deployment/wanaku-http
```

### Updating Resources

Edit the custom resource directly:

```shell
kubectl edit wanakurouter wanaku-dev -n wanaku
```

Or update your YAML and reapply:

```shell
# Edit router.yaml locally
kubectl apply -f router.yaml -n wanaku
```

The operator detects changes and reconciles automatically. For deployments, this triggers a rolling update.

**Example: change router image tag**

```yaml
spec:
  router:
    image: quay.io/wanaku/wanaku-router-backend:1.0.0
```

### Removing Resources

Delete the custom resources in reverse order (catalogs first, then capabilities, then router):

```shell
kubectl delete wanakuservicecatalog my-catalogs -n wanaku
kubectl delete wanakucamelroute my-camel-route -n wanaku
kubectl delete wanakucapability wanaku-capabilities -n wanaku
kubectl delete wanakucamelcodeexecutionengine my-code-engine -n wanaku
kubectl delete wanakurouter wanaku-dev -n wanaku
```

The operator cleans up all managed Kubernetes objects (deployments, services, configmaps, etc.).

### Uninstalling the Operator

```shell
helm uninstall wanaku-operator -n wanaku
```

> [!WARNING]
> Uninstalling the operator does **not** delete the CRDs or existing custom resources. To fully clean up:
>
> ```shell
> kubectl delete wanakurouter --all -n wanaku
> kubectl delete wanakucapability --all -n wanaku
> kubectl delete wanakuservicecatalog --all -n wanaku
> kubectl delete wanakucamelcodeexecutionengine --all -n wanaku
> kubectl delete crd wanakurouters.wanaku.ai wanakucapabilities.wanaku.ai wanakucamelroutes.wanaku.ai wanakuservicecatalogs.wanaku.ai wanakucamelcodeexecutionengines.wanaku.ai
> ```

## Running Without Authentication

For development or testing, you can disable authentication by setting the `wanaku.http.auth` environment variable on the router:

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuRouter
metadata:
  name: wanaku-dev-noauth
spec:
  router:
    env:
      - name: wanaku.http.auth
        value: none
```

With this setting:

- You don't need a Keycloak instance
- Leave `spec.auth.authServer` empty
- Capabilities don't require OIDC credentials either (the router accepts unauthenticated registration)

> [!IMPORTANT]
> **Never use `wanaku.http.auth=none` in production.** This disables all authentication and authorization checks.

## Helm Chart Values

The operator's Helm chart exposes these key configuration options in `values.yaml`:

| Value | Type | Default | Description |
|-------|------|---------|-------------|
| `app.image` | string | `quay.io/wanaku/wanaku-operator:latest` | Operator container image. |
| `app.imagePullPolicy` | string | `IfNotPresent` | Image pull policy for operator pod. |
| `app.ports.http` | int | `8081` | HTTP port for operator's health and metrics endpoints. |
| `app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_ROUTER_NAMESPACES` | string | `JOSDK_WATCH_CURRENT` | Watch scope for `WanakuRouter` resources (`JOSDK_WATCH_CURRENT` = current namespace only, `JOSDK_ALL_NAMESPACES` = cluster-wide). |
| `app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_CAPABILITY_NAMESPACES` | string | `JOSDK_WATCH_CURRENT` | Watch scope for `WanakuCapability` resources. |
| `app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_CAMEL_ROUTE_NAMESPACES` | string | `JOSDK_WATCH_CURRENT` | Watch scope for `WanakuCamelRoute` resources. |
| `app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_SERVICE_CATALOG_NAMESPACES` | string | `JOSDK_WATCH_CURRENT` | Watch scope for `WanakuServiceCatalog` resources. |
| `app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_CAMEL_CODE_EXECUTION_ENGINE_NAMESPACES` | string | `JOSDK_WATCH_CURRENT` | Watch scope for `WanakuCamelCodeExecutionEngine` resources. |

**Example: customize operator image and watch all namespaces**

```shell
helm install wanaku-operator ./apps/wanaku-operator/deploy/helm/wanaku-operator \
  --namespace wanaku \
  --set app.image=quay.io/myorg/wanaku-operator:1.0.0 \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_ROUTER_NAMESPACES=JOSDK_ALL_NAMESPACES \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_CAPABILITY_NAMESPACES=JOSDK_ALL_NAMESPACES \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_CAMEL_ROUTE_NAMESPACES=JOSDK_ALL_NAMESPACES \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_SERVICE_CATALOG_NAMESPACES=JOSDK_ALL_NAMESPACES \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_CAMEL_CODE_EXECUTION_ENGINE_NAMESPACES=JOSDK_ALL_NAMESPACES
```

## Troubleshooting

### Operator pod not starting

**Check pod status:**

```shell
kubectl get pods -n wanaku
kubectl describe pod -n wanaku -l app=wanaku-operator
```

Common causes:

- **Image pull errors**: verify the operator image exists and is accessible
- **RBAC issues**: ensure the ServiceAccount has correct ClusterRole bindings (check Helm chart RBAC templates)
- **Resource limits**: the pod may be pending due to insufficient cluster resources

**Check operator logs:**

```shell
kubectl logs -n wanaku -l app=wanaku-operator
```

Look for startup errors or Java exceptions.

### CRDs not reconciling

**Check custom resource status:**

```shell
kubectl describe wanakurouter wanaku-dev -n wanaku
```

Look at the `Status.Conditions` section for error messages.

**Common issues:**

1. **Router not ready**: the `WanakuRouter` CR is not in `Ready` state
   - Check router deployment logs: `kubectl logs -n wanaku deployment/wanaku-dev`
   - Verify Keycloak is reachable if using authentication
   - Verify OIDC secret exists: `kubectl get secret wanaku-oidc-secret -n wanaku`

2. **Capability deployment stuck**: check if `routerRef` matches an existing `WanakuRouter`

   ```shell
   kubectl get wanakurouter -n wanaku
   ```

3. **Service catalog deployment failed**: verify the ConfigMap exists and has key `catalog.zip`

   ```shell
   kubectl get configmap finance-catalog-data -n wanaku -o yaml
   ```

**Operator reconciliation logs:**

```shell
kubectl logs -n wanaku -l app=wanaku-operator --follow
```

Watch for errors during CR reconciliation.

### Capabilities not connecting to router

**Check capability pod logs:**

```shell
kubectl logs -n wanaku deployment/wanaku-http
```

Look for errors like:

- `Connection refused` — router service not reachable
- `401 Unauthorized` — OIDC credentials mismatch
- `503 Service Unavailable` — router not ready

**Verify internal service exists:**

```shell
kubectl get svc -n wanaku
```

The operator creates a service named `internal-{routerRef}` for capabilities to connect to.

**Verify OIDC credentials match:**

All capabilities must use the same `oidcCredentialsSecret`. Check the secret value:

```shell
kubectl get secret wanaku-oidc-secret -n wanaku -o jsonpath='{.data.client-secret}' | base64 -d
```

Compare this to the Keycloak client secret (should match exactly).

### Router or capability pods crash-looping

**Check pod events:**

```shell
kubectl describe pod -n wanaku <pod-name>
```

**Check application logs:**

```shell
kubectl logs -n wanaku <pod-name>
```

Common causes:

- **Configuration errors**: invalid environment variables or missing secrets
- **Startup probe failures**: pod not becoming healthy in time (increase `initialDelaySeconds` if needed)
- **Java heap issues**: increase memory limits in the CR (capabilities and router use JVM-based containers)

**Example: increase router memory limit**

```yaml
spec:
  router:
    env:
      - name: JAVA_OPTS
        value: "-Xmx1g"
```

(This requires the router image to honor `JAVA_OPTS`. Check the container entrypoint.)

### Ingress/Route not working

**OpenShift (Routes):**

```shell
kubectl get route -n wanaku
kubectl describe route wanaku-dev -n wanaku
```

Routes are auto-generated. If missing, check if the WanakuRouter has `spec.ingress.host` set (it should be empty on OpenShift).

**Kubernetes (Ingress):**

```shell
kubectl get ingress -n wanaku
kubectl describe ingress wanaku-dev -n wanaku
```

On vanilla Kubernetes, you **must** set `spec.ingress.host` in the `WanakuRouter` CR:

```yaml
spec:
  ingress:
    host: wanaku.example.com
```

Verify your Ingress controller is installed and configured (e.g., NGINX, Traefik).

### How to manually inspect operator-managed resources

The operator creates these resources for each custom resource. You can inspect them directly:

**For `WanakuRouter wanaku-dev`:**

```shell
kubectl get deployment wanaku-dev -n wanaku
kubectl get service wanaku-dev -n wanaku
kubectl get service internal-wanaku-dev -n wanaku
kubectl get configmap wanaku-dev-config -n wanaku
```

**For `WanakuCapability wanaku-capabilities` with capability `wanaku-http`:**

```shell
kubectl get deployment wanaku-http -n wanaku
kubectl get service wanaku-http -n wanaku
kubectl get configmap wanaku-http-config -n wanaku
```

> [!WARNING]
> Do **not** manually edit operator-managed resources (deployments, services, etc.). The operator reconciles them continuously and will overwrite manual changes. Always edit the custom resource instead.

### Troubleshooting CRD validation errors

When `kubectl apply` rejects a CR before the operator ever sees it, the problem is purely in the YAML shape. Common culprits and fixes:

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Forbidden: spec.router: Invalid value` | `spec.router` or `spec.capabilities` passed at the top level of the wrong CR kind | `router` belongs on `WanakuRouter`; `capabilities` belongs on `WanakuCapability`. Do not mix them. |
| `no matches for kind "WanakuRouter" in version "wanaku.ai/v1alpha1"` | CRDs not installed | Re-run `helm install` (or `kubectl apply` the CRD YAMLs) and verify with `kubectl get crd wanakurouters.wanaku.ai`. |
| `Forbidden: updates to statefulset spec are not allowed` | Editing an operator-managed Deployment directly | Delete and re-create via `kubectl apply -f <your-cr>`. The operator rebuilds the Deployment from the CR. |
| `Object field is immutable` (e.g. `spec.auth`) on an existing CR | Auth block changed after first reconcile | Delete and recreate the CR (or delete the operator-managed Deployment so it gets rebuilt). |
| `unable to recognize ""` or `error converting YAML` — blank value | Trailing space on a key, or scalar placed where object is expected (e.g. `authProxy: "auto" # with a trailing tab`) | Run the file through `yamllint` and re-apply with `--dry-run=server`. |
| CRD accepted but CR stays in `NotReady` indefinitely | Router not reachable / OIDC secret wrong / `routerRef` typo | Run `kubectl describe <cr> -n <ns>`; check operator logs (`kubectl logs -l app=wanaku-operator -n <ns>`); confirm the referenced router exists and is in `Ready` condition. |

**Quick diagnosis loop:**

```shell
kubectl apply --dry-run=server -f my-cr.yaml -n wanaku
kubectl describe wanakurouter wanaku-router -n wanaku
kubectl logs -l app=wanaku-operator -n wanaku --tail=100
```

## Next Steps

- **Configure authentication**: see [Keycloak Setup](usage.md#keycloak-setup-for-wanaku)
- **Create service catalogs**: see [Service Catalogs](usage.md#service-catalogs)
- **Use service templates**: see [Service Templates](service-templates.md)
- **Configure advanced settings**: see [Configuration Guide](configurations.md)
- **Browse sample CRs**: check [apps/wanaku-operator/samples](https://github.com/wanaku-ai/wanaku/tree/main/apps/wanaku-operator/samples)
