import {
  listForwards as apiListForwards,
  createForward as apiCreateForward,
  type createForwardResponse,
  refreshForward as apiRefreshForward,
  type refreshForwardResponse,
  deleteForward as apiDeleteForward,
  type deleteForwardResponse,
} from "../../api/wanaku-router-api";
import { ForwardEntry } from "../../models";

// Simple in-memory cache for Client Components
let forwardsCache: {
  data: any;
} | null = null;

export const listForwards = async (options: any = null) => {
  if (forwardsCache) {
    return forwardsCache.data;
  }

  const result = await apiListForwards(options ?? undefined);

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
  forward: ForwardEntry,
  options?: RequestInit
): Promise<createForwardResponse> => {
  clearForwardsCache();
  return apiCreateForward(forward, options);
};

export const updateForward = async (
  forward: ForwardEntry,
  options?: RequestInit
): Promise<createForwardResponse> => {
  if (!forward.name) {
    throw new Error("Forward name is required for update");
  }
  clearForwardsCache();
  await apiDeleteForward(forward.name, options);
  return apiCreateForward(forward, options);
};

export const removeForward = async (
  forward: ForwardEntry,
  options?: RequestInit
): Promise<deleteForwardResponse> => {
  if (!forward.name) {
    throw new Error("Forward name is required for removal");
  }
  clearForwardsCache();
  return apiDeleteForward(forward.name, options);
};

export const refreshForward = async (
  forward: ForwardEntry,
  options?: RequestInit
): Promise<refreshForwardResponse> => {
  if (!forward.name) {
    throw new Error("Forward name is required for refresh");
  }
  clearForwardsCache();
  return apiRefreshForward(forward.name, options);
};
