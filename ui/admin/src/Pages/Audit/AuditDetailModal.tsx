import { ComposedModal, ModalBody, ModalHeader, Tag } from "@carbon/react";
import type { AuditEvent } from "../../models";
import { formatAuditDate, getDecisionTagType } from "./audit-utils";

interface AuditDetailModalProps {
  event: AuditEvent;
  onRequestClose: () => void;
}

interface DetailFieldProps {
  label: string;
  value: unknown;
}

const displayValue = (value: unknown): string => {
  if (value === undefined || value === null || value === "") return "—";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  return String(value);
};

const DetailField = ({ label, value }: DetailFieldProps) => (
  <div className="audit-detail__field">
    <dt>{label}</dt>
    <dd>{displayValue(value)}</dd>
  </div>
);

const JsonBlock = ({ label, value }: DetailFieldProps) => (
  <section className="audit-detail__json" aria-labelledby={`audit-${label.toLowerCase()}-heading`}>
    <h3 id={`audit-${label.toLowerCase()}-heading`}>{label}</h3>
    <pre>{JSON.stringify(value ?? null, null, 2)}</pre>
  </section>
);

export const AuditDetailModal = ({ event, onRequestClose }: AuditDetailModalProps) => (
  <ComposedModal open size="lg" onClose={onRequestClose} selectorPrimaryFocus=".cds--modal-close" data-testid="audit-event-detail">
    <ModalHeader title="Audit event details" label={event.event_id} closeModal={onRequestClose} />
    <ModalBody>
      <section className="audit-detail__section" aria-labelledby="audit-decision-heading">
        <h3 id="audit-decision-heading">Decision</h3>
        <dl className="audit-detail__grid">
          <DetailField label="Category" value={event.category} />
          <div className="audit-detail__field"><dt>Decision</dt><dd><Tag type={getDecisionTagType(event.decision)}>{event.decision}</Tag></dd></div>
          <DetailField label="Reason code" value={event.reason_code} />
          <DetailField label="Explanation" value={event.explanation} />
        </dl>
      </section>
      <section className="audit-detail__section" aria-labelledby="audit-request-heading">
        <h3 id="audit-request-heading">Request</h3>
        <dl className="audit-detail__grid">
          <DetailField label="Timestamp" value={formatAuditDate(event.timestamp)} />
          <DetailField label="Protocol" value={event.protocol} />
          <DetailField label="Operation" value={event.operation} />
          <DetailField label="Target type" value={event.target_type} />
          <DetailField label="Target" value={event.target} />
          <DetailField label="Namespace" value={event.namespace} />
          <DetailField label="Actor" value={event.actor} />
          <DetailField label="Workload" value={event.workload} />
          <DetailField label="Audience" value={event.audience} />
        </dl>
      </section>
      <section className="audit-detail__section" aria-labelledby="audit-correlation-heading">
        <h3 id="audit-correlation-heading">Correlation</h3>
        <dl className="audit-detail__grid">
          <DetailField label="Event ID" value={event.event_id} />
          <DetailField label="Request ID" value={event.request_id} />
          <DetailField label="Conversation ID" value={event.conversation_id} />
          <DetailField label="Correlation ID" value={event.correlation_id} />
          <DetailField label="Stream ID" value={event.stream_id} />
          <DetailField label="Sequence" value={event.sequence} />
        </dl>
      </section>
      <section className="audit-detail__section" aria-labelledby="audit-enforcement-heading">
        <h3 id="audit-enforcement-heading">Enforcement</h3>
        <dl className="audit-detail__grid">
          <DetailField label="Filter" value={event.filter} />
          <DetailField label="Evaluator" value={event.evaluator} />
          <DetailField label="Policy revision" value={event.policy_revision} />
          <DetailField label="Upstream ID" value={event.upstream_id} />
          <DetailField label="Response status" value={event.response_status} />
          <DetailField label="Duration (ms)" value={event.duration_ms} />
        </dl>
      </section>
      <section className="audit-detail__section" aria-labelledby="audit-coverage-heading">
        <h3 id="audit-coverage-heading">Redaction and coverage</h3>
        <dl className="audit-detail__grid">
          <DetailField label="Schema version" value={event.schema_version} />
          <DetailField label="Coverage complete" value={event.coverage_complete} />
          <DetailField label="Dropped events" value={event.dropped_events} />
          <DetailField label="Payload captured" value={event.redaction.payload_captured} />
          <DetailField label="Payload truncated" value={event.redaction.payload_truncated} />
          <DetailField label="Redacted fields" value={event.redaction.redacted_fields.join(", ")} />
        </dl>
      </section>
      <JsonBlock label="Attributes" value={event.attributes} />
      <JsonBlock label="Payload" value={event.payload} />
    </ModalBody>
  </ComposedModal>
);
