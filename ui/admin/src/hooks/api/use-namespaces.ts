import {useCallback} from "react";
import {
    listNamespaces as apiListNamespaces,
    listNamespacesResponse,
    createNamespace as apiCreateNamespace,
    createNamespaceResponse,
    deleteNamespace,
    deleteNamespaceResponse,
} from "../../api/wanaku-router-api";
import {NamespaceEntry} from "../../models";

// Simple in-memory cache for Client Components
let namespacesCache: {
  data: any;
} | null = null;

export const useNamespaces = () => {
  const listNamespaces = useCallback(
    (options?: RequestInit): Promise<listNamespacesResponse> => {
      return apiListNamespaces(options);
    },
    []
  );

  const createNamespace = useCallback(
    (namespace: NamespaceEntry, options?: RequestInit): Promise<createNamespaceResponse> => {
      clearNamespacesCache();
      return apiCreateNamespace(namespace, options);
    },
    []
  );

  const updateNamespace = useCallback(
    async (namespace: NamespaceEntry, options?: RequestInit): Promise<void> => {
      if (!namespace.name) {
        throw new Error("Namespace name is required for update");
      }
      clearNamespacesCache();
      await deleteNamespace(namespace.name, options);
      await apiCreateNamespace(namespace, options);
    },
    []
  );

  const removeNamespace = useCallback(
    (name: string, options?: RequestInit): Promise<deleteNamespaceResponse> => {
      clearNamespacesCache();
      return deleteNamespace(name, options);
    },
    []
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
    data: result
  };

  return result;
};

export const clearNamespacesCache = () => {
  namespacesCache = null;
};

export const getNamespacePathById = (name?: string): string => {
  if (!name) {
    return "default"
  }
  if (namespacesCache) {
    const data = namespacesCache.data.data as NamespaceEntry[]
    const found = data.find(namespace => namespace.name === name)?.name
    if (found) return found
  }
  return name;
}
