import {useCallback} from "react";
import {
    deleteApiV1PromptsName,
    deleteApiV1PromptsNameResponse,
    getApiV1Prompts,
    getApiV1PromptsResponse,
    putApiV1Prompts,
    putApiV1PromptsResponse,
} from "../../api/wanaku-router-api";
import {PromptReference,} from "../../models";

export const usePrompts = () => {
  const listPrompts = useCallback(
    (options?: RequestInit): Promise<getApiV1PromptsResponse> => {
      return getApiV1Prompts(options);
    },
    []
  );

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
    updatePrompt,
    removePrompt,
  };
};
