import {useCallback} from "react";
import {
    listTools as apiListTools,
    listToolsResponse,
    deleteTool,
    deleteToolResponse,
} from "../../api/wanaku-router-api";

export const useTools = () => {
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
    listTools,
    removeTool,
  };
};
