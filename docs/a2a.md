# A2A Proxy

Wanaku can register upstream A2A agents and proxy requests to them. The admin UI lets operators manage agents without editing a pipeline file. The proxy uses the Praxis A2A filter and the existing Wanaku action-policy engine.

## Manage agents in the admin UI

Open the **A2A agents** page in the admin UI.

1. Select **Add A2A agent**.
2. Enter the agent name.
3. Select its namespace.
4. Enter its upstream A2A JSON-RPC address.
5. Enter a description if needed.
6. Enter an explicit Agent Card address if the upstream uses a different discovery location.
7. Save the agent.

The table shows the upstream address. Select **Details** to show the Wanaku proxy endpoint. Use **Edit** to change an agent's description or upstream addresses. The name and namespace identify the registered agent and do not change during an edit. Use **Delete** to remove the agent.

Agent entries use the same persistence backend as the other registry entries. A registration, edit, or deletion changes routing for subsequent requests without a restart. Editing an upstream address does not migrate task state between upstream agents.

## Supported operations

Wanaku supports A2A 0.3 and 1.0 JSON-RPC over HTTP. For a registered agent, use `POST /{namespace}/a2a/{name}` for protocol requests. Use `GET /{namespace}/a2a/{name}/.well-known/agent-card.json` for discovery. The namespace is the first path segment, as it is for `/{namespace}/mcp`. The agent name selects one registered agent in that namespace.

| A2A 0.3 method | A2A 1.0 method and policy operation | Behavior |
| --- | --- | --- |
| `message/send` | `SendMessage` | Submit a message to the upstream agent. |
| `tasks/get` | `GetTask` | Read the upstream task state. |
| `tasks/cancel` | `CancelTask` | Request task cancellation from the upstream agent. |

The upstream agent owns task execution and state. Wanaku does not run an agent or translate A2A messages into MCP calls.

Wanaku rejects streaming, push notifications, and unsupported methods before dispatch. It also rejects streaming and push options in a normal message request. Set `A2A-Version: 1.0` for a 1.0 request. A missing or empty header selects 0.3. Wanaku also accepts `0.3.0` for existing clients. An unsupported version returns `VersionNotSupportedError`.

Wanaku forwards method names, request bodies, and upstream responses without converting protocol versions. A method alias selects the policy operation. It does not change the method sent to the upstream agent. The upstream must support the requested version and method.

For 1.0 messages, use `ROLE_USER` or `ROLE_AGENT`. Parts use fields such as `text`, `raw`, `url`, or `data`. The 1.0 format does not require a `kind` field. Task lookup and cancellation use `params.id`.

Send a 1.0 message to a registered agent:

```bash
curl -sS http://127.0.0.1:8084/default/a2a/assistant \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H 'A2A-Version: 1.0' \
  --data '{
    "jsonrpc": "2.0",
    "id": "request-1",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "message-1",
        "role": "ROLE_USER",
        "parts": [{"text": "echo"}]
      }
    }
  }'
```

Wanaku checks the request envelope and required message or task fields. The upstream agent validates the complete A2A message schema. Wanaku requests uncompressed responses and rejects compressed or streaming upstream responses. Wanaku also rejects upstream redirects.

## Configure the managed listener

The embedded pipeline exposes registered agents on the A2A listener. The default listener address is `0.0.0.0:8084`. Set `WANAKU_A2A_LISTEN` to change it. Set `WANAKU_A2A_PUBLIC_URL` to the HTTP or HTTPS origin that clients can access. The default public origin is `http://127.0.0.1:8084/`.

```bash
export WANAKU_A2A_PUBLIC_URL=https://agents.example.com/
cargo run -- --wanaku-config wanaku.yaml
```

The Praxis filter extracts protocol metadata. The registry selector finds the agent by namespace and name. It selects the registered upstream address and supplies the namespace and agent policy target. The Wanaku boundary filter validates supported requests. The shared action-policy filter evaluates the action before upstream dispatch.

The agent path selects the upstream for every task operation. The managed listener does not use the global Praxis task-ID routing store. Agents in different namespaces can use the same name or task ID without sharing a route.

Unknown or deleted agent routes do not dispatch a request. Registering an agent does not grant permission to call it. Configure action policy separately.

## Use a static custom pipeline

For a fixed upstream, you can still use an explicit `--pipeline-config` file. A custom pipeline replaces the embedded pipeline. Include the MCP, inference, and managed A2A listeners in that file if the deployment also requires them. Registering an agent through the UI does not add a managed listener to a custom pipeline.

Use [examples/a2a.yaml](../examples/a2a.yaml) as a starting point. Set its upstream endpoint, `Host` header, and `public_url` for the deployment. The example binds the A2A listener to `127.0.0.1:8084` and forwards to `127.0.0.1:9000`.

Start the server with the pipeline and policy configuration:

```bash
cargo run -- --pipeline-config examples/a2a.yaml --wanaku-config wanaku.yaml
```

This static example uses `POST /` and `GET /.well-known/agent-card.json`. Its upstream is configured in YAML, rather than selected from registered agents.

The A2A filter order is:

1. Place the Praxis `a2a` filter first to extract protocol metadata.
2. Place `wanaku_a2a` next to validate supported requests and set the configured namespace and agent target.
3. Place `wanaku_action_policy` next to apply governance.
4. Place the upstream routing filters last.

Configure these fields on `wanaku_a2a`:

| Field | Value |
| --- | --- |
| `public_url` | Required HTTP or HTTPS origin for the proxy. Use the address that clients can access. |
| `agent` | Stable policy target name. The default is `a2a`. |
| `namespace` | Namespace used for policy and posture. The default is `default`. |
| `max_body_bytes` | Request and discovery-response buffer limit. The default is 1048576 bytes. |

Set the upstream endpoint in the Praxis load-balancer configuration. Set the upstream `Host` header to the value required by that backend.

The example enables Praxis task and context routing with a local store. Follow-up requests use the recorded backend owner. Non-terminal task routes and context routes expire after 3600 seconds. Terminal task routes expire after 300 seconds. The routing store does not persist across a process restart. The example has one upstream backend, so an expired route still resolves to that configured backend. Multi-backend deployment and shared routing storage are outside this initial example.

## Agent Card versions

Wanaku reads the upstream Agent Card. It uses declared protocol versions when present. When protocol version information is absent, Wanaku assumes 1.0.0. It writes the interface version as `1.0`. The card's `version` field identifies the agent release. It does not select the protocol version.

A 1.0 card declares its proxy endpoint in `supportedInterfaces`. Wanaku advertises only supported JSON-RPC interfaces for the configured upstream endpoint. It rewrites their URLs and preserves tenant information. If interface information is absent, Wanaku supplies one JSON-RPC interface. Explicitly incompatible interface declarations fail discovery. Wanaku does not select a different upstream from the card.

For a modern card in a static pipeline, discovery and messages must use the same upstream. The message endpoint must be its root path. Wanaku does not infer that endpoint when the discovery path is rewritten. Use a managed registration for custom RPC paths or separate discovery and message addresses.

An explicitly declared 0.3 card that uses legacy fields retains its `url` and `preferredTransport` fields. Wanaku disables streaming, push notifications, and extended cards. It removes signatures because rewriting changes the signed content.

The 1.0.0 discovery fallback does not change request version selection. Clients that use 1.0 must send `A2A-Version: 1.0`.

## Configure action policy

Use the same `action_policy` configuration as MCP. A2A targets use `target_type: agent`. For a managed route, the target name is the registered agent name. For a static route, the target name is the filter's configured `agent` value. Policy predicates read the JSON-RPC `params` object.

For example, add this policy to `wanaku.yaml`:

```yaml
action_policy:
  rules:
    - id: allow-a2a-agent
      effect: allow
      selectors:
        namespace: default
        target_type: agent
        target_name:
          matcher: exact
          value: a2a
    - id: deny-a2a-cancel
      effect: deny
      selectors:
        namespace: default
        target_type: agent
        operation: CancelTask
      reason_code: cancellation_denied
      message: Task cancellation is not permitted.
```

A matching deny takes precedence over an allow. The existing governance posture controls unmatched requests, policy failures, audit mode, and disabled scopes. The default enforce posture denies unmatched requests. Configure a matching allow before sending an action.

A2A policy decisions use `protocol: a2a` in the shared audit trail. Task and context identifiers provide correlation. They do not establish authenticated identity or task ownership for authorization.

The MCP evaluator is not part of the initial A2A pipeline. A2A policy allows do not imply that an MCP evaluator has approved the request.

## Identity and follow-up work

The initial implementation does not populate a trusted actor identity. A deployment can protect the A2A listener with an external authentication proxy. That authentication does not supply a verified actor context to Wanaku's policy engine.

Complete the initial A2A implementation in [#383](https://github.com/wanaku-ai/wanaku/issues/383) first. Resolve trusted identity and authorization in [#503](https://github.com/wanaku-ai/wanaku/issues/503) next. Then implement these follow-up tasks:

- [#2069](https://github.com/wanaku-ai/wanaku/issues/2069): A2A-to-MCP bridging and verified parent task context.
- [#2070](https://github.com/wanaku-ai/wanaku/issues/2070): A2A integration with the shared approval workflow in [#1967](https://github.com/wanaku-ai/wanaku/issues/1967).

## Verify the proxy

Build the server before running the isolated HTTP test:

```bash
cargo build -p wanaku-server --no-default-features
python3 tests/a2a/smoke.py --binary target/debug/wanaku-server
python3 tests/a2a/managed.py --binary target/debug/wanaku-server
python3 tests/a2a/version1.py --binary target/debug/wanaku-server
```

The test starts two temporary upstream backends and a Wanaku process. It checks discovery, message forwarding, task lookup, cancellation, policy denial, unsupported requests, and A2A audit correlation. Split responses verify that task-owner routing sends follow-up requests to the backend that created the task. The test does not require an external agent.

The managed test checks agent registration, proxy URLs, live address changes, namespace isolation, deletion, and persistence across a server restart.

The 1.0 test checks version-specific message formats, task operations, untouched forwarding, discovery interfaces, unsupported features, namespace isolation, and audit correlation.
