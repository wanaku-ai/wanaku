import { customFetch } from "../../custom-fetch";

// Simple in-memory cache for Client Components
let installationsCache: {
  data: any;
} | null = null;

export const listInstallations = async () => {
  // Check if we have valid cached data
  if (installationsCache) {
    if (process.env.NODE_ENV !== 'production') {
      console.log('Returning cached installations data');
    }
    return installationsCache.data;
  }

  // Fetch fresh data
  if (process.env.NODE_ENV !== 'production') {
    console.log('Fetching fresh installations data');
  }
  const result = await customFetch<any>(
    `/api/v1/capabilities/installations`,
    { method: 'GET', headers: { 'Content-Type': 'application/json' } }
  );

  // Cache the result
  installationsCache = {
    data: result
  };

  return result;
};

// Function to clear cache if needed
export const clearInstallationsCache = () => {
  installationsCache = null;
};

export const createInstallation = async (
  dataStore: { name: string; data: string; labels: Record<string, string> },
  options?: RequestInit
) => {
  clearInstallationsCache();
  return customFetch<any>(
    `/api/v1/capabilities/installations`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dataStore),
      ...options
    }
  );
};

export const updateInstallation = async (
  id: string,
  dataStore: { name: string; data: string; labels: Record<string, string> },
  options?: RequestInit
) => {
  clearInstallationsCache();
  return customFetch<any>(
    `/api/v1/capabilities/installations/${id}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dataStore),
      ...options
    }
  );
};

export const deleteInstallation = async (
  id: string,
  options?: RequestInit
) => {
  clearInstallationsCache();
  return customFetch<any>(
    `/api/v1/capabilities/installations/${id}`,
    { method: 'DELETE', ...options }
  );
};

export const launchInstallation = async (
  id: string,
  options?: RequestInit
) => {
  clearInstallationsCache();
  return customFetch<any>(
    `/api/v1/capabilities/installations/${id}/launch`,
    { method: 'POST', ...options }
  );
};

export const stopInstallation = async (
  id: string,
  options?: RequestInit
) => {
  clearInstallationsCache();
  return customFetch<any>(
    `/api/v1/capabilities/installations/${id}/stop`,
    { method: 'POST', ...options }
  );
};

export const getInstallationStatus = async (
  id: string,
  options?: RequestInit
) => {
  return customFetch<any>(
    `/api/v1/capabilities/installations/${id}/status`,
    { method: 'GET', ...options }
  );
};
