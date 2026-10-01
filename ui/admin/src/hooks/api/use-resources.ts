import { useCallback } from 'react';
import {
  listResources as apiListResources,
  listResourcesResponse,
  deleteResource,
  deleteResourceResponse,
} from '../../api/wanaku-router-api';

export const useResources = () => {
  /**
   * List resources.
   */
  const listResources = useCallback((options?: RequestInit): Promise<listResourcesResponse> => {
    return apiListResources(options);
  }, []);

  /**
   * Remove a resource.
   */
  const removeResource = useCallback(
    (name: string, options?: RequestInit): Promise<deleteResourceResponse> => {
      return deleteResource(name, options);
    },
    [],
  );

  return {
    listResources,
    removeResource,
  };
};
