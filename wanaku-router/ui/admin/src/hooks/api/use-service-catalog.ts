import {useCallback} from "react";

// Types for service catalog API responses
interface ServiceCatalogSummary {
  id: string;
  name: string;
  icon?: string;
  description: string;
  services: string[];
}

interface ServiceCatalogSystem {
  name: string;
  routesFile: string;
  rulesFile: string;
  dependenciesFile?: string;
}

interface ServiceCatalogDetail {
  id: string;
  name: string;
  icon?: string;
  description: string;
  services: ServiceCatalogSystem[];
}

interface WanakuResponse<T> {
  data: T;
  error?: { message: string };
}

export const useServiceCatalog = () => {
  const listServiceCatalogs = useCallback(
    async (search?: string): Promise<WanakuResponse<ServiceCatalogSummary[]>> => {
      const params = search ? `?search=${encodeURIComponent(search)}` : "";
      const response = await fetch(`/api/v1/service-catalog/list${params}`);
      if (!response.ok) throw new Error(`Failed to list service catalogs: ${response.statusText}`);
      return response.json();
    },
    []
  );

  const getServiceCatalog = useCallback(
    async (name: string): Promise<WanakuResponse<ServiceCatalogDetail>> => {
      const response = await fetch(`/api/v1/service-catalog/get?name=${encodeURIComponent(name)}`);
      if (!response.ok) throw new Error(`Failed to get service catalog: ${response.statusText}`);
      return response.json();
    },
    []
  );

  const deployServiceCatalog = useCallback(
    async (name: string, data: string): Promise<WanakuResponse<unknown>> => {
      const response = await fetch("/api/v1/service-catalog/deploy", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, data }),
      });
      if (!response.ok) throw new Error(`Failed to deploy service catalog: ${response.statusText}`);
      return response.json();
    },
    []
  );

  const removeServiceCatalog = useCallback(
    async (name: string): Promise<void> => {
      const response = await fetch(`/api/v1/service-catalog/remove?name=${encodeURIComponent(name)}`, {
        method: "DELETE",
      });
      if (!response.ok) throw new Error(`Failed to remove service catalog: ${response.statusText}`);
    },
    []
  );

  return {
    listServiceCatalogs,
    getServiceCatalog,
    deployServiceCatalog,
    removeServiceCatalog,
  };
};
