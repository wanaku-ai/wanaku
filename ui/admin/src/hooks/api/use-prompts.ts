import { useCallback } from 'react';
import {
  disablePrompt,
  enablePrompt,
  listPrompts as apiListPrompts,
  listPromptsResponse,
} from '../../api/wanaku-router-api';

export const usePrompts = () => {
  const listPrompts = useCallback((options?: RequestInit): Promise<listPromptsResponse> => {
    return apiListPrompts(options);
  }, []);

  /**
   * Enable or disable a prompt. MCP clients cannot list or use a disabled prompt.
   */
  const setPromptEnabled = useCallback(
    (name: string, namespace: string, enabled: boolean, options?: RequestInit) => {
      return enabled
        ? enablePrompt(name, { namespace }, options)
        : disablePrompt(name, { namespace }, options);
    },
    [],
  );

  return {
    listPrompts,
    setPromptEnabled,
  };
};
