import { useCallback } from "react";
import {
  getApiV1Capabilities,
  getApiV1CapabilitiesResponse,
  getApiV1Resources,
  getApiV1ResourcesResponse,
  postApiV1Resources,
  postApiV1ResourcesResponse,
  putApiV1ResourcesName,
  putApiV1ResourcesNameResponse,
  deleteApiV1ResourcesName,
  deleteApiV1ResourcesNameResponse
} from "../../api/wanaku-router-api";
import { ResourceReference, } from "../../models";

export const useResources = () => {


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
   * Expose a resource.
   */
  const exposeResource = useCallback(
    (
      resourceReference: ResourceReference,
      options?: RequestInit
    ): Promise<postApiV1ResourcesResponse> => {
      return postApiV1Resources(resourceReference, options);
    },
    []
  );

  const updateResource = useCallback(
    (
      resource: ResourceReference,
      options?: RequestInit
    ): Promise<putApiV1ResourcesNameResponse> => {
      if (!resource.name) {
        throw new Error("Resource name is required for update");
      }
      return putApiV1ResourcesName(resource.name, resource, options)
    },
    []
  )

  /**
   * List resources.
   */
  const listResources = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1ResourcesResponse> => {
      return getApiV1Resources(undefined, options);
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
    ): Promise<deleteApiV1ResourcesNameResponse> => {
      return deleteApiV1ResourcesName(name, options);
    },
    []
  );

  return {
    listManagementResources,
    exposeResource,
    listResources,
    updateResource,
    removeResource,
  };
};