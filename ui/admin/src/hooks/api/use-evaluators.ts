import {
  activateEvaluatorRevision,
  getActiveEvaluatorRevision,
  getEvaluatorRevision,
  listEvaluatorLlmConnections,
  listEvaluatorRevisions,
  updateEvaluators,
} from '../../api/wanaku-router-api';
import { ApiError } from '../../custom-fetch';
import type { EvaluatorRevisionResponse, RevisionMetadata } from '../../models';
import { revisionErrorKind, type RevisionErrorKind } from '../../utils/revision-error';

export type {
  EvaluatorDef,
  EvaluatorRevisionResponse as EvaluatorRevision,
  RevisionMetadata,
} from '../../models';

/** The active-revision state. `absent` means that no evaluator revision is active. */
export type ActiveEvaluatorRevision =
  | { state: 'active'; revision: EvaluatorRevisionResponse }
  | { state: 'absent' };

export type EvaluatorErrorKind = RevisionErrorKind;

export const evaluatorErrorKind = revisionErrorKind;

/** Loads the active revision. Only a 404 from the active-revision endpoint means `absent`. */
export const getActiveRevision = async (): Promise<ActiveEvaluatorRevision> => {
  try {
    const response = await getActiveEvaluatorRevision();
    if (response.status === 200) return { state: 'active', revision: response.data };
    return { state: 'absent' };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return { state: 'absent' };
    throw error;
  }
};

/** Lists the metadata of every kept revision, including superseded and rejected revisions. */
export const listRevisions = async (): Promise<RevisionMetadata[]> => {
  const response = await listEvaluatorRevisions();
  return response.data;
};

/** Loads one revision. A missing revision rejects with an `ApiError` with status 404. */
export const getRevision = async (id: number): Promise<EvaluatorRevisionResponse> => {
  const response = await getEvaluatorRevision(id);
  if (response.status !== 200) {
    throw new ApiError(response.data.error, response.status, response.data.data);
  }
  return response.data;
};

/**
 * Activates `evaluators` as a new revision. `expectedRevision` is the revision that the
 * evaluators are based on. The server rejects the update with an `ApiError` with status 409 when
 * another revision is active, and with status 422 when it rejects the configuration.
 */
export const saveEvaluators = async (
  evaluators: EvaluatorRevisionResponse['evaluators'],
  expectedRevision?: number,
): Promise<EvaluatorRevisionResponse> => {
  const response = await updateEvaluators({
    evaluators,
    expected_revision: expectedRevision ?? null,
  });
  if (response.status !== 200) {
    throw new ApiError(response.data.error, response.status, response.data.data);
  }
  return response.data;
};

/** Activates the configuration of a historical revision as a new revision. */
export const activateRevision = async (
  id: number,
  expectedRevision?: number,
): Promise<EvaluatorRevisionResponse> => {
  const response = await activateEvaluatorRevision(id, {
    expected_revision: expectedRevision ?? null,
  });
  if (response.status !== 200) {
    throw new ApiError(response.data.error, response.status, response.data.data);
  }
  return response.data;
};

export const listLlmConnections = () => listEvaluatorLlmConnections();

export const useEvaluators = () => ({
  listLlmConnections,
  getActiveRevision,
  listRevisions,
  getRevision,
  saveEvaluators,
  activateRevision,
});
