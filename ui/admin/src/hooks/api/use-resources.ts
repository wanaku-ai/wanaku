import { useCallback } from "react";
import {
  listResources as apiListResources,
  listResourcesResponse,
  deleteResource,
  deleteResourceResponse
} from "../../api/wanaku-router-api";
import { ResourceEntry } from "../../models";

export const useResources = () => {
  const updateResource = useCallback(
    async (
      originalName: string,
      _resource: ResourceEntry,
      options?: RequestInit
    ): Promise<void> => {
      // No PUT endpoint - delete and recreate
      await deleteResource(originalName, options);
      // Note: resources are auto-discovered from forwards, so we can't directly create them
    },
    []
  )

  /**
   * List resources.
   */
  const listResources = useCallback(
    (
      options?: RequestInit
    ): Promise<listResourcesResponse> => {
      return apiListResources(options);
    },
    []
  );

  /**
   * Remove a resource.
   */
  const removeResource = useCallback(
    (
      name: string,
      options?: RequestInit
    ): Promise<deleteResourceResponse> => {
      return deleteResource(name, options);
    },
    []
  );

  return {
    listResources,
    updateResource,
    removeResource,
  };
};