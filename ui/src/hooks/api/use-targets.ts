import { useCallback } from "react";
import {
  getApiV1ManagementTargetsToolsList,
  getApiV1ManagementTargetsToolsState,
  getApiV1ManagementTargetsResourcesState,
  getApiV1ManagementTargetsResourcesList,
  getApiV1ManagementTargetsToolsListResponse,
  getApiV1ManagementTargetsResourcesListResponse,
  getApiV1ManagementTargetsToolsStateResponse,
  getApiV1ManagementTargetsResourcesStateResponse,
} from "../../api/wanaku-router-api";

export const useTargets = () => {
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
   * List management tools state.
   */
  const listManagementToolsState = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1ManagementTargetsToolsStateResponse> => {
      return getApiV1ManagementTargetsToolsState(options);
    },
    []
  );

  /**
   * List management resources.
   */
  const listManagementResources = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1ManagementTargetsResourcesListResponse> => {
      return getApiV1ManagementTargetsResourcesList(options);
    },
    []
  );

  /**
   * List management resources state.
   */
  const listManagementResourcesState = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1ManagementTargetsResourcesStateResponse> => {
      return getApiV1ManagementTargetsResourcesState(options);
    },
    []
  );

  return {
    listManagementTools,
    listManagementResources,
    listManagementToolsState,
    listManagementResourcesState,
  };
};
