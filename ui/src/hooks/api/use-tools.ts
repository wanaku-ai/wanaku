import { useCallback } from "react";
import {
  putApiV1ManagementTargetsToolsConfigureService,
  putApiV1ManagementTargetsToolsLink,
  getApiV1ManagementTargetsToolsList,
  putApiV1ManagementTargetsToolsUnlink,
  postApiV1ToolsAdd,
  getApiV1ToolsList,
  putApiV1ToolsRemove,
  putApiV1ManagementTargetsToolsConfigureServiceResponse,
  putApiV1ManagementTargetsToolsLinkResponse,
  getApiV1ManagementTargetsToolsListResponse,
  putApiV1ManagementTargetsToolsUnlinkResponse,
  postApiV1ToolsAddResponse,
  getApiV1ToolsListResponse,
  putApiV1ToolsRemoveResponse
} from "../../api/wanaku-router-api";
import {
  PutApiV1ManagementTargetsToolsConfigureServiceParams,
  PutApiV1ManagementTargetsToolsLinkParams,
  PutApiV1ManagementTargetsToolsUnlinkParams,
  PutApiV1ToolsRemoveParams,
  ToolReference,
} from "../../models";

export const useTools = () => {
  /**
   * Configure a tool service.
   */
  const configureService = useCallback(
    (
      service: string,
      params?: PutApiV1ManagementTargetsToolsConfigureServiceParams,
      options?: RequestInit
    ): Promise<putApiV1ManagementTargetsToolsConfigureServiceResponse> => {
      return putApiV1ManagementTargetsToolsConfigureService(
        service,
        params,
        options
      );
    },
    []
  );

  /**
   * Link a tool.
   */
  const linkTool = useCallback(
    (
      params?: PutApiV1ManagementTargetsToolsLinkParams,
      options?: RequestInit
    ): Promise<putApiV1ManagementTargetsToolsLinkResponse> => {
      return putApiV1ManagementTargetsToolsLink(params, options);
    },
    []
  );

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
   * Unlink a tool.
   */
  const unlinkTool = useCallback(
    (
      params?: PutApiV1ManagementTargetsToolsUnlinkParams,
      options?: RequestInit
    ): Promise<putApiV1ManagementTargetsToolsUnlinkResponse> => {
      return putApiV1ManagementTargetsToolsUnlink(params, options);
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
    configureService,
    linkTool,
    listManagementTools,
    unlinkTool,
    addTool,
    listTools,
    removeTool,
  };
};
