# Kubernetes Operator Guide

This guide covers deploying and managing Wanaku using the Kubernetes Operator. The operator automates the creation, configuration, and lifecycle management of Wanaku routers, capabilities, and service catalogs on Kubernetes and OpenShift clusters.

## Overview

The Wanaku Operator manages three custom resource definitions (CRDs):

- **WanakuRouter** — deploys and configures the MCP router gateway
- **WanakuCapability** — deploys capability services (HTTP tools, Camel integrations, etc.) and connects them to a router
- **WanakuServiceCatalog** — deploys packaged service catalogs (Camel routes + Wanaku rules) to a router

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

The operator watches for `WanakuRouter`, `WanakuCapability`, and `WanakuServiceCatalog` resources in the namespace specified by `operatorNamespace`. By default, it watches only the namespace where it's installed (current-namespace scope).

To watch all namespaces, override the environment variables during Helm install:

```shell
helm install wanaku-operator ./apps/wanaku-operator/deploy/helm/wanaku-operator \
  --namespace wanaku \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_ROUTER_NAMESPACES=JOSDK_ALL_NAMESPACES \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_CAPABILITY_NAMESPACES=JOSDK_ALL_NAMESPACES \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_SERVICE_CATALOG_NAMESPACES=JOSDK_ALL_NAMESPACES
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
| `spec.auth.authProxy` | string | No | `""` | OIDC proxy address. Use `"auto"` to enable Wanaku's built-in OIDC proxy, or set to Keycloak's address. Empty defaults to Keycloak's address. |
| `spec.auth.authRealm` | string | No | `"wanaku"` | Keycloak realm name. |
| `spec.imagePullPolicy` | string | No | `"IfNotPresent"` | Global image pull policy for all operator-managed deployments (`Always`, `IfNotPresent`, `Never`). |
| `spec.ingress.host` | string | No | `""` | Ingress hostname for Kubernetes clusters. OpenShift auto-generates Routes; set this only on vanilla Kubernetes. |
| `spec.router.image` | string | No | `quay.io/wanaku/wanaku-router-backend:latest` | Router container image. |
| `spec.router.env` | list | No | `[]` | List of `{name, value}` environment variables for the router (e.g., to set `wanaku.http.auth=none`). |
| `spec.router.imagePullPolicy` | string | No | inherits `spec.imagePullPolicy` | Override pull policy for router pod only. |

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

Then apply the CRs:

```shell
kubectl apply -f router.yaml -n wanaku
kubectl apply -f service-catalog.yaml -n wanaku
```

## Lifecycle Operations

### Checking Status

**List all Wanaku resources:**

```shell
kubectl get wanakurouter,wanakucapability,wanakuservicecatalog -n wanaku
```

**Get detailed status:**

```shell
kubectl describe wanakurouter wanaku-dev -n wanaku
kubectl describe wanakucapability wanaku-capabilities -n wanaku
kubectl describe wanakuservicecatalog my-catalogs -n wanaku
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
kubectl delete wanakucapability wanaku-capabilities -n wanaku
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
> kubectl delete crd wanakurouters.wanaku.ai wanakucapabilities.wanaku.ai wanakuservicecatalogs.wanaku.ai
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
| `app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_SERVICE_CATALOG_NAMESPACES` | string | `JOSDK_WATCH_CURRENT` | Watch scope for `WanakuServiceCatalog` resources. |

**Example: customize operator image and watch all namespaces**

```shell
helm install wanaku-operator ./apps/wanaku-operator/deploy/helm/wanaku-operator \
  --namespace wanaku \
  --set app.image=quay.io/myorg/wanaku-operator:1.0.0 \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_ROUTER_NAMESPACES=JOSDK_ALL_NAMESPACES \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_CAPABILITY_NAMESPACES=JOSDK_ALL_NAMESPACES \
  --set app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_WANAKU_SERVICE_CATALOG_NAMESPACES=JOSDK_ALL_NAMESPACES
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
oc get route -n wanaku
oc describe route wanaku-dev -n wanaku
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

## Next Steps

- **Configure authentication**: see [Keycloak Setup](usage.md#keycloak-setup-for-wanaku)
- **Create service catalogs**: see [Service Catalogs](usage.md#service-catalogs)
- **Use service templates**: see [Service Templates](service-templates.md)
- **Configure advanced settings**: see [Configuration Guide](configurations.md)
- **Browse sample CRs**: check [apps/wanaku-operator/samples](https://github.com/wanaku-ai/wanaku/tree/main/apps/wanaku-operator/samples)


## CRD Field Reference

This section provides a field-by-field reference for each Wanaku CRD.

### WanakuRouter

`WanakuRouter` deploys and configures the MCP router gateway.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `spec.image` | string | No | Container image to use. Defaults to the current release image. |
| `spec.replicas` | integer | No | Number of router replicas. Defaults to `1`. |
| `spec.resources.requests.cpu` | string | No | CPU request for the router pod (e.g., `250m`). |
| `spec.resources.requests.memory` | string | No | Memory request for the router pod (e.g., `256Mi`). |
| `spec.resources.limits.cpu` | string | No | CPU limit for the router pod (e.g., `1000m`). |
| `spec.resources.limits.memory` | string | No | Memory limit for the router pod (e.g., `512Mi`). |
| `spec.auth.enabled` | boolean | No | Enable OIDC authentication. Defaults to `false`. |
| `spec.auth.issuerUrl` | string | Cond. | OIDC issuer URL. Required when `auth.enabled` is `true`. |
| `spec.auth.clientId` | string | Cond. | OIDC client ID. Required when `auth.enabled` is `true`. |
| `spec.ingress.enabled` | boolean | No | Create an Ingress resource. Defaults to `false`. |
| `spec.ingress.host` | string | Cond. | Hostname for the Ingress. Required when `ingress.enabled` is `true`. |
| `spec.ingress.tls.enabled` | boolean | No | Enable TLS on the Ingress. Defaults to `false`. |
| `spec.ingress.tls.secretName` | string | Cond. | TLS secret name. Required when `ingress.tls.enabled` is `true`. |

**Example WanakuRouter CR:**

```yaml
apiVersion: wanaku.ai/v1alpha1
kind: WanakuRouter
metadata:
  name: wanaku-prod
  namespace: wanaku
spec:
  replicas: 2
  resources:
    requests:
      cpu: "500m"
      memory: "512Mi"
    limits:
      cpu: "2000m"
      memory: "1Gi"
  auth:
    enabled: true
    issuerUrl: https://keycloak.example.com/realms/wanaku
    clientId: wanaku-router
  ingress:
    enabled: true
    host: mcp.example.com
    tls:
      enabled: true
      secretName: mcp-tls
```

### WanakuCapability

`WanakuCapability` deploys a capability service and connects it to a router.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `spec.image` | string | No | Container image for the capability provider. |
| `spec.routerRef.name` | string | Yes | Name of the `WanakuRouter` this capability connects to. |
| `spec.routerRef.namespace` | string | No | Namespace of the router. Defaults to same namespace. |
| `spec.capabilities` | list | No | List of capability definitions to register. |
| `spec.capabilities[].name` | string | Yes | Unique name for the capability (used as tool/resource name). |
| `spec.capabilities[].type` | string | Yes | Capability type: `tool` or `resource`. |
| `spec.capabilities[].route` | string | Yes | Camel route URI that handles this capability. |
| `spec.resources.requests.cpu` | string | No | CPU request. |
| `spec.resources.requests.memory` | string | No | Memory request. |
| `spec.resources.limits.cpu` | string | No | CPU limit. |
| `spec.resources.limits.memory` | string | No | Memory limit. |

**Example WanakuCapability CR:**

```yaml
apiVersion: wanaku.ai/v1alpha1
kind: WanakuCapability
metadata:
  name: wanaku-http-tools
  namespace: wanaku
spec:
  routerRef:
    name: wanaku-prod
  capabilities:
    - name: http-get
      type: tool
      route: direct:http-get
    - name: http-post
      type: tool
      route: direct:http-post
  resources:
    requests:
      cpu: "250m"
      memory: "256Mi"
    limits:
      cpu: "1000m"
      memory: "512Mi"
```

### Troubleshooting CRD Validation Errors

Common CRD validation errors and how to resolve them:

- **`routerRef.name is required`** — Add a `spec.routerRef.name` pointing to your `WanakuRouter` name.
- - **`auth.issuerUrl is required when auth.enabled=true`** — Provide `spec.auth.issuerUrl` when enabling auth.
  - - **`Unknown field "spec.xyz"`** — Check the CRD version; newer fields may not be available in older operator versions.
    - - **`invalid resource quantity`** — Resource values like `cpu` and `memory` must use valid Kubernetes quantity notation (e.g., `250m`, `256Mi`).
     
      - To validate a CR before applying:
     
      - ```bash
        kubectl apply --dry-run=client -f my-wanaku-router.yaml
        ```
