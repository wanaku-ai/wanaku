import { useCallback } from "react";
import {
  getApiV1CapabilitiesResourcesList,
  postApiV1Resources,
  getApiV1Resources,
  deleteApiV1ResourcesResourceName,
  postApiV1ResourcesResponse,
  getApiV1ResourcesResponse,
  deleteApiV1ResourcesResourceNameResponse, getApiV1CapabilitiesResourcesListResponse
} from "../../api/wanaku-router-api";
import {
  ResourceReference,
} from "../../models";

export const useResources = () => {


  /**
   * List management resources.
   */
  const listManagementResources = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1CapabilitiesResourcesListResponse> => {
      return getApiV1CapabilitiesResourcesList(options);
    },
    []
  );

  /**
   * Expose a resource.
   */
  const exposeResource = useCallback(
    (
      resourceReference: ResourceReference, // Define the proper type from your models if available.
      options?: RequestInit
    ): Promise<postApiV1ResourcesResponse> => {
      return postApiV1Resources(resourceReference, options);
    },
    []
  );

  /**
   * List resources.
   */
  const listResources = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1ResourcesResponse> => {
      return getApiV1Resources(options);
    },
    []
  );

  /**
   * Remove a resource.
   */
  const removeResource = useCallback(
    (
      resourceName: string, // The name/ID of the resource to remove
      options?: RequestInit
    ): Promise<deleteApiV1ResourcesResourceNameResponse> => {
      return deleteApiV1ResourcesResourceName(resourceName, options);
    },
    []
  );

  return {
    listManagementResources,
    exposeResource,
    listResources,
    removeResource,
  };
};