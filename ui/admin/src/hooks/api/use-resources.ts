import { useCallback } from 'react';
import {
  listResources as apiListResources,
  listResourcesResponse,
  disableResource,
  enableResource,
} from '../../api/wanaku-router-api';

export const useResources = () => {
  /**
   * List resources.
   */
  const listResources = useCallback((options?: RequestInit): Promise<listResourcesResponse> => {
    return apiListResources(options);
  }, []);

  /**
   * Enable or disable a resource. MCP clients cannot list or use a disabled resource.
   */
  const setResourceEnabled = useCallback(
    (name: string, enabled: boolean, options?: RequestInit) => {
      return enabled ? enableResource(name, options) : disableResource(name, options);
    },
    [],
  );

  return {
    listResources,
    setResourceEnabled,
  };
};
