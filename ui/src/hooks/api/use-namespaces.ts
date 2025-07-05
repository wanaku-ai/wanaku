import { getApiV1NamespacesList } from "../../api/wanaku-router-api";
import { Namespace } from "../../models";

// Simple in-memory cache for Client Components
let namespacesCache: {
  data: any;
  timestamp: number;
} | null = null;

export const listNamespaces = async (options: any = null) => {
  const now = Date.now();
  
  // Check if we have valid cached data
  if (namespacesCache) {
    console.log('Returning cached namespaces data');
    return namespacesCache.data;
  }
  
  // Fetch fresh data
  console.log('Fetching fresh namespaces data');
  const result = await getApiV1NamespacesList(options);
  
  // Cache the result
  namespacesCache = {
    data: result,
    timestamp: now
  };
  
  return result;
};

// Function to clear cache if needed
export const clearNamespacesCache = () => {
  namespacesCache = null;
};

export const getNamespacePathById = (id?: string): string | undefined => {
  if (!id) {
    return undefined;
  }

  if (namespacesCache) {
    const data = namespacesCache.data.data.data as Namespace[];
    const path = data.find(namespace => namespace.id === id)?.path;
    return path ?? id;
  }

  return id;
}
