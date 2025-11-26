# Configuration Management

This directory uses ConfigMaps for non-sensitive configuration and Kubernetes Secrets for sensitive credentials.

## ConfigMaps

There are two ConfigMaps used in this deployment:

1. **`wanaku-config`** - For router-backend configuration
2. **`wanaku-services-config`** - For tool services configuration

## Secrets

There is one secret used for sensitive credentials (mostly by the services):

1. **`wanaku-service-credentials`** - For tool services OIDC credentials

**IMPORTANT:** Never commit actual secrets to version control. Use external secret management tools like:
- [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets)
- [External Secrets Operator](https://external-secrets.io/)
- [HashiCorp Vault](https://www.vaultproject.io/)
- OpenShift's built-in secret management

## ConfigMap Variables

### wanaku-config (Router Backend)

The following environment variables are configured via the `wanaku-config` ConfigMap:

| Variable | Description | Used By |
|----------|-------------|---------|
| `AUTH_SERVER` | Authentication server URL (Keycloak/OIDC) | router-backend |
| `AUTH_PROXY` | Authentication proxy URL (typically router backend) | router-backend |
| `QUARKUS_MCP_SERVER_TRAFFIC_LOGGING_ENABLED` | Enable/disable MCP traffic logging | router-backend |

### wanaku-services-config (Tool Services)

The following environment variables are configured via the `wanaku-services-config` ConfigMap:

| Variable | Description | Used By |
|----------|-------------|---------|
| `AUTH_SERVER` | OIDC authentication server URL for services | tool-service-http |
| `WANAKU_SERVICE_REGISTRATION_URI` | Service registration URI (router backend) | tool-service-http |

### Secrets Variables

#### wanaku-service-credentials (Tool Services)

| Variable | Description | Used By |
|----------|-------------|---------|
| `QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET` | OIDC client credentials secret for services | tool-service-http |

## Usage

### Deploying to Development

```bash
kubectl apply -k deploy/openshift/kustomize/overlays/dev
```

### Deploying to Production

```bash
kubectl apply -k deploy/openshift/kustomize/overlays/prod
```

## Customizing ConfigMap Values

### For Development Environment

Edit `overlays/dev/wanaku-configmap.yaml` (router configuration):

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: wanaku-config
data:
  AUTH_SERVER: "http://your-keycloak-dev.example.com"
  AUTH_PROXY: "http://wanaku-router-backend:8080"
  QUARKUS_MCP_SERVER_TRAFFIC_LOGGING_ENABLED: "true"
```

Edit `overlays/dev/wanaku-services-config.yaml` (services configuration):

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: wanaku-services-config
data:
  AUTH_SERVER: "http://your-keycloak-dev.example.com"
  WANAKU_SERVICE_REGISTRATION_URI: "http://wanaku-router-backend:8080/"
```

Edit `overlays/dev/wanaku-secrets.yaml` (credentials - **DO NOT COMMIT REAL SECRETS**):

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: wanaku-oidc-credentials
type: Opaque
stringData:
  QUARKUS_OIDC_CREDENTIALS_SECRET: "your-dev-router-secret"
---
apiVersion: v1
kind: Secret
metadata:
  name: wanaku-service-credentials
type: Opaque
stringData:
  QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET: "your-dev-service-secret"
```

**IMPORTANT:** For production deployments, use external secret management tools instead of committing secrets to git.

## Updating Configuration in Running Cluster

After modifying ConfigMaps or Secrets, you can update them without redeploying:

```bash
# Update ConfigMaps and Secrets
kubectl apply -k deploy/openshift/kustomize/overlays/dev

# Restart deployments to pick up changes
kubectl rollout restart deployment/wanaku-router-backend
kubectl rollout restart deployment/wanaku-tool-service-http
```

**Note:** Pods automatically pick up ConfigMap changes after restart, but Secrets require a pod restart to be reflected.
## Security Best Practices

1. **Never commit real secrets to version control**
   - The secrets in this repository contain placeholder values only
   - Replace them with actual values in your deployment environment

2. **Use external secret management** for production:
   - **Sealed Secrets**: Encrypt secrets client-side before committing
   - **External Secrets Operator**: Sync secrets from external vaults
   - **HashiCorp Vault**: Enterprise secret management
   - **OpenShift Secret Management**: Use OpenShift's built-in capabilities

3. **Example using kubectl to create secrets from files** (recommended):
   ```bash
   # Create secret from literal value
   kubectl create secret generic wanaku-oidc-credentials \
     --from-literal=QUARKUS_OIDC_CREDENTIALS_SECRET=your-actual-secret \
     --dry-run=client -o yaml | kubectl apply -f -
   
   # Or from a file
   kubectl create secret generic wanaku-oidc-credentials \
     --from-file=QUARKUS_OIDC_CREDENTIALS_SECRET=./secret.txt \
     --dry-run=client -o yaml | kubectl apply -f -
   ```

4. **Use RBAC** to restrict access to secrets