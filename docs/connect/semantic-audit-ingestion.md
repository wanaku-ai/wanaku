---
title: Ingest Camel semantic audit records
description: Send Camel semantic audit records to the Wanaku audit store, with the envelope format, validation limits, deduplication, and capacity rules.
---

# Ingest Camel semantic audit records

Camel runtimes, such as the Wanaku Semantic Router (WSR), make semantic decisions that Wanaku does not see. This page shows how to send those records to the Wanaku audit store, so that they appear next to the native governance events.

## Enable the endpoint

The management API accepts Camel version-1 semantic audit records at `POST /api/v1/audit/semantic`. This endpoint is disabled by default. Set `WANAKU_AUDIT_INGESTION_ENABLED=true`, or set `audit.ingestion_enabled: true` in the configuration.

The endpoint has no authentication in this version. All source fields are producer claims. The endpoint does not verify source identity. Authentication is tracked in Wanaku issue #2124.

## Send a record

Send this envelope:

```json
{
  "source": {
    "instance_id": "runtime-1",
    "forward_name": "support-route",
    "namespace": "default",
    "catalog_revision": "r1",
    "catalog_digest": "sha256-digest"
  },
  "record": {
    "schemaVersion": 1,
    "eventId": "evaluation-1",
    "timestamp": "2026-10-12T12:00:00Z",
    "category": "evaluation",
    "operation": "choice",
    "result": {"value": "billing", "probability": 0.9}
  }
}
```

The record category must be `evaluation`, `decision`, or `request`. Use the Camel `SemanticAuditRecord.toMap()` fields. Remove `input` and `inputRedacted` before transmission. Unknown fields and null values are rejected. The request body limit is 64 KiB. Strings have a 256-character limit. Collections have a 100-entry limit. Source fields and event identifiers must contain nonblank text. Control characters are rejected.

## Stored format

Wanaku stores these records with category `semantic`, decision `observed`, and protocol `camel-semantic`. This decision records an observation. It does not indicate an enforcement decision. Camel results remain in `attributes.semantic`. Explicit Camel correlation, request, target, expert, policy version, reason code, and duration fields populate the common audit fields. Exchange IDs and invocation IDs do not replace application correlation IDs. Wanaku applies the configured audit content redaction rules. The optional `source` field retains source event IDs, timestamps, and evidence references separately. `source.producer_asserted` is always true for these records.

A successful response has this format:

```json
{"data":{"status":"accepted_in_memory","event_id":"wanaku-event-id","duplicate":false},"error":null}
```

Acceptance confirms in-memory retention. It does not confirm durable storage. Persistence uses the existing asynchronous audit worker. Use `GET /api/v1/audit/health` to inspect storage health.

## Retries and deduplication

A retry with the same source instance ID, source event ID, and content returns the original event ID with `duplicate: true`. Changed content for that pair returns HTTP 409. Deduplication lasts while the event remains retained. Retained persisted records restore deduplication after a restart. The stored source fingerprint identifies the original envelope before content redaction.

## Capacity

External records have a separate capacity limit. The default limit is 1,000. Set `WANAKU_AUDIT_EXTERNAL_MAX_RECORDS`, or `audit.external_max_records`, to change it. Wanaku restricts this value below `audit.max_records` to reserve at least one slot for native events. External admission removes the oldest external record when its capacity is full. It cannot remove a native record. The endpoint returns HTTP 429 if all available slots contain native records. Native events can remove external records.

## Find records

Use `category=semantic`, `source_instance_id`, and `source_event_id` with `GET /api/v1/audit/events` to find records and evidence. Existing native events and persisted records without a source field remain compatible.

## Next steps

- [Governance audit trail](../govern/audit-trail.md): configure retention, redaction, and persistence for the audit store.
- [Semantic routing with Barn and WSR](semantic-routing.md): deploy the router that produces these records.
- [Management API](../reference/management-api.md): read the audit routes.
