import { useCallback } from 'react';
import {
  listTools as apiListTools,
  listToolsResponse,
  disableTool,
  enableTool,
} from '../../api/wanaku-router-api';

export const useTools = () => {
  /**
   * List tools.
   */
  const listTools = useCallback((options?: RequestInit): Promise<listToolsResponse> => {
    return apiListTools(options);
  }, []);

  /**
   * Enable or disable a tool. MCP clients cannot list or use a disabled tool.
   */
  const setToolEnabled = useCallback((name: string, enabled: boolean, options?: RequestInit) => {
    return enabled ? enableTool(name, options) : disableTool(name, options);
  }, []);

  return {
    listTools,
    setToolEnabled,
  };
};
