import { useCallback } from "react";
import {
  getApiV1CapabilitiesToolsList,
  getApiV1CapabilitiesToolsState,
  getApiV1CapabilitiesResourcesState,
  getApiV1CapabilitiesResourcesList,
  getApiV1CapabilitiesToolsListResponse,
  getApiV1CapabilitiesResourcesListResponse,
  getApiV1CapabilitiesToolsStateResponse,
  getApiV1CapabilitiesResourcesStateResponse,
} from "../../api/wanaku-router-api";

export const useCapabilities = () => {
  /**
   * List management tools.
   */
  const listManagementTools = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1CapabilitiesToolsListResponse> => {
      return getApiV1CapabilitiesToolsList(undefined, options);
    },
    []
  );

  /**
   * List management tools state.
   */
  const listManagementToolsState = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1CapabilitiesToolsStateResponse> => {
      return getApiV1CapabilitiesToolsState(options);
    },
    []
  );

  /**
   * List management resources.
   */
  const listManagementResources = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1CapabilitiesResourcesListResponse> => {
      return getApiV1CapabilitiesResourcesList(undefined, options);
    },
    []
  );

  /**
   * List management resources state.
   */
  const listManagementResourcesState = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1CapabilitiesResourcesStateResponse> => {
      return getApiV1CapabilitiesResourcesState(options);
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