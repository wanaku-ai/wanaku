# WanakuCamelRoute CRD

The `WanakuCamelRoute` custom resource lets you define Apache Camel routes and MCP metadata directly in a Kubernetes CR. The operator packages the route into a service catalog and deploys it to the router automatically.

## When to use WanakuCamelRoute vs WanakuServiceCatalog

| Feature | WanakuCamelRoute | WanakuServiceCatalog |
|---|---|---|
| Route definition | Inline in CR spec | External ConfigMap (Base64 ZIP) |
| Packaging | Automatic (operator handles it) | Manual (`wanaku service package`) |
| Best for | Single-route tools/resources | Multi-service catalogs, complex setups |
| Dependencies | Not supported (use capability image) | Supported via `.dependencies.txt` |
| GitOps friendly | Yes (single YAML file) | Requires ConfigMap + CR |

Use `WanakuCamelRoute` for simple integrations where a single Camel route exposes one or more MCP tools/resources. Use `WanakuServiceCatalog` for complex multi-service catalogs or when you need custom Maven dependencies.

## Spec Fields

### `routerRef` (required)

Name of the `WanakuRouter` CR in the same namespace. The operator deploys the service catalog to this router.

```yaml
spec:
  routerRef: my-router
```

### `route` (required)

The Apache Camel route definition in YAML DSL. This is a free-form field that accepts any valid Camel YAML route structure.

```yaml
spec:
  route:
    - route:
        id: my-route
        from:
          uri: direct:wanaku
          steps:
            - to:
                uri: https://api.example.com/search
```

The route `id` must match the `routeId` referenced in the MCP tool/resource definitions.

### `mcp` (required)

MCP metadata that defines how the Camel route is exposed as tools and/or resources.

#### Tools

```yaml
spec:
  mcp:
    tools:
      - name: search-tool
        routeId: my-route
        description: Search for information
        properties:
          - name: wanaku_body
            type: string
            description: The search query
            required: true
```

#### Resources

```yaml
spec:
  mcp:
    resources:
      - name: s3-reader
        routeId: s3-route
        description: Read objects from S3
        uri: wanaku://s3-reader
        mimeType: application/octet-stream
```

### `properties` (optional)

Key-value pairs for Camel property placeholders used in the route. Use `{{placeholder}}` syntax for values that should be parameterized as templates.

```yaml
spec:
  properties:
    api.key: "{{api.key}}"
    base.url: "https://api.example.com"
```

## Status Fields

| Field | Description |
|---|---|
| `conditions` | Standard Kubernetes conditions. `Ready=True` when deployed. |
| `deployedCatalogName` | Name of the service catalog deployed to the router. |
| `registeredTools` | List of MCP tool names registered from this CR. |
| `registeredResources` | List of MCP resource names registered from this CR. |

## Examples

See the [examples directory](examples/camel-route/) for complete working examples:

- [Simple tool (Tavily search)](examples/camel-route/wanaku-camel-route-simple.yaml)
- [Complex tool (Kafka request-reply)](examples/camel-route/wanaku-camel-route-kafka.yaml)

## Troubleshooting

### "routerRef must be specified"

The `routerRef` field is missing or empty. Set it to the name of an existing `WanakuRouter` CR.

### "Referenced WanakuRouter not found"

The `WanakuRouter` CR specified by `routerRef` does not exist in the same namespace. Create the router first:

```bash
kubectl get wanakurouters -n <namespace>
```

### "Failed to deploy service catalog"

The operator could not reach the router's REST API. Check that the router pod is running and the internal service `internal-<routerRef>` is reachable.

### CR stuck without Ready condition

Check operator logs for errors:

```bash
kubectl logs -l app.kubernetes.io/name=wanaku-operator -n <namespace>
```
