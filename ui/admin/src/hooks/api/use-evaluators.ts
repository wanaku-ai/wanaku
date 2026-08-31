import { useCallback } from "react";
import {
  bindEvaluatorNamespace,
  listEvaluatorBindings,
  listEvaluatorLlmConnections,
  listEvaluators as listEvaluatorsRequest,
  unbindEvaluatorNamespace,
  updateEvaluators as updateEvaluatorsRequest,
  type bindEvaluatorNamespaceResponse,
  type bindEvaluatorNamespaceResponseSuccess,
  type listEvaluatorBindingsResponse,
  type listEvaluatorLlmConnectionsResponse,
  type listEvaluatorsResponse,
  type unbindEvaluatorNamespaceResponse,
  type unbindEvaluatorNamespaceResponseSuccess,
  type updateEvaluatorsResponse,
  type updateEvaluatorsResponseSuccess,
} from "../../api/wanaku-router-api";
import type { EvaluatorDef } from "../../models";

export type { EvaluatorDef } from "../../models";

export type EvaluatorsResponse = listEvaluatorsResponse;
export type BindingsResponse = listEvaluatorBindingsResponse;
export type SimpleResponse = updateEvaluatorsResponse;
export type LlmConnectionsResponse = listEvaluatorLlmConnectionsResponse;

export const useEvaluators = () => {
  const listEvaluators = useCallback(
    (options?: RequestInit): Promise<EvaluatorsResponse> => listEvaluatorsRequest(options),
    [],
  );

  const updateEvaluators = useCallback(
    async (evaluators: EvaluatorDef[], options?: RequestInit): Promise<updateEvaluatorsResponseSuccess> => {
      const response: updateEvaluatorsResponse = await updateEvaluatorsRequest({ evaluators }, options);
      if (response.status !== 200) throw new Error(`Failed to update evaluators (${response.status})`);
      return response;
    },
    [],
  );

  const listLlmConnections = useCallback(
    (options?: RequestInit): Promise<LlmConnectionsResponse> => listEvaluatorLlmConnections(options),
    [],
  );

  const listBindings = useCallback(
    (options?: RequestInit): Promise<BindingsResponse> => listEvaluatorBindings(options),
    [],
  );

  const bindNamespace = useCallback(
    async (
      namespace: string,
      conversationId: string,
      options?: RequestInit,
    ): Promise<bindEvaluatorNamespaceResponseSuccess> => {
      const response: bindEvaluatorNamespaceResponse = await bindEvaluatorNamespace(
        namespace,
        { conversation_id: conversationId },
        options,
      );
      if (response.status !== 200) throw new Error(`Failed to bind namespace (${response.status})`);
      return response;
    },
    [],
  );

  const unbindNamespace = useCallback(
    async (namespace: string, options?: RequestInit): Promise<unbindEvaluatorNamespaceResponseSuccess> => {
      const response: unbindEvaluatorNamespaceResponse = await unbindEvaluatorNamespace(namespace, options);
      return response;
    },
    [],
  );

  return {
    listEvaluators,
    updateEvaluators,
    listLlmConnections,
    listBindings,
    bindNamespace,
    unbindNamespace,
  };
};
