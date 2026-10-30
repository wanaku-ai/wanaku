---
title: Deploy Wanaku on Kubernetes and OpenShift
description: Deploy Wanaku on Kubernetes or OpenShift with a ConfigMap, a Secret, a Deployment, and a Service, or use the Wanaku Operator for production.
---

# Deploy Wanaku on Kubernetes and OpenShift

For production installs on Kubernetes or OpenShift, use the Wanaku Operator. See [Production Deployment on Kubernetes or OpenShift](../get-started/quickstart.md#production-deployment-on-kubernetes-or-openshift). The manifests below show a plain `Deployment` for a small installation.

**ConfigMap:**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: wanaku-config
data:
  wanaku.yaml: |
    forwards:
      - name: "upstream-mcp"
        address: "http://upstream:8080/mcp"
        credential_bindings:
          discovery: upstream-token
          invocation: upstream-token
    bindings:
      - id: upstream-token
        forward_id: upstream-mcp
        origin: "http://upstream:8080"
        mechanism:
          type: bearer
        secret_refs:
          - "env:UPSTREAM_TOKEN"
        allowed_purposes:
          - discovery
          - invocation
```

The working directory of the image is `/etc/wanaku`, so the server reads `/etc/wanaku/wanaku.yaml` without a command-line argument.

**Secret:**

Each `env:` reference in `wanaku.yaml` reads an environment variable of the server process. Put the values in a Secret and pass them as environment variables:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: wanaku-secrets
type: Opaque
stringData:
  UPSTREAM_TOKEN: "<token>"
```

**Deployment:**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wanaku-server
spec:
  replicas: 1  # File snapshots support one writer. Do not share this volume across replicas.
  selector:
    matchLabels:
      app: wanaku-server
  template:
    metadata:
      labels:
        app: wanaku-server
    spec:
      securityContext:
        fsGroup: 0  # Plain Kubernetes only. Remove on OpenShift, which assigns its own fsGroup.
      containers:
      - name: wanaku
        image: quay.io/wanaku/wanaku-server:early-access
        ports:
        - name: management
          containerPort: 8080
        - name: mcp
          containerPort: 8081
        envFrom:
        - secretRef:
            name: wanaku-secrets
        volumeMounts:
        - name: config
          mountPath: /etc/wanaku
        - name: data
          mountPath: /data/registry
      volumes:
      - name: config
        configMap:
          name: wanaku-config
      - name: data
        persistentVolumeClaim:
          claimName: wanaku-registry
```

The image sets `WANAKU_PERSIST_PATH=/data/registry`, and file persistence is on by default. The Deployment does not set these values again.

The image runs as user `1001` in group `0`. A new PersistentVolume on plain Kubernetes is often owned by `root` and is not writable by group `0`. The `fsGroup: 0` setting makes Kubernetes give group `0` write access to the volume. Without it, the first registry write can fail with a permission error.

**Service:**

The Service below makes the MCP listener available inside the cluster. It does not expose the management API, because the management API has no authentication. To expose Wanaku outside the cluster, put an authenticating proxy in front of it. See [Authentication with oauth2-proxy](authentication.md).

```yaml
apiVersion: v1
kind: Service
metadata:
  name: wanaku-server
spec:
  type: ClusterIP
  selector:
    app: wanaku-server
  ports:
  - name: mcp
    port: 8081
    targetPort: mcp
```

MCP clients in the cluster use `http://wanaku-server:8081/<namespace>/mcp`. To use the management API or the admin UI from your workstation, forward the port:

```bash
kubectl port-forward deployment/wanaku-server 8080:8080
```

To use a custom pipeline, add a `pipeline.yaml` key to the ConfigMap and pass it to the server. The server does not read a pipeline file without this argument:

```yaml
        args: ["--pipeline-config", "/etc/wanaku/pipeline.yaml"]
```

## Next steps

- [Persist registry and revision state](persistence.md): learn the limits of file persistence with replicas.
- [Authentication](authentication.md): put an authenticating proxy in front of the MCP listener.
- [Wanaku configuration file](../reference/wanaku-yaml.md): write the `wanaku.yaml` for the ConfigMap.
