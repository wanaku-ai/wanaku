---
title: Semantic routing with Barn and WSR
description: Create a semantic router in Barn, run it with the Wanaku Semantic Router (WSR), and authorize its MCP tool with Wanaku action policies.
---

# Semantic routing with Barn and the Wanaku Semantic Router (WSR)

Barn provides a focused wizard for creating semantic routers from curated Kamelet actions. Wanaku Semantic Router (WSR) runs the published Camel integration. Wanaku discovers its MCP tool and applies the normal action policy before forwarding each call.

## Ownership and authority

| Application | Responsibility |
| --- | --- |
| Barn | Remote Kamelet storage and discovery, definitions, validation, classification previews, catalog generation, and publication. |
| WSR | Pinned catalog download, Camel startup, MCP exposure, runtime status, and forward registration. |
| Wanaku | Namespace discovery, authorization, credential brokerage, and MCP forwarding. |

WSR executes with its service credentials. An allowed router invocation permits use of the configured workflow. Caller attribution does not grant delegated authority. Internal Camel routes can use any protocol. If an action calls Wanaku, Wanaku applies governance to the WSR service identity for that call.

## Create and deploy a router

1. Open **Semantic routers** in the Barn admin UI or the installed Barn plugin.
2. Enter the router identity and public tool name.
3. Select compatible business actions.
4. Set the action configuration fields.
5. Select a configured expert instance.
6. Select the input that the expert evaluates.
7. Enter the criteria for each fixed label and for no match.
8. Save examples with their expected labels.
9. Run classification previews.
10. Review the validation result.
11. Publish the catalog.
12. Configure WSR with the published route name (`--semantic-route` or `WSR_SEMANTIC_ROUTE`). To pin the publication, set `WSR_CATALOG_REVISION`, `WSR_CATALOG_SHA256`, `WSR_CATALOG_NAME`, `WSR_CATALOG_SERVICE`, and `WSR_CATALOG_MAIN`.
13. Configure the expert credentials (for the TypeSafe AI expert, `TYPESAFE_API_KEY` and the optional `TYPESAFE_MODEL`) and the backend service credentials in the deployment.
14. Start WSR.
15. Read its runtime status.

Publication creates an immutable catalog revision. Publication does not start WSR. WSR downloads and verifies the complete archive before Camel starts. WSR registers a forward only after MCP readiness and discovery succeed. The status response reports the loaded revision, digest, Camel version, and Camel build.

The initial compatibility profile is `message-to-string/v1`. The public tool requires a `message` string. Action configuration is deployment data. The message is invocation data. Catalog authors must provide compatible Kamelets or adapters. A native sink can receive the message as a routing destination. The generated route returns a text acknowledgment after successful sink execution. A sink failure returns an execution error. Model output selects a configured label. Model output cannot supply a destination URI or an executable expression.

Classification preview uses the same native semantic question and configured expert instance. The preview worker creates an isolated Camel context. It does not load action routes. Available numeric diagnostics describe the provider response. Deterministic fixtures do not establish model accuracy.

For runtime packaging, all WSR settings, credential configuration, and the exact Camel build, read `docs/deployment.md` in the WSR distribution. For the artifact contract and authoring API, read `docs/semantic-router-contract.md` in Barn. For the authoring workflow, read `docs/semantic-routing-wizard.md` in Barn. These documents are included with the sibling implementations.

## Manage remote Kamelets

Open **Kamelets** in Barn. Upload one UTF-8 `.kamelet.yaml` file. Barn validates and stores the native source, sink, or action definition. Eligible semantic actions become available when you open the router wizard. Barn does not require a restart.

Native sinks that consume from `kamelet:source` can appear in the wizard without Barn annotations or an output schema. An explicit input schema must accept a string. Supply the sink parameters before publication. For `kafka-sink`, supply `topic` and `bootstrapServers`. Native password fields use environment variable references. The WSR distribution includes the Kafka component. Other Camel components must be included in the runtime distribution. Source Kamelets create messages and are not routing destinations.

Camel applications can use the raw YAML catalog location:

```properties
camel.component.kamelet.location=http://localhost:8180/api/v1/kamelets/
```

Include the trailing slash. The application must have the required Camel component dependencies. Its native resource loader must be able to read the Barn URL. Read `docs/kamelets.md` in Barn for upload APIs and eligibility rules.

Saved action selections contain the Kamelet SHA-256 digest. An upload with changed content creates a new current revision. It does not change a saved selection. Use the wizard's explicit revision update to select the new content. Removing a current entry retains revisions used by saved selections. Published WSR catalogs contain the selected files and remain independent of later catalog changes.

## Authorize the tool

Register WSR in a dedicated namespace. Add an explicit action policy for the exposed tool.

> [!WARNING]
> `PUT /api/v1/action-policies` replaces the complete active policy. The request below removes all other rules. If a policy is already active, get it with `GET /api/v1/action-policies`, add the new rule to its `rules` list, and send the complete list. Set `expected_revision` to the active revision ID to prevent a lost update.

```bash
curl -X PUT http://localhost:8080/api/v1/action-policies \
  -H 'Content-Type: application/json' \
  -d '{"policy":{"rules":[{
    "id":"allow-support-router",
    "effect":"allow",
    "selectors":{
      "namespace":"support",
      "operation":"tools/call",
      "target_type":"tool",
      "target_name":{"matcher":"exact","value":"route_support"}
    }
  }]}}'
```

Use `/support/mcp` on the Wanaku MCP listener. Tool discovery does not grant authorization. A policy denial occurs before the request reaches WSR. See [Action Policies](../govern/action-policies.md) for deny precedence and policy revision management.

An action-policy allow does not bypass the evaluator. With the default posture (`no_match: deny`), a call to the tool still fails with `evaluator: no_match` when no evaluator matches it. Configure an evaluator for this tool, or set the no-match posture of the namespace. For example, in `wanaku.yaml`:

```yaml
governance:
  namespaces:
    support:
      no_match: allow
```

`no_match: allow` also applies to the action policy in that namespace. Then the action policy permits each call that no rule denies. Keep explicit deny rules for actions that the namespace must block. See [Governance Posture](../govern/governance-posture.md). The acceptance suite uses explicit policy allow and deny rules with `no_match: allow` in its isolated test namespace.

An action result is a successful MCP result. No match is an explicit successful result and executes no action. Evaluation failure and action failure return errors. Wanaku preserves supported MCP content blocks, structured content, metadata, and `isError`. Wanaku retains credential redaction at the forwarding boundary. An unsupported content type returns an error.

## Replace an instance

1. Publish the changed definition.
2. Record the new revision and digest.
3. Stop the old WSR instance.
4. Start the replacement with the new deployment pins.
5. Verify its status and forward availability.

Graceful shutdown removes the WSR forward. After an abrupt failure, Wanaku marks the forward unavailable when discovery fails. A replacement uses the normal forward registration path. Updates require restart or replacement. WSR does not insert routes into a running production context or select the latest revision automatically.

## Run acceptance checks

Wanaku has a cross-repository acceptance suite for Barn, WSR, and Wanaku. Read [Semantic router acceptance suite](../contributing/testing.md#semantic-router-acceptance-suite) for the prerequisites and the command.

## Release compatibility

Use Barn, WSR, and Wanaku revisions that pass the cross-repository suite together. Run the [acceptance suite](../contributing/testing.md#semantic-router-acceptance-suite) against the selected revisions before release.

Tracking: [Wanaku #2101](https://github.com/wanaku-ai/wanaku/issues/2101), [Wanaku #2102](https://github.com/wanaku-ai/wanaku/issues/2102), and [Barn #179–184](https://github.com/wanaku-ai/wanaku-barn/issues/179).

## Next steps

- [Action Policies](../govern/action-policies.md): allow and deny calls to the router tool.
- [Ingest Camel semantic audit records](semantic-audit-ingestion.md): store the router decisions in the audit trail.
- [Governance Posture](../govern/governance-posture.md): set the no-match posture for the router namespace.
