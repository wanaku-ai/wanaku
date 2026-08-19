import {useCallback} from "react";
import {
    deletePrompt,
    deletePromptResponse,
    listPrompts as apiListPrompts,
    listPromptsResponse,
} from "../../api/wanaku-router-api";
import {PromptEntry} from "../../models";

export const usePrompts = () => {
  const listPrompts = useCallback(
    (options?: RequestInit): Promise<listPromptsResponse> => {
      return apiListPrompts(options);
    },
    []
  );

  const updatePrompt = useCallback(
    async (
      originalName: string,
      _promptEntry: PromptEntry,
      options?: RequestInit
    ): Promise<void> => {
      // No PUT endpoint - delete and recreate
      await deletePrompt(originalName, options);
      // Note: prompts are auto-discovered from forwards, so we can't directly create them
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
    ): Promise<deletePromptResponse> => {
      return deletePrompt(name, options);
    },
    []
  );

  return {
    listPrompts,
    updatePrompt,
    removePrompt,
  };
};
