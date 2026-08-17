import {useCallback} from "react";
import {
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
    (originalName: string, tool: ToolReference, options?: RequestInit): Promise<putApiV1ToolsNameResponse> => {
      return putApiV1ToolsName(originalName, tool, options)
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
    addTool,
    updateTool,
    listTools,
    removeTool,
  };
};
