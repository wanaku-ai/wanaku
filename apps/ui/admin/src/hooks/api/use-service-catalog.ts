import {useCallback} from "react";
import {customFetch} from "../../custom-fetch";

const BASE_PATH = "/api/v1/service-catalog";

/**
 * Custom hook for Service Catalog API operations
 */
export const useServiceCatalog = () => {
  const listServiceCatalogs = useCallback(
    (search?: string, options?: RequestInit) => {
      const params = new URLSearchParams();
      if (search) {
        params.append("search", search);
      }
      const query = params.toString();
      const url = query ? `${BASE_PATH}?${query}` : `${BASE_PATH}`;
      return customFetch<{ data: unknown; status: number; headers: Headers }>(url, {
        ...options,
        method: "GET",
      });
    },
    []
  );

  const getServiceCatalog = useCallback(
    (name: string, options?: RequestInit) => {
      return customFetch<{ data: unknown; status: number; headers: Headers }>(
        `${BASE_PATH}/${encodeURIComponent(name)}`,
        {
          ...options,
          method: "GET",
        }
      );
    },
    []
  );

  const downloadServiceCatalog = useCallback(
    (name: string, options?: RequestInit) => {
      const params = new URLSearchParams({ name });
      return customFetch<{ data: unknown; status: number; headers: Headers }>(
        `${BASE_PATH}/download?${params.toString()}`,
        {
          ...options,
          method: "GET",
        }
      );
    },
    []
  );

  const deployServiceCatalog = useCallback(
    (dataStore: { name: string; data: string }, options?: RequestInit) => {
      return customFetch<{ data: unknown; status: number; headers: Headers }>(
        `${BASE_PATH}`,
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

  const removeServiceCatalog = useCallback(
    (name: string, options?: RequestInit) => {
      return customFetch<{ data: unknown; status: number; headers: Headers }>(
        `${BASE_PATH}/${encodeURIComponent(name)}`,
        {
          ...options,
          method: "DELETE",
        }
      );
    },
    []
  );

  const getDeploymentInstructions = useCallback(
    (name: string, model: string, options?: RequestInit) => {
      const params = new URLSearchParams({ name, model });
      return customFetch<{ data: unknown; status: number; headers: Headers }>(
        `${BASE_PATH}/instructions?${params.toString()}`,
        {
          ...options,
          method: "GET",
        }
      );
    },
    []
  );

  return {
    listServiceCatalogs,
    getServiceCatalog,
    downloadServiceCatalog,
    deployServiceCatalog,
    removeServiceCatalog,
    getDeploymentInstructions,
  };
};
