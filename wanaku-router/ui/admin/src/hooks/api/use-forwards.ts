import {
  getApiV1Forwards,
  postApiV1Forwards,
  postApiV1ForwardsResponse,
  postApiV1ForwardsNameRefreshes,
  postApiV1ForwardsNameRefreshesResponse,
  putApiV1ForwardsName,
  putApiV1ForwardsNameResponse,
  deleteApiV1ForwardsName,
  deleteApiV1ForwardsNameResponse
} from "../../api/wanaku-router-api";
import { ForwardReference } from "../../models";

// Simple in-memory cache for Client Components
let forwardsCache: {
  data: any;
} | null = null;

export const listForwards = async (options: any = null) => {
  // Check if we have valid cached data
  if (forwardsCache) {
    if (process.env.NODE_ENV !== 'production') {
      console.log('Returning cached forwards data');
    }
    return forwardsCache.data;
  }

  // Fetch fresh data
  if (process.env.NODE_ENV !== 'production') {
    console.log('Fetching fresh forwards data');
  }
  const result = await getApiV1Forwards(undefined, options);

  // Cache the result
  forwardsCache = {
    data: result
  };

  return result;
};

// Function to clear cache if needed
export const clearForwardsCache = () => {
  forwardsCache = null;
};

export const addForward = async (
  forwardReference: ForwardReference,
  options?: RequestInit
): Promise<postApiV1ForwardsResponse> => {
  clearForwardsCache();
  return postApiV1Forwards(forwardReference, options);
};

export const updateForward = async (
  forward: ForwardReference,
  options?: RequestInit
): Promise<putApiV1ForwardsNameResponse> => {
  if (!forward.name) {
    throw new Error("Forward name is required for update");
  }
  clearForwardsCache()
  return putApiV1ForwardsName(forward.name, forward, options)
}

export const removeForward = async (
  forward: ForwardReference,
  options?: RequestInit
): Promise<deleteApiV1ForwardsNameResponse> => {
  if (!forward.name) {
    throw new Error("Forward name is required for removal");
  }
  clearForwardsCache();
  return deleteApiV1ForwardsName(forward.name, options);
};

export const refreshForward = async (
  forward: ForwardReference,
  options?: RequestInit
): Promise<postApiV1ForwardsNameRefreshesResponse> => {
  if (!forward.name) {
    throw new Error("Forward name is required for refresh");
  }
  clearForwardsCache();
  return postApiV1ForwardsNameRefreshes(forward.name, options);
};
