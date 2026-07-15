# Security Best Practices Guide

This guide covers security best practices for deploying and operating Wanaku in production environments.

## Secret Management

### Kubernetes Secrets

Store all sensitive configuration in Kubernetes Secrets rather than ConfigMaps or plain YAML.

**Example: OIDC Credentials Secret**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: wanaku-oidc-credentials
  namespace: wanaku-system
type: Opaque
stringData:
  client-id: "wanaku-service"
  client-secret: "<rotate-me-regularly>"
  token-endpoint: "https://keycloak.example.com/realms/production/"
```

**Best Practices:**

- Never commit secrets to version control
- Use `stringData` for plaintext values (auto-encoded to base64)
- Restrict secret access with RBAC:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: secret-reader
  namespace: wanaku-system
rules:
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get", "list"]
  resourceNames: ["wanaku-oidc-credentials"]
```

- Rotate credentials quarterly at a minimum
- Consider external secret managers (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault) for production

### Environment Variables

Inject secrets as environment variables in deployment manifests:

```yaml
env:
- name: CLIENT_ID
  valueFrom:
    secretKeyRef:
      name: wanaku-oidc-credentials
      key: client-id
- name: CLIENT_SECRET
  valueFrom:
    secretKeyRef:
      name: wanaku-oidc-credentials
      key: client-secret
```

### Wanaku CLI Credentials

When using `wanaku` CLI commands that require admin access:

- Use `--plain` flag only in trusted environments
- Prefer environment variables for credentials:

```bash
export WANAKU_ADMIN_USERNAME=admin
export WANAKU_ADMIN_PASSWORD=secure-password
wanaku realm create --config wanaku-config.json
```

## Network Policies for gRPC Traffic

### Default Deny Ingress

Apply a default deny policy to capability namespaces:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-ingress
  namespace: wanaku-capabilities
spec:
  podSelector: {}
  policyTypes:
  - Ingress
```

### Allow Router-to-Capability gRPC

Allow gRPC traffic (port 9190 by default) only from the router namespace:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-router-grpc
  namespace: wanaku-capabilities
spec:
  podSelector:
    matchLabels:
      app: camel-capability
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: wanaku-system
    ports:
    - protocol: TCP
      port: 9190
```

### Allow Capability-to-Router Registration

Allow capability services to register with the router (port 8080):

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-capability-registration
  namespace: wanaku-system
spec:
  podSelector:
    matchLabels:
      app: wanaku-router
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: wanaku-capabilities
    ports:
    - protocol: TCP
      port: 8080
```

### Keycloak Access

Restrict Keycloak access to the router namespace only:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-router-keycloak
  namespace: wanaku-system
spec:
  podSelector:
    matchLabels:
      app: keycloak
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: wanaku-system
    ports:
    - protocol: TCP
      port: 8080
```

## Least-Privilege Principles for Capability Services

### Service Account per Capability

Create dedicated service accounts for each capability:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: camel-integration-capability
  namespace: wanaku-capabilities
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: camel-capability-role
  namespace: wanaku-capabilities
rules:
- apiGroups: [""]
  resources: ["pods", "services", "configmaps"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["wanaku.ai"]
  resources: ["wanakurouters", "wanakucapabilities"]
  verbs: ["get", "list", "watch", "update"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: camel-capability-binding
  namespace: wanaku-capabilities
subjects:
- kind: ServiceAccount
  name: camel-integration-capability
  namespace: wanaku-capabilities
roleRef:
  kind: Role
  name: camel-capability-role
  apiGroup: rbac.authorization.k8s.io
```

### Keycloak Client Roles

Assign minimal roles in Keycloak for each capability:

1. Create dedicated client per capability (e.g., `camel-integration-capability-prod`)
2. Enable **Service Accounts** and **Client Authentication**
3. Assign only required roles:
   - `wanaku-service` role for registration
   - Specific resource roles as needed
4. Avoid realm-admin or broad roles

### Container Security Context

Run capabilities with non-root user and read-only filesystem:

```yaml
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 10001
    fsGroup: 10001
  containers:
  - name: camel-capability
    securityContext:
      allowPrivilegeEscalation: false
      readOnlyRootFilesystem: true
      capabilities:
        drop:
        - ALL
```

### Resource Limits

Prevent resource exhaustion attacks:

```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
  limits:
    memory: "512Mi"
    cpu: "500m"
```

## Audit Considerations

### Enable Audit Logging

Configure Keycloak audit logging for authentication events.

For **Quarkus-based Keycloak (v17+)**, use environment variables in your deployment:

```bash
KC_SPI_EVENTS_LISTENER_JBOSS_LOGGING_SUCCESS_EVENTS=LOGIN,REGISTER,TOKEN_REFRESH
KC_SPI_EVENTS_LISTENER_JBOSS_LOGGING_ERROR_EVENTS=LOGIN_ERROR,TOKEN_REFRESH_ERROR
```

The exact configuration depends on your Keycloak deployment method. Check the [Keycloak documentation](https://www.keycloak.org/server/configuration) for your version.

### Wanaku Router Audit

The router logs all management API operations. Configure log level in `application.properties`:

```properties
quarkus.log.category."ai.wanaku".level=INFO
quarkus.log.category."ai.wanaku.router".level=DEBUG
```

Key events logged:

- Tool/resource registration and deletion
- Namespace creation/modification
- Capability service registration/deregistration
- Authentication successes and failures

### gRPC Access Logs

Enable gRPC access logging on capability services to track tool invocations:

```properties
# In capability service application.properties
quarkus.grpc.server.access-log.enabled=true
quarkus.grpc.server.access-log.pattern=%{remote-address}i %{method}m %{path}p %{status}s %{duration}D
```

### Kubernetes Audit Policy

Deploy Kubernetes audit policy to track all Wanaku CR operations:

```yaml
apiVersion: audit.k8s.io/v1
kind: Policy
rules:
- level: Metadata
  resources:
  - group: "wanaku.ai"
    resources: ["wanakurouters", "wanakucapabilities", "wanakuservicecatalogs"]
- level: RequestResponse
  resources:
  - group: ""
    resources: ["secrets"]
    resourceNames: ["wanaku-oidc-credentials"]
```

### Log Retention

- Retain audit logs for minimum 90 days
- Ship logs to centralized logging (ELK, Loki, Splunk)
- Set up alerts for:
  - Failed authentication attempts > 5/minute
  - Unauthorized secret access
  - New capability service registrations
  - Tool/resource modifications outside change windows

## TLS/mTLS Configuration

### Router TLS

> [!IMPORTANT]
> TLS configuration via the WanakuRouter CRD (`tls.enabled`, `tls.secretName`) is **planned for a future release** but is not yet available. For now, configure TLS at the **Ingress** (Kubernetes) or **Route** (OpenShift) level that fronts the Wanaku Router.

Example OpenShift Route with TLS:

```yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: wanaku-router
spec:
  host: wanaku.example.com
  to:
    kind: Service
    name: wanaku-router
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
```

### gRPC mTLS (Future)

For enhanced service-to-service security, plan for mTLS:

- Use cert-manager for certificate issuance
- Configure gRPC with mutual TLS
- Validate peer certificates in capability services

## Compliance Checklist

| Control | Implementation | Verification |
|---------|---------------|--------------|
| Encryption at rest | Infinispan encryption + PV encryption | Check PV encryption class |
| Encryption in transit | TLS 1.2+ for all endpoints | `curl -v https://router` |
| Secret management | K8s Secrets + external vault | Audit secret access logs |
| Authentication | OIDC with Keycloak | Verify token validation |
| Authorization | Namespace isolation + RBAC | Test cross-namespace access |
| Audit logging | Keycloak + router + K8s audit | Review log samples |
| Network segmentation | NetworkPolicies per namespace | `kubectl exec` connectivity tests |
| Vulnerability scanning | Image scanning in CI/CD | Check scan reports |
| Incident response | Runbook for credential rotation | Tabletop exercise |

If you find a bug, please [report it](https://github.com/wanaku-ai/wanaku/issues).
To get in touch with the community, visit the [Wanaku project](https://github.com/wanaku-ai/wanaku).
