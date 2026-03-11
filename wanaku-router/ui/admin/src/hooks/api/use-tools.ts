import {useCallback} from "react";
import {
    getApiV1Capabilities,
    getApiV1CapabilitiesResponse,
    getApiV1ToolsList,
    getApiV1ToolsListResponse,
    postApiV1ToolsAdd,
    postApiV1ToolsAddResponse,
    postApiV1ToolsUpdate,
    postApiV1ToolsUpdateResponse,
    deleteApiV1ToolsRemove,
    deleteApiV1ToolsRemoveResponse
} from "../../api/wanaku-router-api";
import {DeleteApiV1ToolsRemoveParams, GetApiV1ToolsListParams, ToolReference,} from "../../models";

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
    ): Promise<postApiV1ToolsAddResponse> => {
      return postApiV1ToolsAdd(toolReference, options);
    },
    []
  );

  const updateTool = useCallback(
    (tool: ToolReference, options?: RequestInit): Promise<postApiV1ToolsUpdateResponse> => {
      return postApiV1ToolsUpdate(tool, options)
    }, []
  )

  /**
   * List tools.
   */
  const listTools = useCallback(
    (params?: GetApiV1ToolsListParams, options?: RequestInit): Promise<getApiV1ToolsListResponse> => {
      return getApiV1ToolsList(params, options);
    },
    []
  );

  /**
   * Remove a tool.
   */
  const removeTool = useCallback(
    (
      params?: DeleteApiV1ToolsRemoveParams,
      options?: RequestInit
    ): Promise<deleteApiV1ToolsRemoveResponse> => {
      return deleteApiV1ToolsRemove(params, options);
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
