import { useCallback } from "react";
import {
  getApiV1Capabilities,
  postApiV1ToolsAdd,
  getApiV1ToolsList,
  putApiV1ToolsRemove,
  getApiV1CapabilitiesResponse,
  postApiV1ToolsAddResponse,
  getApiV1ToolsListResponse,
  putApiV1ToolsRemoveResponse
} from "../../api/wanaku-router-api";
import {
  PutApiV1ToolsRemoveParams,
  ToolReference,
  GetApiV1ToolsListParams,
} from "../../models";

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
      params?: PutApiV1ToolsRemoveParams,
      options?: RequestInit
    ): Promise<putApiV1ToolsRemoveResponse> => {
      return putApiV1ToolsRemove(params, options);
    },
    []
  );

  return {
    listManagementTools,
    addTool,
    listTools,
    removeTool,
  };
};
