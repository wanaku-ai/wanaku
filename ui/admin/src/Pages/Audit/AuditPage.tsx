import { Button, InlineNotification, Tag } from "@carbon/react";
import { Renew } from "@carbon/icons-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorNotification } from "../../components/ErrorNotification";
import { PageSkeleton } from "../../components/PageSkeleton";
import { useAudit, type AuditEvent, type AuditHealth, type AuditPage as AuditPageData } from "../../hooks/api/use-audit";
import { AuditDetailModal } from "./AuditDetailModal";
import { AuditFilters } from "./AuditFilters";
import { AuditTable } from "./AuditTable";
import { EMPTY_AUDIT_FILTERS, toAuditParams, type AuditFilterValues } from "./audit-utils";
import "./AuditPage.scss";

type EventsState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "success"; data: AuditPageData };

type HealthState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "success"; data: AuditHealth };

const DEFAULT_LIMIT = 50;

const errorMessage = (error: unknown, fallback: string): string =>
  error instanceof Error ? error.message : fallback;

export const AuditPage = () => {
  const { getEvent, getHealth, getSchema, listEvents } = useAudit();
  const [draftFilters, setDraftFilters] = useState<AuditFilterValues>(EMPTY_AUDIT_FILTERS);
  const [activeFilters, setActiveFilters] = useState<AuditFilterValues>(EMPTY_AUDIT_FILTERS);
  const [offset, setOffset] = useState(0);
  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [eventsState, setEventsState] = useState<EventsState>({ status: "loading" });
  const [healthState, setHealthState] = useState<HealthState>({ status: "loading" });
  const [schemaVersion, setSchemaVersion] = useState<string>();
  const [selectedEvent, setSelectedEvent] = useState<AuditEvent>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string>();
  const eventsRequest = useRef(0);
  const detailRequest = useRef(0);

  const loadEvents = useCallback(async () => {
    const request = ++eventsRequest.current;
    setEventsState((current) => current.status === "success" ? current : { status: "loading" });
    try {
      const response = await listEvents(toAuditParams(activeFilters, offset, limit));
      if (request === eventsRequest.current) {
        setEventsState({ status: "success", data: response.data });
      }
    } catch (error) {
      if (request === eventsRequest.current) {
        setEventsState({ status: "error", message: errorMessage(error, "Failed to load audit events") });
      }
    }
  }, [activeFilters, limit, listEvents, offset]);

  const loadHealth = useCallback(async () => {
    try {
      const response = await getHealth();
      setHealthState({ status: "success", data: response.data });
    } catch (error) {
      setHealthState({ status: "error", message: errorMessage(error, "Failed to load audit health") });
    }
  }, [getHealth]);

  useEffect(() => { void loadEvents(); }, [loadEvents]);
  useEffect(() => { void loadHealth(); }, [loadHealth]);
  useEffect(() => {
    getSchema()
      .then(({ data }) => setSchemaVersion(data.schema_version))
      .catch(() => setSchemaVersion(undefined));
  }, [getSchema]);

  const refresh = () => {
    void Promise.all([loadEvents(), loadHealth()]);
  };

  const applyFilters = () => {
    setOffset(0);
    setActiveFilters({ ...draftFilters });
  };

  const clearFilters = () => {
    setDraftFilters(EMPTY_AUDIT_FILTERS);
    setActiveFilters(EMPTY_AUDIT_FILTERS);
    setOffset(0);
  };

  const openEvent = async (event: AuditEvent) => {
    const request = ++detailRequest.current;
    setSelectedEvent(event);
    setDetailError(undefined);
    setDetailLoading(true);
    try {
      const response = await getEvent(event.event_id);
      if (request === detailRequest.current) {
        setSelectedEvent(response.data);
      }
    } catch (error) {
      if (request !== detailRequest.current) return;
      const message = errorMessage(error, "Failed to load the audit event");
      setSelectedEvent(undefined);
      setDetailError(/not found|404/i.test(message) ? "This audit event is no longer retained." : message);
    } finally {
      if (request === detailRequest.current) {
        setDetailLoading(false);
      }
    }
  };

  const closeEvent = () => {
    detailRequest.current += 1;
    setDetailLoading(false);
    setSelectedEvent(undefined);
  };

  if (eventsState.status === "loading") return <PageSkeleton title="Audit" />;

  return (
    <div className="audit-page">
      <div className="audit-page__heading">
        <div>
          <h1 className="title" data-testid="audit-page-title">Audit</h1>
          <p className="description">Inspect retained decisions, request context, and audit-store health.</p>
        </div>
        <Button kind="ghost" renderIcon={Renew} onClick={refresh}>Refresh</Button>
      </div>

      <section className="audit-health" aria-label="Audit store health" data-testid="audit-health">
        {healthState.status === "loading" && <Tag type="gray">Checking health</Tag>}
        {healthState.status === "error" && (
          <InlineNotification
            kind="warning"
            title="Audit health is unavailable"
            subtitle={healthState.message}
            lowContrast
            hideCloseButton
          />
        )}
        {healthState.status === "success" && (
          <>
            <Tag type={healthState.data.healthy ? "green" : "red"}>
              {healthState.data.healthy ? "Healthy" : "Degraded"}
            </Tag>
            <span>{healthState.data.retained_events} retained</span>
            <span>{healthState.data.dropped_events} dropped</span>
            <span>Capacity {healthState.data.capacity}</span>
            {schemaVersion && <span>Schema {schemaVersion}</span>}
            {!healthState.data.healthy && healthState.data.last_error && (
              <InlineNotification
                kind="warning"
                title="Audit storage is degraded"
                subtitle={healthState.data.last_error}
                lowContrast
                hideCloseButton
              />
            )}
          </>
        )}
      </section>

      {detailError && <ErrorNotification errorMessage={detailError} onClose={() => setDetailError(undefined)} />}

      <div id="page-content" className="audit-page__content">
        <AuditFilters
          disabled={detailLoading}
          values={draftFilters}
          onChange={setDraftFilters}
          onClear={clearFilters}
          onSubmit={applyFilters}
        />

        {eventsState.status === "error" ? (
          <div className="audit-page__error">
            <ErrorNotification errorMessage={eventsState.message} onClose={() => void loadEvents()} />
            <Button kind="tertiary" onClick={() => void loadEvents()}>Retry</Button>
          </div>
        ) : (
          <AuditTable
            events={eventsState.data.events}
            limit={eventsState.data.limit}
            offset={eventsState.data.offset}
            total={eventsState.data.total}
            onSelect={(event) => void openEvent(event)}
            onPageChange={(nextOffset, nextLimit) => {
              setOffset(nextOffset);
              setLimit(nextLimit);
            }}
          />
        )}
      </div>

      {selectedEvent && <AuditDetailModal event={selectedEvent} onRequestClose={closeEvent} />}
    </div>
  );
};
