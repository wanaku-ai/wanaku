import {useCallback} from "react";
import {
    listTools as apiListTools,
    listToolsResponse,
    deleteTool,
    deleteToolResponse,
} from "../../api/wanaku-router-api";
import {ToolEntry} from "../../models";

export const useTools = () => {
  /**
   * Update tool by deleting and recreating (no PUT endpoint).
   */
  const updateTool = useCallback(
    async (originalName: string, _tool: ToolEntry, options?: RequestInit): Promise<void> => {
      await deleteTool(originalName, options);
      // Note: tools are auto-discovered from forwards, so we can't directly create them
      // This is a placeholder to match the API contract
    }, []
  )

  /**
   * List tools.
   */
  const listTools = useCallback(
    (options?: RequestInit): Promise<listToolsResponse> => {
      return apiListTools(options);
    },
    []
  );

  /**
   * Remove a tool.
   */
  const removeTool = useCallback(
    (
      name: string,
      options?: RequestInit
    ): Promise<deleteToolResponse> => {
      return deleteTool(name, options);
    },
    []
  );

  return {
    updateTool,
    listTools,
    removeTool,
  };
};
