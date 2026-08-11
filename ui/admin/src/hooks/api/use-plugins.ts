import { useCallback } from "react";
import { customFetch } from "../../custom-fetch";
import type { PluginManifest } from "../../plugins/types";

interface PluginsResponse {
  status: number;
  data: {
    data: PluginManifest[] | null;
    error: string | null;
  };
  headers: Headers;
}

export const usePlugins = () => {
  const listPlugins = useCallback(
    (options?: RequestInit): Promise<PluginsResponse> => {
      return customFetch<PluginsResponse>("/api/v1/plugins", {
        ...options,
        method: "GET",
      });
    },
    []
  );

  return { listPlugins };
};
