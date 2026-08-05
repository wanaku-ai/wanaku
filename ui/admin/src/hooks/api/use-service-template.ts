import {useCallback} from "react";
import {customFetch} from "../../custom-fetch";

const BASE_PATH = "/api/v1/service-template";

/**
 * Custom hook for Service Template API operations
 */
export const useServiceTemplate = () => {
  const listServiceTemplates = useCallback(
    (search?: string, options?: RequestInit) => {
      const params = new URLSearchParams();
      if (search) {
        params.append("search", search);
      }
      const query = params.toString();
      const url = query ? `${BASE_PATH}/list?${query}` : `${BASE_PATH}/list`;
      return customFetch<{ data: unknown; status: number; headers: Headers }>(url, {
        ...options,
        method: "GET",
      });
    },
    []
  );

  const getServiceTemplate = useCallback(
    (name: string, options?: RequestInit) => {
      const params = new URLSearchParams({ name });
      return customFetch<{ data: unknown; status: number; headers: Headers }>(
        `${BASE_PATH}/get?${params.toString()}`,
        {
          ...options,
          method: "GET",
        }
      );
    },
    []
  );

  const getTemplateProperties = useCallback(
    (name: string, options?: RequestInit) => {
      const params = new URLSearchParams({ name });
      return customFetch<{ data: unknown; status: number; headers: Headers }>(
        `${BASE_PATH}/properties?${params.toString()}`,
        {
          ...options,
          method: "GET",
        }
      );
    },
    []
  );

  const deployServiceTemplate = useCallback(
    (dataStore: { name: string; data: string }, options?: RequestInit) => {
      return customFetch<{ data: unknown; status: number; headers: Headers }>(
        `${BASE_PATH}/deploy`,
        {
          ...options,
          method: "POST",
          headers: { "Content-Type": "application/json", ...options?.headers },
          body: JSON.stringify(dataStore),
        }
      );
    },
    []
  );

  const instantiateTemplate = useCallback(
    (templateName: string, properties: Record<string, string>, serviceName?: string, serviceSystem?: string, options?: RequestInit) => {
      return customFetch<{ data: unknown; status: number; headers: Headers }>(
        `${BASE_PATH}/instantiate`,
        {
          ...options,
          method: "POST",
          headers: { "Content-Type": "application/json", ...options?.headers },
          body: JSON.stringify({ templateName, properties, serviceName: serviceName || undefined, serviceSystem: serviceSystem || undefined }),
        }
      );
    },
    []
  );

  const removeServiceTemplate = useCallback(
    (name: string, options?: RequestInit) => {
      const params = new URLSearchParams({ name });
      return customFetch<{ data: unknown; status: number; headers: Headers }>(
        `${BASE_PATH}/remove?${params.toString()}`,
        {
          ...options,
          method: "DELETE",
        }
      );
    },
    []
  );

  return {
    listServiceTemplates,
    getServiceTemplate,
    getTemplateProperties,
    deployServiceTemplate,
    instantiateTemplate,
    removeServiceTemplate,
  };
};
