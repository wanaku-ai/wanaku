import { getApiV1NamespacesList } from "../../api/wanaku-router-api";
import { Namespace } from "../../models";

// Simple in-memory cache for Client Components
let namespacesCache: {
  data: any;
} | null = null;

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
  const result = await getApiV1NamespacesList(options);
  
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
    return undefined;
  }

  if (namespacesCache) {
    const data = namespacesCache.data.data.data as Namespace[];
    const path = data.find(namespace => namespace.id === id)?.path;
    return path;
  }

  return undefined;
}
