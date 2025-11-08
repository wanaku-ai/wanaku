import { useCallback } from "react";
import {
  getApiV1CapabilitiesToolsList,
  postApiV1Tools,
  getApiV1Tools,
  deleteApiV1ToolsToolName,
  getApiV1CapabilitiesToolsListResponse,
  postApiV1ToolsResponse,
  getApiV1ToolsResponse,
  deleteApiV1ToolsToolNameResponse
} from "../../api/wanaku-router-api";
import {
  ToolReference,
} from "../../models";

export const useTools = () => {
  /**
   * List management tools.
   */
  const listManagementTools = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1CapabilitiesToolsListResponse> => {
      return getApiV1CapabilitiesToolsList(options);
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

  /**
   * List tools.
   */
  const listTools = useCallback(
    (options?: RequestInit): Promise<getApiV1ToolsResponse> => {
      return getApiV1Tools(options);
    },
    []
  );

  /**
   * Remove a tool.
   */
  const removeTool = useCallback(
    (
      toolName: string, // The name/ID of the tool to remove
      options?: RequestInit
    ): Promise<deleteApiV1ToolsToolNameResponse> => {
      return deleteApiV1ToolsToolName(toolName, options);
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
