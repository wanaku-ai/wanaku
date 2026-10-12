import {
  Button,
  Checkbox,
  InlineLoading,
  InlineNotification,
  Select,
  SelectItem,
  TextInput,
} from '@carbon/react';
import { useEffect, useState } from 'react';
import { useAudit } from '../../hooks/api/use-audit';
import { ApiError } from '../../custom-fetch';
import type { AuditEvent, AuditPage, NamespaceEntry } from '../../models';
import { formatAuditDate } from '../Audit/audit-utils';

const PAGE_SIZE = 20;
const METHODS = ['tools/call', 'resources/read', 'prompts/get'];

const unavailableReason = (event: AuditEvent): string | undefined => {
  if (event.category !== 'decision') return 'Administrative event';
  if (event.payload == null) return 'No captured request payload';
  if (event.redaction.payload_truncated) return 'Truncated request payload';
  if (event.redaction.redacted_fields.length) return 'Redacted request fields';
  const payload = event.payload;
  if (
    typeof payload !== 'object' ||
    Array.isArray(payload) ||
    !('method' in payload) ||
    typeof payload.method !== 'string' ||
    !METHODS.includes(payload.method)
  )
    return 'Unsupported MCP action';
  if (
    !('jsonrpc' in payload) ||
    payload.jsonrpc !== '2.0' ||
    !('params' in payload) ||
    !payload.params ||
    typeof payload.params !== 'object' ||
    Array.isArray(payload.params)
  )
    return 'Invalid MCP request payload';
  const params = payload.params as Record<string, unknown>;
  const target = payload.method === 'resources/read' ? params.uri : params.name;
  if (typeof target !== 'string' || !target.trim()) return 'Invalid MCP request payload';
  return undefined;
};

interface AuditEventPickerProps {
  namespaces: NamespaceEntry[];
  selected: string[];
  onChange: (ids: string[]) => void;
  disabled: boolean;
}

export const AuditEventPicker = ({
  namespaces,
  selected,
  onChange,
  disabled,
}: AuditEventPickerProps) => {
  const { listEvents, getEvent } = useAudit();
  const [eventId, setEventId] = useState('');
  const [namespace, setNamespace] = useState('');
  const [operation, setOperation] = useState('tools/call');
  const [target, setTarget] = useState('');
  const [query, setQuery] = useState({
    namespace: '',
    operation: 'tools/call',
    target: '',
    eventId: '',
    offset: 0,
  });
  const [results, setResults] = useState<AuditPage>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setResults(undefined);
    void (async () => {
      try {
        if (query.eventId) {
          const response = await getEvent(encodeURIComponent(query.eventId), {
            signal: controller.signal,
          });
          setResults({ events: [response.data], total: 1, offset: 0, limit: PAGE_SIZE });
        } else {
          const response = await listEvents(
            {
              namespace: query.namespace || undefined,
              operation: query.operation || undefined,
              target: query.target || undefined,
              offset: query.offset,
              limit: PAGE_SIZE,
            },
            { signal: controller.signal },
          );
          setResults(response.data);
        }
      } catch (cause) {
        if (!controller.signal.aborted)
          setError(
            cause instanceof ApiError && cause.status === 404
              ? 'Audit event not found or no longer retained.'
              : cause instanceof Error
                ? cause.message
                : 'Audit events are unavailable.',
          );
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    })();
    return () => controller.abort();
  }, [query, getEvent, listEvents]);

  const browse = () =>
    setQuery({ namespace, operation, target: target.trim(), eventId: '', offset: 0 });

  return (
    <section aria-label="Audit events for replay">
      <h3>Choose audit events</h3>
      <p>
        Select retained MCP decision events with usable request payloads. Payload capture is
        disabled by default. Events without captured payloads cannot be reconstructed from an ID.
      </p>
      <form
        className="policy-simulation__audit-search"
        onSubmit={(event) => {
          event.preventDefault();
          if (eventId.trim()) setQuery({ ...query, eventId: eventId.trim(), offset: 0 });
        }}
      >
        <TextInput
          id="replay-event-id"
          labelText="Find audit event by ID"
          value={eventId}
          disabled={disabled}
          onChange={(event) => setEventId(event.target.value)}
        />
        <Button type="submit" disabled={disabled || !eventId.trim()}>
          Find event
        </Button>
      </form>
      <form
        className="policy-simulation__audit-search"
        onSubmit={(event) => {
          event.preventDefault();
          browse();
        }}
      >
        <Select
          id="replay-event-namespace"
          labelText="Audit namespace"
          value={namespace}
          disabled={disabled}
          onChange={(event) => setNamespace(event.target.value)}
        >
          <SelectItem value="" text="All namespaces" />
          {namespaces.map((entry) => (
            <SelectItem key={entry.name} value={entry.name} text={entry.name} />
          ))}
        </Select>
        <Select
          id="replay-event-method"
          labelText="Audit operation"
          value={operation}
          disabled={disabled}
          onChange={(event) => setOperation(event.target.value)}
        >
          <SelectItem value="" text="All operations" />
          {METHODS.map((method) => (
            <SelectItem key={method} value={method} text={method} />
          ))}
        </Select>
        <TextInput
          id="replay-event-target"
          labelText="Audit target"
          value={target}
          disabled={disabled}
          onChange={(event) => setTarget(event.target.value)}
        />
        <Button type="submit" disabled={disabled}>
          Search events
        </Button>
      </form>
      {error && (
        <InlineNotification
          kind="error"
          title="Audit events are unavailable"
          subtitle={error}
          hideCloseButton
        />
      )}
      {loading && <InlineLoading description="Loading audit events…" />}
      {results && (
        <>
          <div className="policy-simulation__table-scroll">
            <table>
              <caption>Retained audit events</caption>
              <thead>
                <tr>
                  <th>Select</th>
                  <th>Event ID</th>
                  <th>Time</th>
                  <th>Namespace</th>
                  <th>Action</th>
                  <th>Target</th>
                  <th>Replay availability</th>
                </tr>
              </thead>
              <tbody>
                {results.events.map((event) => {
                  const reason = unavailableReason(event);
                  return (
                    <tr key={event.event_id}>
                      <td>
                        <Checkbox
                          id={`replay-select-${event.event_id}`}
                          labelText={`Select audit event ${event.event_id}`}
                          hideLabel
                          checked={selected.includes(event.event_id)}
                          disabled={disabled || Boolean(reason)}
                          onChange={(_, { checked }) =>
                            onChange(
                              checked
                                ? [...new Set([...selected, event.event_id])]
                                : selected.filter((id) => id !== event.event_id),
                            )
                          }
                        />
                      </td>
                      <td>{event.event_id}</td>
                      <td>{formatAuditDate(event.timestamp)}</td>
                      <td>{event.namespace ?? 'default'}</td>
                      <td>{event.operation}</td>
                      <td>{event.target ?? '—'}</td>
                      <td>{reason ?? 'Ready for replay'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {results.events.length === 0 && <p>No audit events match this search.</p>}
          {!query.eventId && (
            <div className="policy-simulation__buttons">
              <Button
                kind="ghost"
                disabled={disabled || query.offset === 0}
                onClick={() =>
                  setQuery({ ...query, offset: Math.max(0, query.offset - PAGE_SIZE) })
                }
              >
                Previous events
              </Button>
              <span>{results.total} matching events</span>
              <Button
                kind="ghost"
                disabled={disabled || query.offset + PAGE_SIZE >= results.total}
                onClick={() => setQuery({ ...query, offset: query.offset + PAGE_SIZE })}
              >
                Next events
              </Button>
            </div>
          )}
        </>
      )}
      <p>{selected.length} audit events selected</p>
      {selected.length > 0 && (
        <ul>
          {selected.map((id) => (
            <li key={id}>
              {id}{' '}
              <Button
                kind="ghost"
                size="sm"
                disabled={disabled}
                onClick={() => onChange(selected.filter((entry) => entry !== id))}
              >
                Remove audit event {id}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
};
