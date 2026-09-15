import { useCallback } from "react";
import {
  getAuditEvent as getAuditEventRequest,
  getAuditHealth as getAuditHealthRequest,
  getAuditSchema as getAuditSchemaRequest,
  listAuditEvents as listAuditEventsRequest,
} from "../../api/wanaku-router-api";
import type { AuditEvent, AuditHealth, AuditPage, ListAuditEventsParams } from "../../models";

export type { AuditEvent, AuditHealth, AuditPage, ListAuditEventsParams } from "../../models";

export interface AuditSchema {
  schema_version: string;
}

interface AuditResponse<T> {
  data: T;
  headers: Headers;
  status: number;
}

const asAuditResponse = <T>(response: unknown): AuditResponse<T> => response as AuditResponse<T>;

export const useAudit = () => {
  const listEvents = useCallback(
    async (params?: ListAuditEventsParams, options?: RequestInit): Promise<AuditResponse<AuditPage>> =>
      asAuditResponse<AuditPage>(await listAuditEventsRequest(params, options)),
    [],
  );

  const getEvent = useCallback(
    async (id: string, options?: RequestInit): Promise<AuditResponse<AuditEvent>> =>
      asAuditResponse<AuditEvent>(await getAuditEventRequest(id, options)),
    [],
  );

  const getHealth = useCallback(
    async (options?: RequestInit): Promise<AuditResponse<AuditHealth>> =>
      asAuditResponse<AuditHealth>(await getAuditHealthRequest(options)),
    [],
  );

  const getSchema = useCallback(
    async (options?: RequestInit): Promise<AuditResponse<AuditSchema>> =>
      asAuditResponse<AuditSchema>(await getAuditSchemaRequest(options)),
    [],
  );

  return { getEvent, getHealth, getSchema, listEvents };
};
