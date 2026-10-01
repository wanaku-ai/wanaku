import { useCallback } from "react";
import { configurePlugin, installPlugin, listPlugins as apiListPlugins } from "../../api/wanaku-router-api";
import type { ConfigurePluginRequest, InstallPluginRequest } from "../../models";

export const usePlugins = () => {
  const listPlugins = useCallback(
    (options?: RequestInit) => {
      return apiListPlugins(options);
    },
    []
  );

  const install = useCallback(
    (request: InstallPluginRequest, options?: RequestInit) => {
      return installPlugin(request, options);
    },
    []
  );

  const configure = useCallback(
    (id: string, request: ConfigurePluginRequest, options?: RequestInit) => {
      return configurePlugin(id, request, options);
    },
    []
  );

  return { listPlugins, installPlugin: install, configurePlugin: configure };
};
