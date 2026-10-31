import { useCallback } from 'react';
import {
  listEvaluatorLlmConnections,
  listEvaluators as listEvaluatorsRequest,
  updateEvaluators as updateEvaluatorsRequest,
  type listEvaluatorLlmConnectionsResponse,
  type listEvaluatorsResponse,
  type updateEvaluatorsResponse,
  type updateEvaluatorsResponseSuccess,
} from '../../api/wanaku-router-api';
import type { EvaluatorDef } from '../../models';

export type { EvaluatorDef } from '../../models';

export type EvaluatorsResponse = listEvaluatorsResponse;
export type SimpleResponse = updateEvaluatorsResponse;
export type LlmConnectionsResponse = listEvaluatorLlmConnectionsResponse;

export const useEvaluators = () => {
  const listEvaluators = useCallback(
    (options?: RequestInit): Promise<EvaluatorsResponse> => listEvaluatorsRequest(options),
    [],
  );

  const updateEvaluators = useCallback(
    async (
      evaluators: EvaluatorDef[],
      options?: RequestInit,
    ): Promise<updateEvaluatorsResponseSuccess> => {
      const response: updateEvaluatorsResponse = await updateEvaluatorsRequest(
        { evaluators },
        options,
      );
      if (response.status !== 200)
        throw new Error(`Failed to update evaluators (${response.status})`);
      return response;
    },
    [],
  );

  const listLlmConnections = useCallback(
    (options?: RequestInit): Promise<LlmConnectionsResponse> =>
      listEvaluatorLlmConnections(options),
    [],
  );

  return {
    listEvaluators,
    updateEvaluators,
    listLlmConnections,
  };
};
