import { useCallback } from 'react';
import { listPluginCatalog } from '../../api/wanaku-router-api';

export const usePluginCatalog = () => {
  const listCatalog = useCallback((options?: RequestInit) => listPluginCatalog(options), []);
  return { listCatalog };
};
