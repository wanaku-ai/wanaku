import { useCallback } from "react";
import {
  getApiV1ManagementTargetsToolsList,
  postApiV1ToolsAdd,
  getApiV1ToolsList,
  putApiV1ToolsRemove,
  getApiV1ManagementTargetsToolsListResponse,
  postApiV1ToolsAddResponse,
  getApiV1ToolsListResponse,
  putApiV1ToolsRemoveResponse
} from "../../api/wanaku-router-api";
import {
  PutApiV1ToolsRemoveParams,
  ToolReference,
} from "../../models";

export const useTools = () => {
  /**
   * List management tools.
   */
  const listManagementTools = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1ManagementTargetsToolsListResponse> => {
      return getApiV1ManagementTargetsToolsList(options);
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
    (options?: RequestInit): Promise<getApiV1ToolsListResponse> => {
      return getApiV1ToolsList(options);
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
