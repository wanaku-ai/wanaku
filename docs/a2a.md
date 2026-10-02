# A2A Proxy

Wanaku can proxy A2A requests to an upstream agent. This initial implementation uses the Praxis A2A filter and the existing Wanaku action-policy engine. It provides a small starting point for governed agent communication.

## Supported operations

The initial binding is A2A 0.3 JSON-RPC over HTTP. Use `POST /` for protocol requests. Use `GET /.well-known/agent-card.json` for discovery.

| Wire method | Canonical policy operation | Behavior |
| --- | --- | --- |
| `message/send` | `SendMessage` | Submit a message to the upstream agent. |
| `tasks/get` | `GetTask` | Read the upstream task state. |
| `tasks/cancel` | `CancelTask` | Request task cancellation from the upstream agent. |

The upstream agent owns task execution and state. Wanaku does not run an agent or translate A2A messages into MCP calls.

Wanaku rejects streaming, push notifications, and unsupported methods before dispatch. It also rejects streaming and push options in a normal message request. An explicit `A2A-Version` header must identify the supported version.

Wanaku checks the request envelope and required message or task fields. The upstream agent validates the complete A2A message schema. Wanaku requests uncompressed responses and rejects compressed or streaming upstream responses.

## Configure the pipeline

A2A is not enabled in the embedded default pipeline. Start Wanaku with an explicit `--pipeline-config` file. A custom pipeline replaces the embedded pipeline. Include the MCP and inference listeners in that file if the deployment also requires them.

Use [examples/a2a.yaml](../examples/a2a.yaml) as a starting point. Set its upstream endpoint, `Host` header, and `public_url` for the deployment. The example binds the A2A listener to `127.0.0.1:8084` and forwards to `127.0.0.1:9000`.

Start the server with the pipeline and policy configuration:

```bash
cargo run -- --pipeline-config examples/a2a.yaml --wanaku-config wanaku.yaml
```

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

Discovery returns the upstream Agent Card with the proxy URL. Wanaku disables the advertised streaming and push capabilities. It removes additional interfaces and signatures because the card has changed. The proxy does not expose authenticated extended cards.

## Configure action policy

Use the same `action_policy` configuration as MCP. A2A targets use `target_type: agent`. The target name is the filter's configured `agent` value. Policy predicates read the JSON-RPC `params` object.

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
```

The test starts two temporary upstream backends and a Wanaku process. It checks discovery, message forwarding, task lookup, cancellation, policy denial, unsupported requests, and A2A audit correlation. Split responses verify that task-owner routing sends follow-up requests to the backend that created the task. The test does not require an external agent.
