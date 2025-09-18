import { getApiV1ForwardsList } from "../../api/wanaku-router-api";

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
  const result = await getApiV1ForwardsList(options);
  
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
