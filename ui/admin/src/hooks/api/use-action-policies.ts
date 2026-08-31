import { useCallback } from "react";
import {
  getActionPolicyRevision,
  getEffectiveActionPolicy,
  listActionPolicyRevisions,
  type getActionPolicyRevisionResponseSuccess,
  type getEffectiveActionPolicyResponseSuccess,
} from "../../api/wanaku-router-api";

export type {
  ActionPolicy,
  ActionPolicyRevisionResponse as ActionPolicyRevision,
  Predicate as PolicyPredicate,
  RevisionMetadata,
  Rule as ActionPolicyRule,
} from "../../models";

export const useActionPolicies = () => {
  const getActivePolicy = useCallback(async (): Promise<getEffectiveActionPolicyResponseSuccess> => {
    const response = await getEffectiveActionPolicy();
    if (response.status !== 200) throw new Error("No active action policy revision found");
    return response;
  }, []);
  const listRevisions = useCallback(() => listActionPolicyRevisions(), []);
  const getRevision = useCallback(async (id: number): Promise<getActionPolicyRevisionResponseSuccess> => {
    const response = await getActionPolicyRevision(id);
    if (response.status !== 200) throw new Error(`Action policy revision ${id} was not found`);
    return response;
  }, []);

  return { getActivePolicy, listRevisions, getRevision };
};
