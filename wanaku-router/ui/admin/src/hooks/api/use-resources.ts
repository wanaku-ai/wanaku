import {useCallback} from "react";
import {
    getApiV1Capabilities,
    getApiV1CapabilitiesResponse,
    getApiV1ResourcesList,
    getApiV1ResourcesListResponse,
    postApiV1DataStoreUpdateResponse,
    postApiV1ResourcesExpose,
    postApiV1ResourcesExposeResponse,
    postApiV1ResourcesUpdate,
    putApiV1ResourcesRemove,
    putApiV1ResourcesRemoveResponse
} from "../../api/wanaku-router-api";
import {PutApiV1ResourcesRemoveParams, ResourceReference,} from "../../models";

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
      resourceReference: ResourceReference, // Define the proper type from your models if available.
      options?: RequestInit
    ): Promise<postApiV1ResourcesExposeResponse> => {
      return postApiV1ResourcesExpose(resourceReference, options);
    },
    []
  );

  const updateResource = useCallback(
    (
      resource: ResourceReference,
      options?: RequestInit
    ): Promise<postApiV1DataStoreUpdateResponse> => {
      return postApiV1ResourcesUpdate(resource, options)
    },
    []
  )

  /**
   * List resources.
   */
  const listResources = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1ResourcesListResponse> => {
      return getApiV1ResourcesList(undefined, options);
    },
    []
  );

  /**
   * Remove a resource.
   */
  const removeResource = useCallback(
    (
      params?: PutApiV1ResourcesRemoveParams, // Replace with the actual type if available from models.
      options?: RequestInit
    ): Promise<putApiV1ResourcesRemoveResponse> => {
      return putApiV1ResourcesRemove(params, options);
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