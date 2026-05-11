import {useCallback} from "react";
import {
    deleteApiV1PromptsName,
    deleteApiV1PromptsNameResponse,
    getApiV1Prompts,
    getApiV1PromptsResponse,
    postApiV1Prompts,
    postApiV1PromptsResponse,
    putApiV1Prompts,
    putApiV1PromptsResponse,
} from "../../api/wanaku-router-api";
import {PromptReference,} from "../../models";

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
      name: string,
      options?: RequestInit
    ): Promise<deleteApiV1PromptsNameResponse> => {
      return deleteApiV1PromptsName(name, options);
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
