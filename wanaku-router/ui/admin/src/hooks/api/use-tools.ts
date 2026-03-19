import {useCallback} from "react";
import {
    getApiV1Capabilities,
    getApiV1CapabilitiesResponse,
    getApiV1Tools,
    getApiV1ToolsResponse,
    postApiV1Tools,
    postApiV1ToolsResponse,
    putApiV1ToolsName,
    putApiV1ToolsNameResponse,
    deleteApiV1ToolsName,
    deleteApiV1ToolsNameResponse,
} from "../../api/wanaku-router-api";
import {GetApiV1ToolsParams, ToolReference,} from "../../models";

export const useTools = () => {
  /**
   * List management tools.
   */
  const listManagementTools = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1CapabilitiesResponse> => {
      return getApiV1Capabilities(options);
    },
    []
  );

  /**
   * Add a tool.
   */
  const addTool = useCallback(
    (
      toolReference: ToolReference,
      options?: RequestInit
    ): Promise<postApiV1ToolsResponse> => {
      return postApiV1Tools(toolReference, options);
    },
    []
  );

  const updateTool = useCallback(
    (tool: ToolReference, options?: RequestInit): Promise<putApiV1ToolsNameResponse> => {
      if (!tool.name) {
          throw new Error("Tool name is required for update");
      }
      return putApiV1ToolsName(tool.name, tool, options)
    }, []
  )

  /**
   * List tools.
   */
  const listTools = useCallback(
    (params?: GetApiV1ToolsParams, options?: RequestInit): Promise<getApiV1ToolsResponse> => {
      return getApiV1Tools(params, options);
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
    ): Promise<deleteApiV1ToolsNameResponse> => {
      return deleteApiV1ToolsName(name, options);
    },
    []
  );

  return {
    listManagementTools,
    addTool,
    updateTool,
    listTools,
    removeTool,
  };
};
