# Audit UI implementation handoff

## Objective

Add an Audit page to the embedded React admin UI. The page must let an operator inspect recent audit events, filter the event list, open one event, and see audit-store health. The page is read-only. Do not add mutation controls or make audit failures affect other pages.

## Repository conventions

- Work in `ui/admin/`.
- Use React 19, TypeScript, React Router, and Carbon components from `@carbon/react`.
- Use Carbon icons from `@carbon/icons-react`.
- Follow the existing three-file page pattern: `Pages/Audit/AuditPage.tsx`, `Pages/Audit/index.ts`, and `Pages/Audit/router-exports.tsx`.
- Add the route to `router/links.models.ts`, `router.tsx`, and `navigation/core-nav-items.ts`.
- Use `PageSkeleton`, `ErrorNotification`, and the existing error-notification hook where appropriate.
- Do not edit generated files in `src/api/` or `src/models/` by hand. The generated audit client and models already exist.
- Keep styling in a page SCSS file or the existing page styling conventions. Use Carbon theme tokens. Do not introduce a new UI library.

## API contract

The generated functions are in `src/api/wanaku-router-api.ts`:

- `listAuditEvents(params?, options?)`
- `getAuditEvent(id, options?)`
- `getAuditHealth(options?)`
- `getAuditSchema(options?)`

The server returns the normal management envelope. The shared `customFetch` function removes that outer envelope. Check `response.status` and then read `response.data`, as existing pages do. Some generated audit response types still describe the server envelope and are more nested than the runtime value. Do not add another `.data` access to the runtime response.

### List events

`GET /api/v1/audit/events`

Supported query parameters (`ListAuditEventsParams`):

| Parameter | Meaning |
| --- | --- |
| `from`, `to` | Inclusive ISO 8601 time bounds |
| `namespace` | Namespace value |
| `actor` | Actor value; currently usually empty because trusted identity is not connected |
| `operation` | MCP operation, for example `tools/call`, `resources/read`, `prompts/get` |
| `target` | Tool, resource, or prompt target |
| `decision` | `allow`, `block`, `warn`, `reject_malformed`, or `error` |
| `reason_code` | Audit reason code |
| `correlation_id` | Correlation identifier |
| `offset` | Zero-based offset |
| `limit` | Page size; the server caps it at 1000 and uses 100 when omitted |

The response data is `{ events, offset, limit, total }`. Events are returned newest first.

### Event detail

`GET /api/v1/audit/events/{id}` returns one `AuditEvent`, or HTTP 404 when the event is not retained.

Important fields:

- Identity and ordering: `event_id`, `stream_id`, `sequence`, `timestamp`, `schema_version`.
- Decision: `category`, `decision`, `reason_code`, `explanation`.
- Request context: `correlation_id`, `request_id`, `conversation_id`, `namespace`, `actor`, `workload`, `audience`.
- Operation context: `protocol`, `operation`, `target_type`, `target`, `filter`, `evaluator`, `policy_revision`, `upstream_id`.
- Processing: `response_status`, `duration_ms`, `coverage_complete`, `dropped_events`.
- Data controls: `redaction` and optional `payload`.
- Dynamic metadata: `attributes` (JSON object).

Treat `payload` and `attributes` as untrusted JSON. Render them with a JSON viewer or formatted `<pre>` content, never as HTML. Keep long values collapsed or truncated in the list and allow full viewing in the detail panel.

### Health

`GET /api/v1/audit/health` returns:

```ts
{
  healthy: boolean;
  dropped_events: number;
  retained_events: number;
  capacity: number;
  last_error?: string | null;
}
```

Show a compact health status near the page title. A degraded store is an operator warning, not a page-fatal error. Display `last_error` only as returned by the API; the server uses a content-free safe message.

### Schema

`GET /api/v1/audit/schema` returns `{ schema_version: string }`. Display the version in the health/status area or event detail metadata if it is useful. Do not make the page depend on this request; failure to load the schema must not hide events.

## Recommended page behavior

1. Load the first page of events and health in parallel on mount.
2. Show a skeleton while the first event request is pending.
3. Show a Carbon `DataTable` with columns: timestamp, decision, category, operation, target, namespace, reason code, and correlation ID.
4. Map decisions to accessible Carbon tags: green for `allow`, red for `block`, yellow for `warn`, orange for `reject_malformed`, and gray/red for `error` according to the project theme. Do not rely on color alone; include the text label.
5. Make each row selectable. Open a Carbon `Modal` or `SidePanel` with the complete event detail. Include a copy action for `event_id` and `correlation_id` if an existing Carbon copy pattern is available.
6. Add filters above the table:
   - decision select;
   - operation text/select;
   - namespace text/select;
   - target text;
   - reason code text;
   - correlation ID text;
   - from/to date-time inputs;
   - a clear-filters button.
7. Apply filters through the API, not only client-side. Reset `offset` to zero whenever a filter changes or the user submits the filter form.
8. Add pagination using the returned `offset`, `limit`, and `total`. Keep the selected row/detail open only if its event remains available.
9. Add a refresh button. Refresh events and health together. Avoid polling unless the existing UI has an established polling pattern.
10. Handle an empty result with a clear empty state. Handle list/detail failures with `ErrorNotification` and a retry action.
11. If health fails while events load, keep the event table visible and show a non-blocking warning.
12. If an event detail returns 404, close the detail view and show that the event is no longer retained.

## Detail layout

Group fields into readable sections:

- Decision: category, decision tag, reason code, explanation.
- Request: timestamp, operation, target type, target, namespace, actor, workload, audience.
- Correlation: event ID, request ID, conversation ID, correlation ID, stream ID, sequence.
- Enforcement: filter, evaluator, policy revision, upstream ID, response status, duration.
- Redaction and coverage: schema version, coverage complete, dropped events, redacted fields, payload captured/truncated.
- Attributes and payload: formatted JSON, with an explicit “redacted” indication when applicable.

Show absent optional fields as an em dash or “Not available”. Do not display empty actor/workload values as an error; identity integration is deferred.

## API hook

Create `src/hooks/api/use-audit.ts` following the existing hooks such as `use-evaluators.ts`. Wrap the generated functions and expose typed methods for listing events, getting an event, getting health, and getting the schema. Keep response status handling in the page or hook consistent with existing pages.

## Routing and navigation

- Add `Audit = "/audit"` to `Links`.
- Add a lazy route in `router.tsx` that imports `./Pages/Audit`.
- Add `{ id: "audit", label: "Audit", route: Links.Audit, source: "core", section: "Admin", order: 88 }` (or the nearest available Admin order) to `CORE_NAV_ITEMS`.

## Tests

Add unit tests for filter serialization/state and decision-tag mapping where the project test setup supports them. Add or extend Playwright tests under `tests/e2e/ui/`:

- page title and navigation entry are visible;
- mocked or seeded events render in the table;
- decision and namespace filters issue the expected query and update the table;
- selecting a row opens detail fields and formatted attributes;
- empty state renders when the API returns no events;
- degraded health renders a warning without hiding the table.

Use the existing page-object and API-helper patterns. Do not hard-code generated model types or duplicate the API contract in handwritten model files.

## Verification

From `ui/admin/` run:

```bash
yarn run build
```

If the API contract changes, regenerate the client with `yarn run generate-api` and commit the generated files. For UI-only work, do not regenerate the API. Run the relevant Playwright tests, then rebuild the Rust server if embedded assets must be verified:

```bash
cargo build
```

## Acceptance criteria

- The Audit page is reachable from the Admin navigation and through a hash route.
- The page uses the existing API client and Carbon components.
- Operators can filter, paginate, refresh, and inspect retained audit events.
- Health degradation is visible but does not block audit observation.
- Sensitive values are not rendered as executable markup, and redaction metadata is visible.
- Loading, empty, 404, API error, and degraded-health states are handled.
- TypeScript build and required E2E tests pass.
