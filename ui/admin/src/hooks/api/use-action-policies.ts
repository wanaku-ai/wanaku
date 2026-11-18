import {
  getActionPolicyRevision,
  getEffectiveActionPolicy,
  listActionPolicyRevisions,
  updateActionPolicy,
} from '../../api/wanaku-router-api';
import { ApiError } from '../../custom-fetch';
import type { ActionPolicy, ActionPolicyRevisionResponse } from '../../models';
import { revisionErrorKind, type RevisionErrorKind } from '../../utils/revision-error';

export type {
  ActionPolicy,
  ActionPolicyRevisionResponse as ActionPolicyRevision,
  Predicate as PolicyPredicate,
  RevisionMetadata,
  Rule as ActionPolicyRule,
} from '../../models';

/** The active-policy state. `absent` means that no policy revision is active. */
export type ActivePolicy =
  | { state: 'active'; revision: ActionPolicyRevisionResponse }
  | { state: 'absent' };

export type ActionPolicyErrorKind = RevisionErrorKind;

export const actionPolicyErrorKind = revisionErrorKind;

/** Loads the active policy. Only a 404 from the active-policy endpoint means `absent`. */
export const getActivePolicy = async (): Promise<ActivePolicy> => {
  try {
    const response = await getEffectiveActionPolicy();
    if (response.status === 200) return { state: 'active', revision: response.data };
    return { state: 'absent' };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return { state: 'absent' };
    throw error;
  }
};

export const listRevisions = () => listActionPolicyRevisions();

/** Loads one revision. A missing revision rejects with an `ApiError` with status 404. */
export const getRevision = async (id: number): Promise<ActionPolicyRevisionResponse> => {
  const response = await getActionPolicyRevision(id);
  if (response.status !== 200) {
    throw new ApiError(response.data.error, response.status, response.data.data);
  }
  return response.data;
};

/**
 * Activates `policy` as a new revision. `expectedRevision` is the revision that the policy is based
 * on. The server rejects the update with an `ApiError` with status 409 when another revision is
 * active, and with status 422 when it rejects the policy.
 */
export const updatePolicy = async (
  policy: ActionPolicy,
  expectedRevision?: number,
): Promise<ActionPolicyRevisionResponse> => {
  const response = await updateActionPolicy({
    policy,
    expected_revision: expectedRevision ?? null,
  });
  if (response.status !== 200) {
    throw new ApiError(response.data.error, response.status, response.data.data);
  }
  return response.data;
};

export const useActionPolicies = () => ({
  getActivePolicy,
  listRevisions,
  getRevision,
  updatePolicy,
});
