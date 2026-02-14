import {useCallback} from "react";
import {
    getApiV1Capabilities,
    getApiV1CapabilitiesResourcesState,
    getApiV1CapabilitiesResourcesStateResponse,
    getApiV1CapabilitiesResponse,
    getApiV1CapabilitiesToolsState,
    getApiV1CapabilitiesToolsStateResponse,
} from "../../api/wanaku-router-api";

export const useCapabilities = () => {
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
    ): Promise<getApiV1CapabilitiesResponse> => {
      return getApiV1Capabilities(options);
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