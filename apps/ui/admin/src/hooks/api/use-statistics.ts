import {useCallback} from "react";
import {getApiV1ManagementStatistics, getApiV1ManagementStatisticsResponse,} from "../../api/wanaku-router-api";

export const useStatistics = () => {
  const getStatistics = useCallback(
    (
      options?: RequestInit,
    ): Promise<getApiV1ManagementStatisticsResponse> => {
      return getApiV1ManagementStatistics(options);
    },
    [],
  );

  return {
    getStatistics,
  };
};
