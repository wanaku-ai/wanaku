import { useCallback } from "react";
import {
  getMetrics as apiGetMetrics,
  getMetricsResponse,
} from "../../api/wanaku-router-api";

export const useMetrics = () => {
  const getMetrics = useCallback(
    (options?: RequestInit): Promise<getMetricsResponse> => {
      return apiGetMetrics(options);
    },
    [],
  );

  return {
    getMetrics,
  };
};
