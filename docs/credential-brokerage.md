# Credential Brokerage

Agents that call tools through Wanaku never see the credentials those tools need. An upstream API key, a service-account bearer token, a Basic-auth password — none of it crosses the agent boundary. Wanaku resolves the credential, injects it into the outbound request, and hands the agent only the result. This is credential brokerage.

The design goal is narrow and deliberate: an agent (or a compromised prompt) should never be able to name a secret, choose where a credential goes, or read one back out of a response. Every credential is scoped to one forward, one upstream origin, and a declared purpose. Every brokerage attempt is authorized before a secret is touched. Every path fails closed.

## How It Works

Wanaku resolves and injects a credential only after governance has already authorized the action. The flow looks like this:

1. Governance (action policies, evaluators, identity checks) allows the request.
2. The credential broker checks the binding against the request scope: the correct forward, the exact upstream origin, the requested purpose, and any configured restrictions.
3. On success, the broker resolves the secret reference just in time, through a resolver plugin.
4. The broker builds the credential header and injects it into the outbound request.
5. The broker emits a redacted audit record. No secret value or secret reference path appears in the record.

If any step in this chain is unavailable or rejects the request, Wanaku injects no credential and denies the call. There is no partial success. A forward that has no credential binding configured for a purpose is unaffected — it forwards the request unauthenticated, as before.

## Credential Bindings

A `CredentialBinding` is a top-level resource, owned by exactly one forward. It ties a forward to an exact upstream origin, an injection mechanism, and one or more opaque secret references.

| Field | Type | Description |
| --- | --- | --- |
| `id` | string | Stable, non-secret binding identifier. |
| `forwardId` | string | The stable identifier of the owning forward. This is the forward name. |
| `origin` | string | The exact normalized upstream origin: scheme, host, and explicit port (for example `https://api.example.com:443`). Normalization lower-cases the scheme and host and makes the port explicit. |
| `mechanism` | object | The outbound injection mechanism. See [Injection Mechanisms](#injection-mechanisms). |
| `secretRefs` | array of strings | Opaque secret references in `scheme:path` form (for example `env:API_TOKEN`). A binding never stores an inline secret value. |
| `allowedPurposes` | array of strings | The purposes this binding may serve: `discovery`, `invocation`, or both. |
| `restrictions` | object, optional | Additional scope restrictions. See [Scope and Restrictions](#scope-and-restrictions). |
| `cache` | object, optional | Cache rules for resolved credentials. See [Caching and Invalidation](#caching-and-invalidation). |
| `revision` | integer | A monotonic counter. Any change to the binding must increment it. |

You author bindings in the `wanaku.yaml` configuration file, under the top-level `bindings` key. See [Configuring Credentials in wanaku.yaml](#configuring-credentials-in-wanakuyaml). The following binding injects a Bearer token that is read from the `API_TOKEN` environment variable:

```yaml
bindings:
  - id: b1
    forwardId: upstream-mcp
    origin: "https://api.example.com:443"
    mechanism:
      type: bearer
    secretRefs:
      - "env:API_TOKEN"
    allowedPurposes:
      - invocation
    restrictions:
      namespaces:
        - finance
    cache:
      maxTtlSeconds: 3600
    revision: 1
```

> [!NOTE]
> The `restrictions` object uses snake_case keys (`governed_items`, not `governedItems`), unlike the top-level binding fields, which use camelCase. Use the exact key when you author or read a binding.

## Purposes

A credential purpose declares what the credential is for. Wanaku keeps two purposes distinct in policy checks, resolution context, audit records, and cache keys:

- **`discovery`** — authenticated forward discovery and refresh (the upstream calls Wanaku makes to learn a forward's tools, resources, and prompts).
- **`invocation`** — authenticated tool calls, resource reads, and prompt retrieval.

A binding declares which purposes it may serve through `allowedPurposes`. A binding scoped only to `discovery` cannot be used to authenticate a tool call, and vice versa.

## Attaching a Binding to a Forward

A forward references its bindings through the `credentialBindings` field: a map from purpose to binding id. You configure the forward in the same `wanaku.yaml` file, under the top-level `forwards` key.

```yaml
forwards:
  - name: upstream-mcp
    address: "https://api.example.com/mcp"
    namespace: finance
    credentialBindings:
      discovery: b-discovery
      invocation: b1
```

The `credentialBindings` map holds only the binding id, not the binding itself. You must also declare each referenced binding under the `bindings` key. See [Configuring Credentials in wanaku.yaml](#configuring-credentials-in-wanakuyaml).

Tools, resources, and prompts carry no secret or binding reference of their own. They inherit the invocation binding through their `forwardId`. This keeps the credential surface in one place: change the binding on the forward, and every tool, resource, and prompt that forward exposes picks up the change on its next use.

> [!NOTE]
> The admin UI forward form shows a binding selector for each purpose. The selector lists only the bindings that this forward owns. A binding is owned by one forward, so its `forwardId` must equal the forward name. Type the exact forward name in the form. If no binding matches the forward name and the purpose, the selector shows an empty state. Author the binding in `wanaku.yaml` with a `forwardId` that equals the forward name.

## Configuring Credentials in wanaku.yaml

You author forwards and their credential bindings in the `wanaku.yaml` configuration file. Wanaku reads the top-level `forwards` and `bindings` keys at startup. A binding is a separate top-level resource, so you declare it under `bindings` and then reference it by id from a forward's `credentialBindings` map.

The following example configures one forward and the two bindings it uses. One binding serves discovery. The other binding serves invocation.

```yaml
forwards:
  - name: upstream-mcp
    address: "https://api.example.com/mcp"
    namespace: finance
    credentialBindings:
      discovery: b-discovery
      invocation: b1

bindings:
  - id: b-discovery
    forwardId: upstream-mcp
    origin: "https://api.example.com:443"
    mechanism:
      type: bearer
    secretRefs:
      - "env:DISCOVERY_TOKEN"
    allowedPurposes:
      - discovery
    revision: 1

  - id: b1
    forwardId: upstream-mcp
    origin: "https://api.example.com:443"
    mechanism:
      type: bearer
    secretRefs:
      - "env:API_TOKEN"
    allowedPurposes:
      - invocation
    revision: 1
```

Wanaku applies these rules when it loads the configuration:

- Wanaku registers every binding before it runs forward discovery. A forward that references a discovery binding needs the binding to exist first. A missing discovery binding makes the discovery call fail closed.
- Wanaku validates each binding before it registers the binding. A binding that fails validation is logged and skipped. The rest of the configuration still loads. Bearer and named-header mechanisms require exactly one secret reference. The Basic mechanism requires exactly two secret references.
- The `origin` value must be the exact normalized origin: scheme, host, and explicit port. Use `https://api.example.com:443`, not `https://api.example.com`. The broker compares the binding origin to the normalized forward address, and an unnormalized origin does not match.
- The `forwardId` of a binding must equal the `name` of the forward that references it. The broker rejects a binding that a different forward tries to use.

Start the server with your configuration file:

```bash
cargo run -- --wanaku-config wanaku.yaml
```

> [!NOTE]
> The management API does not create bindings. The API for bindings is read-only. You author bindings in `wanaku.yaml`. Wanaku also restores bindings that were persisted in an earlier run from the registry snapshot.

> [!WARNING]
> Configuration is additive over the persisted snapshot. Wanaku restores the snapshot first, then applies the `bindings` from `wanaku.yaml` on top, matched by id. If you delete a binding from `wanaku.yaml` on a deployment that uses persistence, Wanaku still restores that binding from the snapshot on the next start. To remove a binding on a persistent deployment, you must also remove it from the registry snapshot. On a deployment without persistence, the configuration is the full source of truth.

## Injection Mechanisms

Wanaku supports a deliberately small set of injection mechanisms. There is no query-parameter, URL-path, request-body, or arbitrary template injection — a mechanism can never place a credential somewhere a caller could observe or redirect.

| Mechanism | JSON `type` | Secrets required | Result |
| --- | --- | --- | --- |
| Bearer | `bearer` | 1 | `Authorization: Bearer <token>` |
| Named header | `named_header` | 1 | A static header you name (for example `X-Api-Key: <value>`) |
| Basic | `basic` | 2 (username, then password) | `Authorization: Basic <base64(username:password)>` |

A named header cannot use a hop-by-hop or routing-sensitive header name. Wanaku rejects `connection`, `keep-alive`, `proxy-authenticate`, `proxy-connection`, `te`, `trailer`, `transfer-encoding`, `upgrade`, `host`, `content-length`, `forwarded`, `x-forwarded-for`, `x-forwarded-host`, and `x-forwarded-proto`.

Wanaku also rejects the request outright if a client-forwarded header collides with a header the mechanism manages. For example, if the mechanism is Bearer and the client already sends an `Authorization` header, Wanaku denies the request instead of overwriting or merging headers. This prevents a caller from observing or influencing a broker-managed credential.

## Secret Resolvers

A secret reference has the form `scheme:path`, for example `env:API_TOKEN`. The scheme selects a resolver; the path is resolver-specific and is never derived from request data — only an operator-configured binding can select it.

Wanaku registers one resolver by default: `env`. It reads the named process environment variable.

The `env` resolver:

- Accepts only plausible environment variable names — ASCII letters, digits, and underscores. Anything else is rejected before Wanaku touches the environment.
- Supports an optional allowlist that further restricts which variable names it may read.
- Fails closed when the variable is missing or empty.

An unknown or unregistered resolver scheme also fails closed — Wanaku denies the request rather than skip the credential.

## Scope and Restrictions

A binding's `origin` must match the resolved upstream address exactly: same scheme, same host, same port. There is no wildcard matching and no subdomain matching. If a forward's address changes to a different host or port, its bindings stop matching until an operator updates them.

Beyond the origin, a binding can carry `restrictions`, four independent dimensions:

| Dimension | JSON key | Restricts use to |
| --- | --- | --- |
| Namespaces | `namespaces` | The listed namespaces |
| Governed items | `governed_items` | The listed governed item identifiers (tools, resources, prompts) |
| Operations | `operations` | The listed MCP operations |
| Identities | `identities` | The listed authenticated actor or workload identities |

An empty array on any dimension means no restriction on that dimension. A non-empty array means the request's value for that dimension must appear in the list, or the broker denies the request.

## Caching and Invalidation

The broker caches a resolved credential only when it has an expiry: either the resolver supplies one, or the binding sets `cache.maxTtlSeconds`. When both are present, Wanaku uses the earlier of the two. When neither is present, Wanaku resolves the secret fresh on every use and never caches it.

The cache key includes the binding id, the binding revision, the resolver scheme, the secret reference, the purpose, the forward id, the origin, and every scope dimension from the request (namespace, identity, governed item, operation) — whether or not the binding restricts that dimension. A cached credential is therefore never shared across scopes, origins, or purposes.

Wanaku invalidates cached credentials in three cases:

- The binding's `revision` changes.
- The owning forward's address changes.
- The forward is deleted.

This guarantees a cached secret never outlives the forward it belongs to, and a revision bump on a binding immediately stops old cached values from being served.

## Security Guarantees

- Resolved secrets are held in memory as redacted, non-serializable material. They never implement `Display` or `Serialize`, their `Debug` output is a fixed redaction placeholder, and their backing bytes are zeroed when dropped.
- Injected header values are marked sensitive so the transport layer redacts them in logs and traces.
- Audit records contain only non-secret metadata: binding id, revision, resolver scheme, mechanism type, forward id, origin, purpose, outcome, expiry category, and a stable failure reason code. An audit record never contains a secret reference path or a resolved value. Wanaku persists each record to the audit trail. See [Audit Trail Integration](#audit-trail-integration).
- Only header names are safe to log. Header values that carry credentials are never logged.
- The proxy redacts injected credential material from the upstream response before the response reaches the agent or the logs. This applies to both the request pipeline and the forward discovery path. This closes the leak-back path where an upstream server echoes the credential in its output. See [Response Redaction](#response-redaction).
- Every failure mode — missing broker, missing binding, forward mismatch, origin mismatch, disallowed purpose, restriction violation, secret arity mismatch, unknown resolver scheme, missing secret, or an invalid header value — denies the request and injects no credential.
- A client-forwarded header that collides with a broker-managed header is rejected, not merged or overwritten.

## Audit Trail Integration

Wanaku persists each credential brokerage decision to the governance audit trail. Wanaku records one event for each brokerage attempt that reaches the broker. Wanaku also records one event for each fail-closed denial that happens before the broker runs. Every record is redacted and contains only non-secret metadata.

The event uses these fields:

- The `filter` field is `wanaku_credentials`.
- The `operation` field is `credential/discovery` or `credential/invocation`. The purpose keeps discovery records and invocation records distinct.
- The `target` field is the forward id. The `target_type` field is `forward`.
- The `upstream_id` field is the normalized upstream origin.
- The `attributes` field carries the binding id, binding revision, resolver types, mechanism, purpose, outcome, and expiry category.

Wanaku maps the resolution outcome to the audit decision:

- A resolved credential and a cache hit map to the `allow` decision.
- A denied credential maps to the `block` decision.
- A failed resolution maps to the `error` decision.

Wanaku records a fail-closed denial that happens before the broker runs with the `block` decision. The `reason_code` field identifies the cause: `binding_not_found`, `header_collision`, `broker_unavailable`, or `registry_unavailable`. A pre-broker denial knows only non-secret identifiers, so it sets an empty binding revision, empty resolver types, an empty mechanism, and an empty origin.

Wanaku records the event on both paths. The request pipeline records the event for the `tools/call`, `resources/read`, and `prompts/get` paths. The forward discovery path records the event at startup, in the management API, and in the background reconnect loop.

Wanaku records the success event after it builds the transport headers. If a resolved credential produces an invalid header value, Wanaku records a failed event with the `invalid_header_value` reason code and denies the request. Wanaku never records an allow decision for a denied request.

An audit storage failure does not change or block the brokerage decision. See [Governance audit trail](audit-trail.md).

> [!NOTE]
> A credential audit event carries no correlation or request identifier in this release. You cannot yet join a credential event to the tool call or governance decision that triggered it.

## Response Redaction

An upstream server can echo an injected credential in its response. For example, a verbose error can include the received `Authorization` header, or a debug endpoint can reflect the request. The proxy redacts this material before the response reaches the agent or the logs.

The redactor runs on the forwarded `tools/call`, `resources/read`, and `prompts/get` paths. It also runs on the forward discovery path. See [Discovery Redaction](#discovery-redaction). For each brokered request it strips two patterns:

1. The full injected header value, for example `Bearer <secret>`.
2. The raw resolved secret material.

The proxy replaces each match with the placeholder `<redacted>`. It redacts the response content, the upstream error text, and the error text that goes to the logs.

The redactor uses exact substring matching. This has the following limits:

- A secret that the upstream re-encodes (for example base64 or URL-encoded) before it echoes the value is not matched. The redactor only matches the injected form.
- A secret that the upstream splits across two separate content items is not matched. The redactor checks each content item on its own.
- A very short secret can match unrelated text and cause over-redaction. Use secrets with sufficient length and entropy.

Response redaction is a defense-in-depth control. It reduces the impact of an upstream that echoes a credential. It does not replace the primary controls: scope enforcement, origin binding, and fail-closed brokerage.

### Discovery Redaction

Forward discovery runs outside the request filter pipeline. It runs at startup, in the management API, and in the background reconnect loop. When a forward configures a `discovery` binding, Wanaku injects the brokered credential into the discovery call. The upstream can echo that credential back in the discovered metadata.

The proxy redacts the injected discovery credential from the discovery response before it persists the metadata and serves it to agents. The redactor strips the same two patterns as the request pipeline: the full injected header value and the raw resolved secret material. It applies to the discovered tools, resources, resource templates, and prompts, and to the server identity fields. The proxy also redacts the discovery error text before it logs the error or stores it in the forward status message.

Discovery redaction has the same exact-substring-matching limits as response redaction. A re-encoded or split secret is not matched.

## Inspecting Bindings

The management API exposes two read-only routes for credential bindings. Both return only non-secret metadata — `secretRefs` shows the opaque reference string, never a resolved value.

```bash
curl http://localhost:8080/api/v1/bindings
```

```json
{
  "data": [
    {
      "id": "b1",
      "forwardId": "upstream-mcp",
      "origin": "https://api.example.com:443",
      "mechanism": { "type": "bearer" },
      "secretRefs": ["env:API_TOKEN"],
      "allowedPurposes": ["invocation"],
      "restrictions": {},
      "cache": { "maxTtlSeconds": 3600 },
      "revision": 1
    }
  ],
  "error": null
}
```

```bash
curl http://localhost:8080/api/v1/bindings/b1
```

Wanaku returns `404 Not Found` when no binding with the given id exists.

This release does not have create, update, or delete routes for bindings. You author bindings in the `wanaku.yaml` configuration file. See [Configuring Credentials in wanaku.yaml](#configuring-credentials-in-wanakuyaml). Wanaku also persists bindings as part of the registry snapshot and restores them at startup. See [Management API](management-api.md) for the full route reference and response envelope.

## Related Docs

- [Management API](management-api.md) — the complete route reference and response format.
- [Governance Posture](governance-posture.md) — how Wanaku decides whether an action is authorized before the broker ever runs.
