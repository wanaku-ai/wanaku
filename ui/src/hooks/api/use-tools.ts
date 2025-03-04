import { useCallback } from "react";
import type { AxiosRequestConfig, AxiosResponse } from "axios";
import {
  putApiV1ManagementTargetsToolsConfigureService,
  putApiV1ManagementTargetsToolsLink,
  getApiV1ManagementTargetsToolsList,
  putApiV1ManagementTargetsToolsUnlink,
  postApiV1ToolsAdd,
  getApiV1ToolsList,
  putApiV1ToolsRemove,
  GetApiV1ManagementTargetsToolsListResult,
  GetApiV1ToolsListResult,
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
      options?: AxiosRequestConfig
    ): Promise<AxiosResponse<void>> => {
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
      options?: AxiosRequestConfig
    ): Promise<AxiosResponse<void>> => {
      return putApiV1ManagementTargetsToolsLink(params, options);
    },
    []
  );

  /**
   * List management tools.
   */
  const listManagementTools = useCallback(
    (
      options?: AxiosRequestConfig
    ): Promise<GetApiV1ManagementTargetsToolsListResult> => {
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
      options?: AxiosRequestConfig
    ): Promise<AxiosResponse<void>> => {
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
      options?: AxiosRequestConfig
    ): Promise<AxiosResponse<void>> => {
      return postApiV1ToolsAdd(toolReference, options);
    },
    []
  );

  /**
   * List tools.
   */
  const listTools = useCallback(
    (options?: AxiosRequestConfig): Promise<GetApiV1ToolsListResult> => {
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
      options?: AxiosRequestConfig
    ): Promise<AxiosResponse<void>> => {
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
