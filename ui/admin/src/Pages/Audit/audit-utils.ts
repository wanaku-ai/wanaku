import type { AuditDecision, ListAuditEventsParams } from "../../models";

export type DecisionTagType = "green" | "red" | "warm-gray" | "magenta" | "gray";

export interface AuditFilterValues {
  correlationId: string;
  decision: "" | AuditDecision;
  from: string;
  namespace: string;
  operation: string;
  reasonCode: string;
  target: string;
  to: string;
}

export const EMPTY_AUDIT_FILTERS: AuditFilterValues = {
  correlationId: "",
  decision: "",
  from: "",
  namespace: "",
  operation: "",
  reasonCode: "",
  target: "",
  to: "",
};

export const getDecisionTagType = (decision: AuditDecision): DecisionTagType => {
  switch (decision) {
    case "allow": return "green";
    case "block": return "red";
    case "warn": return "warm-gray";
    case "reject_malformed": return "magenta";
    case "error": return "gray";
  }
};

const optional = (value: string): string | undefined => value.trim() || undefined;

const toIsoDate = (value: string): string | undefined => {
  if (!value) return undefined;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
};

export const toAuditParams = (
  filters: AuditFilterValues,
  offset: number,
  limit: number,
): ListAuditEventsParams => ({
  correlation_id: optional(filters.correlationId),
  decision: filters.decision || undefined,
  from: toIsoDate(filters.from),
  limit,
  namespace: optional(filters.namespace),
  offset,
  operation: optional(filters.operation),
  reason_code: optional(filters.reasonCode),
  target: optional(filters.target),
  to: toIsoDate(filters.to),
});

export const formatAuditDate = (value: string): string => {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "medium",
  }).format(date);
};
