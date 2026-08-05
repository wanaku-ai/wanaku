import {useCallback} from "react";
import {
    getApiV1Namespaces,
    getApiV1NamespacesResponse,
    postApiV1Namespaces,
    postApiV1NamespacesResponse,
    putApiV1NamespacesId,
    putApiV1NamespacesIdResponse,
    deleteApiV1NamespacesId,
    deleteApiV1NamespacesIdResponse,
} from "../../api/wanaku-router-api";
import {Namespace} from "../../models";

// Simple in-memory cache for Client Components
let namespacesCache: {
  data: any;
} | null = null;

export const useNamespaces = () => {
  const listNamespaces = useCallback(
    (options?: RequestInit): Promise<getApiV1NamespacesResponse> => {
      return getApiV1Namespaces(undefined, options);
    },
    []
  );

  const createNamespace = useCallback(
    (namespace: Namespace, options?: RequestInit): Promise<postApiV1NamespacesResponse> => {
      clearNamespacesCache();
      return postApiV1Namespaces(namespace, options);
    },
    []
  );

  const updateNamespace = useCallback(
    (namespace: Namespace, options?: RequestInit): Promise<putApiV1NamespacesIdResponse> => {
      if (!namespace.id) {
        throw new Error("Namespace ID is required for update");
      }
      clearNamespacesCache();
      return putApiV1NamespacesId(namespace.id, namespace, options);
    },
    []
  );

  const removeNamespace = useCallback(
    (id: string, options?: RequestInit): Promise<deleteApiV1NamespacesIdResponse> => {
      clearNamespacesCache();
      return deleteApiV1NamespacesId(id, options);
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
  // Check if we have valid cached data
  if (namespacesCache) {
    if (process.env.NODE_ENV !== 'production') {
      console.log('Returning cached namespaces data');
    }
    return namespacesCache.data;
  }

  // Fetch fresh data
  if (process.env.NODE_ENV !== 'production') {
    console.log('Fetching fresh namespaces data');
  }
  const result = await getApiV1Namespaces(undefined, options);

  // Cache the result
  namespacesCache = {
    data: result
  };

  return result;
};

// Function to clear cache if needed
export const clearNamespacesCache = () => {
  namespacesCache = null;
};

export const getNamespacePathById = (id?: string): string | undefined => {
  if (!id) {
    return "default"
  }
  if (namespacesCache) {
    const data = namespacesCache.data.data.data as Namespace[]
    return data.find(namespace => namespace.id === id)?.path
  }
  return undefined;
}
