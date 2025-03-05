import { useCallback } from "react";
import {
  putApiV1ManagementTargetsResourcesConfigureService,
  putApiV1ManagementTargetsResourcesLink,
  getApiV1ManagementTargetsResourcesList,
  putApiV1ManagementTargetsResourcesUnlink,
  postApiV1ResourcesExpose,
  getApiV1ResourcesList,
  putApiV1ResourcesRemove,
  putApiV1ManagementTargetsResourcesConfigureServiceResponse,
  putApiV1ManagementTargetsResourcesLinkResponse,
  getApiV1ManagementTargetsResourcesListResponse,
  putApiV1ManagementTargetsResourcesUnlinkResponse,
  postApiV1ResourcesExposeResponse,
  getApiV1ResourcesListResponse,
  putApiV1ResourcesRemoveResponse
} from "../../api/wanaku-router-api";
import {
  PutApiV1ManagementTargetsResourcesConfigureServiceParams,
  PutApiV1ManagementTargetsResourcesLinkParams,
  PutApiV1ManagementTargetsResourcesUnlinkParams,
  PutApiV1ResourcesRemoveParams,
  ResourceReference,
} from "../../models";

export const useResources = () => {
  /**
   * Configure a resource service.
   */
  const configureService = useCallback(
    (
      service: string,
      params?: PutApiV1ManagementTargetsResourcesConfigureServiceParams,
      options?: RequestInit
    ): Promise<putApiV1ManagementTargetsResourcesConfigureServiceResponse> => {
      return putApiV1ManagementTargetsResourcesConfigureService(service, params, options);
    },
    []
  );

  /**
   * Link a resource.
   */
  const linkResource = useCallback(
    (
      params?: PutApiV1ManagementTargetsResourcesLinkParams,
      options?: RequestInit
    ): Promise<putApiV1ManagementTargetsResourcesLinkResponse> => {
      return putApiV1ManagementTargetsResourcesLink(params, options);
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
   * Unlink a resource.
   */
  const unlinkResource = useCallback(
    (
      params?: PutApiV1ManagementTargetsResourcesUnlinkParams,
      options?: RequestInit
    ): Promise<putApiV1ManagementTargetsResourcesUnlinkResponse> => {
      return putApiV1ManagementTargetsResourcesUnlink(params, options);
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
    configureService,
    linkResource,
    listManagementResources,
    unlinkResource,
    exposeResource,
    listResources,
    removeResource,
  };
};