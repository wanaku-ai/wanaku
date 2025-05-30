/**
 * Generated by orval v7.6.0 🍺
 * Do not edit manually.
 * wanaku-router API
 * OpenAPI spec version: 0.0.6-SNAPSHOT
 */
import type {
  ForwardReference,
  PutApiV1ManagementTargetsResourcesConfigureServiceParams,
  PutApiV1ManagementTargetsToolsConfigureServiceParams,
  PutApiV1ResourcesRemoveParams,
  PutApiV1ToolsRemoveParams,
  ResourceReference,
  ToolReference,
  WanakuResponseListForwardReference,
  WanakuResponseListResourceReference,
  WanakuResponseListToolReference,
  WanakuResponseMapStringListState,
  WanakuResponseMapStringService,
  WanakuResponseServerInfo,
} from "../models";

import { customFetch } from "../custom-fetch";
/**
 * @summary Add Forward
 */
export type postApiV1ForwardsAddResponse200 = {
  data: void;
  status: 200;
};

export type postApiV1ForwardsAddResponse400 = {
  data: void;
  status: 400;
};

export type postApiV1ForwardsAddResponseComposite =
  | postApiV1ForwardsAddResponse200
  | postApiV1ForwardsAddResponse400;

export type postApiV1ForwardsAddResponse =
  postApiV1ForwardsAddResponseComposite & {
    headers: Headers;
  };

export const getPostApiV1ForwardsAddUrl = () => {
  return `/api/v1/forwards/add`;
};

export const postApiV1ForwardsAdd = async (
  forwardReference: ForwardReference,
  options?: RequestInit,
): Promise<postApiV1ForwardsAddResponse> => {
  return customFetch<postApiV1ForwardsAddResponse>(
    getPostApiV1ForwardsAddUrl(),
    {
      ...options,
      method: "POST",
      headers: { "Content-Type": "application/json", ...options?.headers },
      body: JSON.stringify(forwardReference),
    },
  );
};

/**
 * @summary List Forwards
 */
export type getApiV1ForwardsListResponse200 = {
  data: WanakuResponseListForwardReference;
  status: 200;
};

export type getApiV1ForwardsListResponseComposite =
  getApiV1ForwardsListResponse200;

export type getApiV1ForwardsListResponse =
  getApiV1ForwardsListResponseComposite & {
    headers: Headers;
  };

export const getGetApiV1ForwardsListUrl = () => {
  return `/api/v1/forwards/list`;
};

export const getApiV1ForwardsList = async (
  options?: RequestInit,
): Promise<getApiV1ForwardsListResponse> => {
  return customFetch<getApiV1ForwardsListResponse>(
    getGetApiV1ForwardsListUrl(),
    {
      ...options,
      method: "GET",
    },
  );
};

/**
 * @summary Remove Forward
 */
export type putApiV1ForwardsRemoveResponse200 = {
  data: void;
  status: 200;
};

export type putApiV1ForwardsRemoveResponse400 = {
  data: void;
  status: 400;
};

export type putApiV1ForwardsRemoveResponseComposite =
  | putApiV1ForwardsRemoveResponse200
  | putApiV1ForwardsRemoveResponse400;

export type putApiV1ForwardsRemoveResponse =
  putApiV1ForwardsRemoveResponseComposite & {
    headers: Headers;
  };

export const getPutApiV1ForwardsRemoveUrl = () => {
  return `/api/v1/forwards/remove`;
};

export const putApiV1ForwardsRemove = async (
  forwardReference: ForwardReference,
  options?: RequestInit,
): Promise<putApiV1ForwardsRemoveResponse> => {
  return customFetch<putApiV1ForwardsRemoveResponse>(
    getPutApiV1ForwardsRemoveUrl(),
    {
      ...options,
      method: "PUT",
      headers: { "Content-Type": "application/json", ...options?.headers },
      body: JSON.stringify(forwardReference),
    },
  );
};

/**
 * @summary Update
 */
export type postApiV1ForwardsUpdateResponse200 = {
  data: void;
  status: 200;
};

export type postApiV1ForwardsUpdateResponse400 = {
  data: void;
  status: 400;
};

export type postApiV1ForwardsUpdateResponseComposite =
  | postApiV1ForwardsUpdateResponse200
  | postApiV1ForwardsUpdateResponse400;

export type postApiV1ForwardsUpdateResponse =
  postApiV1ForwardsUpdateResponseComposite & {
    headers: Headers;
  };

export const getPostApiV1ForwardsUpdateUrl = () => {
  return `/api/v1/forwards/update`;
};

export const postApiV1ForwardsUpdate = async (
  forwardReference: ForwardReference,
  options?: RequestInit,
): Promise<postApiV1ForwardsUpdateResponse> => {
  return customFetch<postApiV1ForwardsUpdateResponse>(
    getPostApiV1ForwardsUpdateUrl(),
    {
      ...options,
      method: "POST",
      headers: { "Content-Type": "application/json", ...options?.headers },
      body: JSON.stringify(forwardReference),
    },
  );
};

/**
 * @summary Version
 */
export type getApiV1ManagementInfoVersionResponse200 = {
  data: WanakuResponseServerInfo;
  status: 200;
};

export type getApiV1ManagementInfoVersionResponseComposite =
  getApiV1ManagementInfoVersionResponse200;

export type getApiV1ManagementInfoVersionResponse =
  getApiV1ManagementInfoVersionResponseComposite & {
    headers: Headers;
  };

export const getGetApiV1ManagementInfoVersionUrl = () => {
  return `/api/v1/management/info/version`;
};

export const getApiV1ManagementInfoVersion = async (
  options?: RequestInit,
): Promise<getApiV1ManagementInfoVersionResponse> => {
  return customFetch<getApiV1ManagementInfoVersionResponse>(
    getGetApiV1ManagementInfoVersionUrl(),
    {
      ...options,
      method: "GET",
    },
  );
};

/**
 * @summary Resources Configure
 */
export type putApiV1ManagementTargetsResourcesConfigureServiceResponse200 = {
  data: void;
  status: 200;
};

export type putApiV1ManagementTargetsResourcesConfigureServiceResponseComposite =
  putApiV1ManagementTargetsResourcesConfigureServiceResponse200;

export type putApiV1ManagementTargetsResourcesConfigureServiceResponse =
  putApiV1ManagementTargetsResourcesConfigureServiceResponseComposite & {
    headers: Headers;
  };

export const getPutApiV1ManagementTargetsResourcesConfigureServiceUrl = (
  service: string,
  params?: PutApiV1ManagementTargetsResourcesConfigureServiceParams,
) => {
  const normalizedParams = new URLSearchParams();

  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined) {
      normalizedParams.append(key, value === null ? "null" : value.toString());
    }
  });

  const stringifiedParams = normalizedParams.toString();

  return stringifiedParams.length > 0
    ? `/api/v1/management/targets/resources/configure/${service}?${stringifiedParams}`
    : `/api/v1/management/targets/resources/configure/${service}`;
};

export const putApiV1ManagementTargetsResourcesConfigureService = async (
  service: string,
  params?: PutApiV1ManagementTargetsResourcesConfigureServiceParams,
  options?: RequestInit,
): Promise<putApiV1ManagementTargetsResourcesConfigureServiceResponse> => {
  return customFetch<putApiV1ManagementTargetsResourcesConfigureServiceResponse>(
    getPutApiV1ManagementTargetsResourcesConfigureServiceUrl(service, params),
    {
      ...options,
      method: "PUT",
    },
  );
};

/**
 * @summary Resources List
 */
export type getApiV1ManagementTargetsResourcesListResponse200 = {
  data: WanakuResponseMapStringService;
  status: 200;
};

export type getApiV1ManagementTargetsResourcesListResponseComposite =
  getApiV1ManagementTargetsResourcesListResponse200;

export type getApiV1ManagementTargetsResourcesListResponse =
  getApiV1ManagementTargetsResourcesListResponseComposite & {
    headers: Headers;
  };

export const getGetApiV1ManagementTargetsResourcesListUrl = () => {
  return `/api/v1/management/targets/resources/list`;
};

export const getApiV1ManagementTargetsResourcesList = async (
  options?: RequestInit,
): Promise<getApiV1ManagementTargetsResourcesListResponse> => {
  return customFetch<getApiV1ManagementTargetsResourcesListResponse>(
    getGetApiV1ManagementTargetsResourcesListUrl(),
    {
      ...options,
      method: "GET",
    },
  );
};

/**
 * @summary Resources State
 */
export type getApiV1ManagementTargetsResourcesStateResponse200 = {
  data: WanakuResponseMapStringListState;
  status: 200;
};

export type getApiV1ManagementTargetsResourcesStateResponseComposite =
  getApiV1ManagementTargetsResourcesStateResponse200;

export type getApiV1ManagementTargetsResourcesStateResponse =
  getApiV1ManagementTargetsResourcesStateResponseComposite & {
    headers: Headers;
  };

export const getGetApiV1ManagementTargetsResourcesStateUrl = () => {
  return `/api/v1/management/targets/resources/state`;
};

export const getApiV1ManagementTargetsResourcesState = async (
  options?: RequestInit,
): Promise<getApiV1ManagementTargetsResourcesStateResponse> => {
  return customFetch<getApiV1ManagementTargetsResourcesStateResponse>(
    getGetApiV1ManagementTargetsResourcesStateUrl(),
    {
      ...options,
      method: "GET",
    },
  );
};

/**
 * @summary Tools Configure
 */
export type putApiV1ManagementTargetsToolsConfigureServiceResponse200 = {
  data: void;
  status: 200;
};

export type putApiV1ManagementTargetsToolsConfigureServiceResponseComposite =
  putApiV1ManagementTargetsToolsConfigureServiceResponse200;

export type putApiV1ManagementTargetsToolsConfigureServiceResponse =
  putApiV1ManagementTargetsToolsConfigureServiceResponseComposite & {
    headers: Headers;
  };

export const getPutApiV1ManagementTargetsToolsConfigureServiceUrl = (
  service: string,
  params?: PutApiV1ManagementTargetsToolsConfigureServiceParams,
) => {
  const normalizedParams = new URLSearchParams();

  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined) {
      normalizedParams.append(key, value === null ? "null" : value.toString());
    }
  });

  const stringifiedParams = normalizedParams.toString();

  return stringifiedParams.length > 0
    ? `/api/v1/management/targets/tools/configure/${service}?${stringifiedParams}`
    : `/api/v1/management/targets/tools/configure/${service}`;
};

export const putApiV1ManagementTargetsToolsConfigureService = async (
  service: string,
  params?: PutApiV1ManagementTargetsToolsConfigureServiceParams,
  options?: RequestInit,
): Promise<putApiV1ManagementTargetsToolsConfigureServiceResponse> => {
  return customFetch<putApiV1ManagementTargetsToolsConfigureServiceResponse>(
    getPutApiV1ManagementTargetsToolsConfigureServiceUrl(service, params),
    {
      ...options,
      method: "PUT",
    },
  );
};

/**
 * @summary Tool List
 */
export type getApiV1ManagementTargetsToolsListResponse200 = {
  data: WanakuResponseMapStringService;
  status: 200;
};

export type getApiV1ManagementTargetsToolsListResponseComposite =
  getApiV1ManagementTargetsToolsListResponse200;

export type getApiV1ManagementTargetsToolsListResponse =
  getApiV1ManagementTargetsToolsListResponseComposite & {
    headers: Headers;
  };

export const getGetApiV1ManagementTargetsToolsListUrl = () => {
  return `/api/v1/management/targets/tools/list`;
};

export const getApiV1ManagementTargetsToolsList = async (
  options?: RequestInit,
): Promise<getApiV1ManagementTargetsToolsListResponse> => {
  return customFetch<getApiV1ManagementTargetsToolsListResponse>(
    getGetApiV1ManagementTargetsToolsListUrl(),
    {
      ...options,
      method: "GET",
    },
  );
};

/**
 * @summary Tools State
 */
export type getApiV1ManagementTargetsToolsStateResponse200 = {
  data: WanakuResponseMapStringListState;
  status: 200;
};

export type getApiV1ManagementTargetsToolsStateResponseComposite =
  getApiV1ManagementTargetsToolsStateResponse200;

export type getApiV1ManagementTargetsToolsStateResponse =
  getApiV1ManagementTargetsToolsStateResponseComposite & {
    headers: Headers;
  };

export const getGetApiV1ManagementTargetsToolsStateUrl = () => {
  return `/api/v1/management/targets/tools/state`;
};

export const getApiV1ManagementTargetsToolsState = async (
  options?: RequestInit,
): Promise<getApiV1ManagementTargetsToolsStateResponse> => {
  return customFetch<getApiV1ManagementTargetsToolsStateResponse>(
    getGetApiV1ManagementTargetsToolsStateUrl(),
    {
      ...options,
      method: "GET",
    },
  );
};

/**
 * @summary Expose
 */
export type postApiV1ResourcesExposeResponse200 = {
  data: void;
  status: 200;
};

export type postApiV1ResourcesExposeResponse400 = {
  data: void;
  status: 400;
};

export type postApiV1ResourcesExposeResponseComposite =
  | postApiV1ResourcesExposeResponse200
  | postApiV1ResourcesExposeResponse400;

export type postApiV1ResourcesExposeResponse =
  postApiV1ResourcesExposeResponseComposite & {
    headers: Headers;
  };

export const getPostApiV1ResourcesExposeUrl = () => {
  return `/api/v1/resources/expose`;
};

export const postApiV1ResourcesExpose = async (
  resourceReference: ResourceReference,
  options?: RequestInit,
): Promise<postApiV1ResourcesExposeResponse> => {
  return customFetch<postApiV1ResourcesExposeResponse>(
    getPostApiV1ResourcesExposeUrl(),
    {
      ...options,
      method: "POST",
      headers: { "Content-Type": "application/json", ...options?.headers },
      body: JSON.stringify(resourceReference),
    },
  );
};

/**
 * @summary List
 */
export type getApiV1ResourcesListResponse200 = {
  data: WanakuResponseListResourceReference;
  status: 200;
};

export type getApiV1ResourcesListResponseComposite =
  getApiV1ResourcesListResponse200;

export type getApiV1ResourcesListResponse =
  getApiV1ResourcesListResponseComposite & {
    headers: Headers;
  };

export const getGetApiV1ResourcesListUrl = () => {
  return `/api/v1/resources/list`;
};

export const getApiV1ResourcesList = async (
  options?: RequestInit,
): Promise<getApiV1ResourcesListResponse> => {
  return customFetch<getApiV1ResourcesListResponse>(
    getGetApiV1ResourcesListUrl(),
    {
      ...options,
      method: "GET",
    },
  );
};

/**
 * @summary Remove
 */
export type putApiV1ResourcesRemoveResponse200 = {
  data: void;
  status: 200;
};

export type putApiV1ResourcesRemoveResponseComposite =
  putApiV1ResourcesRemoveResponse200;

export type putApiV1ResourcesRemoveResponse =
  putApiV1ResourcesRemoveResponseComposite & {
    headers: Headers;
  };

export const getPutApiV1ResourcesRemoveUrl = (
  params?: PutApiV1ResourcesRemoveParams,
) => {
  const normalizedParams = new URLSearchParams();

  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined) {
      normalizedParams.append(key, value === null ? "null" : value.toString());
    }
  });

  const stringifiedParams = normalizedParams.toString();

  return stringifiedParams.length > 0
    ? `/api/v1/resources/remove?${stringifiedParams}`
    : `/api/v1/resources/remove`;
};

export const putApiV1ResourcesRemove = async (
  params?: PutApiV1ResourcesRemoveParams,
  options?: RequestInit,
): Promise<putApiV1ResourcesRemoveResponse> => {
  return customFetch<putApiV1ResourcesRemoveResponse>(
    getPutApiV1ResourcesRemoveUrl(params),
    {
      ...options,
      method: "PUT",
    },
  );
};

/**
 * @summary Update
 */
export type postApiV1ResourcesUpdateResponse200 = {
  data: void;
  status: 200;
};

export type postApiV1ResourcesUpdateResponse400 = {
  data: void;
  status: 400;
};

export type postApiV1ResourcesUpdateResponseComposite =
  | postApiV1ResourcesUpdateResponse200
  | postApiV1ResourcesUpdateResponse400;

export type postApiV1ResourcesUpdateResponse =
  postApiV1ResourcesUpdateResponseComposite & {
    headers: Headers;
  };

export const getPostApiV1ResourcesUpdateUrl = () => {
  return `/api/v1/resources/update`;
};

export const postApiV1ResourcesUpdate = async (
  resourceReference: ResourceReference,
  options?: RequestInit,
): Promise<postApiV1ResourcesUpdateResponse> => {
  return customFetch<postApiV1ResourcesUpdateResponse>(
    getPostApiV1ResourcesUpdateUrl(),
    {
      ...options,
      method: "POST",
      headers: { "Content-Type": "application/json", ...options?.headers },
      body: JSON.stringify(resourceReference),
    },
  );
};

/**
 * @summary Add
 */
export type postApiV1ToolsAddResponse200 = {
  data: void;
  status: 200;
};

export type postApiV1ToolsAddResponse400 = {
  data: void;
  status: 400;
};

export type postApiV1ToolsAddResponseComposite =
  | postApiV1ToolsAddResponse200
  | postApiV1ToolsAddResponse400;

export type postApiV1ToolsAddResponse = postApiV1ToolsAddResponseComposite & {
  headers: Headers;
};

export const getPostApiV1ToolsAddUrl = () => {
  return `/api/v1/tools/add`;
};

export const postApiV1ToolsAdd = async (
  toolReference: ToolReference,
  options?: RequestInit,
): Promise<postApiV1ToolsAddResponse> => {
  return customFetch<postApiV1ToolsAddResponse>(getPostApiV1ToolsAddUrl(), {
    ...options,
    method: "POST",
    headers: { "Content-Type": "application/json", ...options?.headers },
    body: JSON.stringify(toolReference),
  });
};

/**
 * @summary List
 */
export type getApiV1ToolsListResponse200 = {
  data: WanakuResponseListToolReference;
  status: 200;
};

export type getApiV1ToolsListResponseComposite = getApiV1ToolsListResponse200;

export type getApiV1ToolsListResponse = getApiV1ToolsListResponseComposite & {
  headers: Headers;
};

export const getGetApiV1ToolsListUrl = () => {
  return `/api/v1/tools/list`;
};

export const getApiV1ToolsList = async (
  options?: RequestInit,
): Promise<getApiV1ToolsListResponse> => {
  return customFetch<getApiV1ToolsListResponse>(getGetApiV1ToolsListUrl(), {
    ...options,
    method: "GET",
  });
};

/**
 * @summary Remove
 */
export type putApiV1ToolsRemoveResponse200 = {
  data: void;
  status: 200;
};

export type putApiV1ToolsRemoveResponseComposite =
  putApiV1ToolsRemoveResponse200;

export type putApiV1ToolsRemoveResponse =
  putApiV1ToolsRemoveResponseComposite & {
    headers: Headers;
  };

export const getPutApiV1ToolsRemoveUrl = (
  params?: PutApiV1ToolsRemoveParams,
) => {
  const normalizedParams = new URLSearchParams();

  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined) {
      normalizedParams.append(key, value === null ? "null" : value.toString());
    }
  });

  const stringifiedParams = normalizedParams.toString();

  return stringifiedParams.length > 0
    ? `/api/v1/tools/remove?${stringifiedParams}`
    : `/api/v1/tools/remove`;
};

export const putApiV1ToolsRemove = async (
  params?: PutApiV1ToolsRemoveParams,
  options?: RequestInit,
): Promise<putApiV1ToolsRemoveResponse> => {
  return customFetch<putApiV1ToolsRemoveResponse>(
    getPutApiV1ToolsRemoveUrl(params),
    {
      ...options,
      method: "PUT",
    },
  );
};

/**
 * @summary Update
 */
export type postApiV1ToolsUpdateResponse200 = {
  data: void;
  status: 200;
};

export type postApiV1ToolsUpdateResponse400 = {
  data: void;
  status: 400;
};

export type postApiV1ToolsUpdateResponseComposite =
  | postApiV1ToolsUpdateResponse200
  | postApiV1ToolsUpdateResponse400;

export type postApiV1ToolsUpdateResponse =
  postApiV1ToolsUpdateResponseComposite & {
    headers: Headers;
  };

export const getPostApiV1ToolsUpdateUrl = () => {
  return `/api/v1/tools/update`;
};

export const postApiV1ToolsUpdate = async (
  toolReference: ToolReference,
  options?: RequestInit,
): Promise<postApiV1ToolsUpdateResponse> => {
  return customFetch<postApiV1ToolsUpdateResponse>(
    getPostApiV1ToolsUpdateUrl(),
    {
      ...options,
      method: "POST",
      headers: { "Content-Type": "application/json", ...options?.headers },
      body: JSON.stringify(toolReference),
    },
  );
};
