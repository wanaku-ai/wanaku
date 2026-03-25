import {useCallback} from "react";
import {
    deleteApiV1Prompts,
    deleteApiV1PromptsResponse,
    getApiV1Prompts,
    getApiV1PromptsResponse,
    postApiV1Prompts,
    postApiV1PromptsResponse,
    putApiV1Prompts,
    putApiV1PromptsResponse,
} from "../../api/wanaku-router-api";
import {DeleteApiV1PromptsParams, PromptReference,} from "../../models";

export const usePrompts = () => {
  /**
   * List prompts.
   */
  const listPrompts = useCallback(
    (options?: RequestInit): Promise<getApiV1PromptsResponse> => {
      return getApiV1Prompts(options);
    },
    []
  );

  /**
   * Add a prompt.
   */
  const addPrompt = useCallback(
    (
      promptReference: PromptReference,
      options?: RequestInit
    ): Promise<postApiV1PromptsResponse> => {
      return postApiV1Prompts(promptReference, options);
    },
    []
  );

  /**
   * Update a prompt.
   */
  const updatePrompt = useCallback(
    (
      promptReference: PromptReference,
      options?: RequestInit
    ): Promise<putApiV1PromptsResponse> => {
      return putApiV1Prompts(promptReference, options);
    },
    []
  );

  /**
   * Remove a prompt.
   */
  const removePrompt = useCallback(
    (
      params?: DeleteApiV1PromptsParams,
      options?: RequestInit
    ): Promise<deleteApiV1PromptsResponse> => {
      return deleteApiV1Prompts(params, options);
    },
    []
  );

  return {
    listPrompts,
    addPrompt,
    updatePrompt,
    removePrompt,
  };
};
