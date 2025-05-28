import { useCallback } from "react";
import {
  getApiV1ManagementTargetsResourcesList,
  postApiV1ResourcesExpose,
  getApiV1ResourcesList,
  putApiV1ResourcesRemove,
  getApiV1ManagementTargetsResourcesListResponse,
  postApiV1ResourcesExposeResponse,
  getApiV1ResourcesListResponse,
  putApiV1ResourcesRemoveResponse
} from "../../api/wanaku-router-api";
import {
  PutApiV1ResourcesRemoveParams,
  ResourceReference,
} from "../../models";

export const useResources = () => {


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

  /**
   * List resources.
   */
  const listResources = useCallback(
    (
      options?: RequestInit
    ): Promise<getApiV1ResourcesListResponse> => {
      return getApiV1ResourcesList(options);
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
    removeResource,
  };
};