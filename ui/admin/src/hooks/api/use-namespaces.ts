import { useCallback } from 'react';
import {
  listNamespaces as apiListNamespaces,
  listNamespacesResponse,
  createNamespace as apiCreateNamespace,
  createNamespaceResponse,
  updateNamespace as apiUpdateNamespace,
  updateNamespaceResponse,
  deleteNamespace,
  deleteNamespaceResponse,
} from '../../api/wanaku-router-api';
import { NamespaceEntry } from '../../models';

// Simple in-memory cache for Client Components
let namespacesCache: {
  data: any;
} | null = null;

export const DEFAULT_NAMESPACE: NamespaceEntry = { name: 'default' };

export const useNamespaces = () => {
  const listNamespaces = useCallback((options?: RequestInit): Promise<listNamespacesResponse> => {
    return apiListNamespaces(options);
  }, []);

  const createNamespace = useCallback(
    (namespace: NamespaceEntry, options?: RequestInit): Promise<createNamespaceResponse> => {
      clearNamespacesCache();
      return apiCreateNamespace(namespace, options);
    },
    [],
  );

  const updateNamespace = useCallback(
    (namespace: NamespaceEntry, options?: RequestInit): Promise<updateNamespaceResponse> => {
      if (!namespace.name) {
        return Promise.reject(new Error('Namespace name is required for update'));
      }
      clearNamespacesCache();
      return apiUpdateNamespace(namespace.name, namespace, options);
    },
    [],
  );

  const removeNamespace = useCallback(
    (name: string, options?: RequestInit): Promise<deleteNamespaceResponse> => {
      clearNamespacesCache();
      return deleteNamespace(name, options);
    },
    [],
  );

  return {
    listNamespaces,
    createNamespace,
    updateNamespace,
    removeNamespace,
  };
};

export const listNamespaces = async (options: any = null) => {
  if (namespacesCache) {
    if (process.env.NODE_ENV !== 'production') {
      console.log('Returning cached namespaces data');
    }
    return namespacesCache.data;
  }

  if (process.env.NODE_ENV !== 'production') {
    console.log('Fetching fresh namespaces data');
  }
  const result = await apiListNamespaces(options);

  namespacesCache = {
    data: result,
  };

  return result;
};

export const clearNamespacesCache = () => {
  namespacesCache = null;
};

export const getNamespacePathById = (name?: string): string => {
  if (!name) {
    return 'default';
  }
  if (namespacesCache) {
    const data = namespacesCache.data.data as NamespaceEntry[];
    const found = data.find((namespace) => namespace.name === name)?.name;
    if (found) return found;
  }
  return name;
};
