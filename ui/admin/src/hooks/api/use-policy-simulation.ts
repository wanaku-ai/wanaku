import { useCallback } from 'react';
import {
  validatePolicyCandidate,
  simulatePolicyAction,
  startPolicyReplay,
  getPolicyReplay,
  cancelPolicyReplay,
  deletePolicyReplay,
  getEffectiveActionPolicy,
  getActiveEvaluatorRevision,
} from '../../api/wanaku-router-api';
import { ApiError } from '../../custom-fetch';
import type { CandidateSelection, SyntheticAction, ReplayRequest } from '../../models';

export const usePolicySimulation = () => {
  const getBaseRevisions = useCallback(async (signal?: AbortSignal) => {
    const readRevision = async (
      load: typeof getEffectiveActionPolicy | typeof getActiveEvaluatorRevision,
    ) => {
      try {
        const response = await load({ signal });
        if (response.status === 404) return 0;
        if (response.status !== 200) throw new Error('Failed to load current base revisions.');
        return response.data.revision.id;
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 404) return 0;
        throw cause;
      }
    };
    const [policy, evaluator] = await Promise.all([
      readRevision(getEffectiveActionPolicy),
      readRevision(getActiveEvaluatorRevision),
    ]);
    return { policy, evaluator };
  }, []);

  const validate = useCallback(async (candidate: CandidateSelection) => {
    const response = await validatePolicyCandidate({ candidate });
    if (response.status !== 200) throw new Error('Candidate validation failed');
    return response.data;
  }, []);
  const simulate = useCallback(async (candidate: CandidateSelection, action: SyntheticAction) => {
    const response = await simulatePolicyAction({
      candidate,
      action,
      allow_external_evaluators: false,
    });
    if (response.status !== 200) throw new Error('Action simulation failed');
    return response.data;
  }, []);
  const replay = useCallback(
    async (request: ReplayRequest) => {
      try {
        const response = await startPolicyReplay({ ...request, allow_external_evaluators: false });
        if (response.status !== 202) throw new Error('Replay could not start');
        return response.data;
      } catch (cause) {
        if (
          cause instanceof ApiError &&
          cause.message === 'active_configuration_invalid' &&
          !cause.data
        ) {
          const report = await validate({});
          throw new ApiError(cause.message, cause.status, {
            reason_code: cause.message,
            validation: report,
          });
        }
        throw cause;
      }
    },
    [validate],
  );
  const getReplay = useCallback(async (id: string, signal?: AbortSignal) => {
    const response = await getPolicyReplay(id, { signal });
    if (response.status !== 200) throw new Error('Replay result is unavailable');
    return response.data;
  }, []);
  const cancelReplay = useCallback(async (id: string) => {
    const response = await cancelPolicyReplay(id);
    if (response.status !== 200) throw new Error('Replay could not be cancelled');
    return response.data;
  }, []);
  const deleteReplay = useCallback(async (id: string) => {
    await deletePolicyReplay(id);
  }, []);
  return { validate, simulate, replay, getReplay, cancelReplay, deleteReplay, getBaseRevisions };
};
