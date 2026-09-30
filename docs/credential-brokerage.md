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

Here is a binding that injects a Bearer token read from the `API_TOKEN` environment variable:

```json
{
  "id": "b1",
  "forwardId": "upstream-mcp",
  "origin": "https://api.example.com:443",
  "mechanism": { "type": "bearer" },
  "secretRefs": ["env:API_TOKEN"],
  "allowedPurposes": ["invocation"],
  "restrictions": {
    "namespaces": ["finance"]
  },
  "cache": { "maxTtlSeconds": 3600 },
  "revision": 1
}
```

> [!NOTE]
> The `restrictions` object serializes its inner fields in snake_case (`governed_items`, not `governedItems`), unlike the top-level binding fields, which use camelCase. Match the exact key when you author or read a binding.

## Purposes

A credential purpose declares what the credential is for. Wanaku keeps two purposes distinct in policy checks, resolution context, audit records, and cache keys:

- **`discovery`** — authenticated forward discovery and refresh (the upstream calls Wanaku makes to learn a forward's tools, resources, and prompts).
- **`invocation`** — authenticated tool calls, resource reads, and prompt retrieval.

A binding declares which purposes it may serve through `allowedPurposes`. A binding scoped only to `discovery` cannot be used to authenticate a tool call, and vice versa.

## Attaching a Binding to a Forward

A forward references its bindings through the `credentialBindings` field: a map from purpose to binding id.

```json
{
  "name": "upstream-mcp",
  "address": "https://api.example.com/mcp",
  "namespace": "finance",
  "credentialBindings": {
    "discovery": "b-discovery",
    "invocation": "b1"
  }
}
```

Tools, resources, and prompts carry no secret or binding reference of their own. They inherit the invocation binding through their `forwardId`. This keeps the credential surface in one place: change the binding on the forward, and every tool, resource, and prompt that forward exposes picks up the change on its next use.

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
- Audit records contain only non-secret metadata: binding id, revision, resolver scheme, mechanism type, forward id, origin, purpose, outcome, expiry category, and a stable failure reason code. An audit record never contains a secret reference path or a resolved value.
- Only header names are safe to log. Header values that carry credentials are never logged.
- The proxy redacts injected credential material from the upstream response before the response reaches the agent or the logs. This applies to both the request pipeline and the forward discovery path. This closes the leak-back path where an upstream server echoes the credential in its output. See [Response Redaction](#response-redaction).
- Every failure mode — missing broker, missing binding, forward mismatch, origin mismatch, disallowed purpose, restriction violation, secret arity mismatch, unknown resolver scheme, missing secret, or an invalid header value — denies the request and injects no credential.
- A client-forwarded header that collides with a broker-managed header is rejected, not merged or overwritten.

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

This release does not have create, update, or delete routes for bindings. Bindings are persisted as part of the registry snapshot. See [Management API](management-api.md) for the full route reference and response envelope.

## Related Docs

- [Management API](management-api.md) — the complete route reference and response format.
- [Governance Posture](governance-posture.md) — how Wanaku decides whether an action is authorized before the broker ever runs.
