import { useCallback } from "react";
import { customFetch } from "../../custom-fetch";

export type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };

export interface MatchExpression {
  matcher: "exact" | "glob" | "prefix";
  value: string;
}

export interface PolicySelectors {
  namespace?: string;
  operation?: string;
  target_type?: "tool" | "resource" | "prompt";
  target_name?: MatchExpression;
  labels?: Record<string, string>;
  uri?: MatchExpression;
}

export interface PolicyPredicate {
  operator: "exists" | "equals" | "not_equals" | "one_of" | "not_one_of";
  pointer: string;
  value?: JsonValue;
  values?: JsonValue[];
}

export interface ActionPolicyRule {
  id: string;
  description?: string;
  effect: "allow" | "deny";
  selectors: PolicySelectors;
  predicates?: PolicyPredicate[];
  reason_code?: string;
  message?: string;
  metadata?: Record<string, JsonValue>;
}

export interface ActionPolicy {
  rules: ActionPolicyRule[];
}

export interface RevisionMetadata {
  id: number;
  created_at: string;
  activated_at?: string;
  status: "active" | "superseded" | "rejected";
  checksum: string;
  origin: "startup" | "api";
  actor?: string;
  failure_reason?: string;
}

export interface ActionPolicyRevision {
  revision: RevisionMetadata;
  policy: ActionPolicy;
}

interface ApiResponse<T> {
  status: number;
  data: T;
  headers: Headers;
}

export const useActionPolicies = () => {
  const getActivePolicy = useCallback(
    () => customFetch<ApiResponse<ActionPolicyRevision>>("/api/v1/action-policies", { method: "GET" }),
    [],
  );

  const listRevisions = useCallback(
    () => customFetch<ApiResponse<RevisionMetadata[]>>("/api/v1/action-policies/revisions", { method: "GET" }),
    [],
  );

  const getRevision = useCallback(
    (id: number) => customFetch<ApiResponse<ActionPolicyRevision>>(`/api/v1/action-policies/revisions/${id}`, { method: "GET" }),
    [],
  );

  return { getActivePolicy, listRevisions, getRevision };
};
