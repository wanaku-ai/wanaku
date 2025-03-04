import { useCallback } from "react";
import type { AxiosRequestConfig, AxiosResponse } from "axios";
import {
  putApiV1ManagementTargetsResourcesConfigureService,
  putApiV1ManagementTargetsResourcesLink,
  getApiV1ManagementTargetsResourcesList,
  putApiV1ManagementTargetsResourcesUnlink,
  postApiV1ResourcesExpose,
  getApiV1ResourcesList,
  putApiV1ResourcesRemove,
  GetApiV1ResourcesListResult,
  GetApiV1ManagementTargetsResourcesListResult,
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
      options?: AxiosRequestConfig
    ): Promise<AxiosResponse<void>> => {
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
      options?: AxiosRequestConfig
    ): Promise<AxiosResponse<void>> => {
      return putApiV1ManagementTargetsResourcesLink(params, options);
    },
    []
  );

  /**
   * List management resources.
   */
  const listManagementResources = useCallback(
    (
      options?: AxiosRequestConfig
    ): Promise<GetApiV1ManagementTargetsResourcesListResult> => {
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
      options?: AxiosRequestConfig
    ): Promise<AxiosResponse<void>> => {
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
      options?: AxiosRequestConfig
    ): Promise<AxiosResponse<void>> => {
      return postApiV1ResourcesExpose(resourceReference, options);
    },
    []
  );

  /**
   * List resources.
   */
  const listResources = useCallback(
    (
      options?: AxiosRequestConfig
    ): Promise<GetApiV1ResourcesListResult> => {
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
      options?: AxiosRequestConfig
    ): Promise<AxiosResponse<void>> => {
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