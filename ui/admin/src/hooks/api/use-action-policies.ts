import {
  getActionPolicyRevision,
  getEffectiveActionPolicy,
  listActionPolicyRevisions,
} from '../../api/wanaku-router-api';
import { ApiError } from '../../custom-fetch';
import type { ActionPolicyRevisionResponse } from '../../models';

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

/**
 * The action-policy meaning of a failed request. `not_found` is a missing revision,
 * `conflict` is a stale expected revision, `validation` is a rejected policy,
 * `invalid_request` is a malformed request, and `unexpected` is any other failure.
 */
export type ActionPolicyErrorKind =
  | 'not_found'
  | 'conflict'
  | 'validation'
  | 'invalid_request'
  | 'unexpected';

export const actionPolicyErrorKind = (error: unknown): ActionPolicyErrorKind => {
  if (!(error instanceof ApiError)) return 'unexpected';
  switch (error.status) {
    case 400:
      return 'invalid_request';
    case 404:
      return 'not_found';
    case 409:
      return 'conflict';
    case 422:
      return 'validation';
    default:
      return 'unexpected';
  }
};

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

export const useActionPolicies = () => ({ getActivePolicy, listRevisions, getRevision });
