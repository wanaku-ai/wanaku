import { useCallback } from "react";
import { customFetch } from "../../custom-fetch";

export interface SafetyConfig {
  llm_url: string;
  llm_model: string;
  llm_api_key: string;
  red_action: "log" | "warn" | "block";
  yellow_action: "log" | "warn" | "block";
}

export interface SafetyResponse {
  status: number;
  data: {
    data: SafetyConfig | null;
    error: string | null;
  };
  headers: Headers;
}

export const useSafety = () => {
  const getSafety = useCallback(
    (options?: RequestInit): Promise<SafetyResponse> => {
      return customFetch<SafetyResponse>("/api/v1/safety", {
        ...options,
        method: "GET",
      });
    },
    []
  );

  const putSafety = useCallback(
    (config: SafetyConfig, options?: RequestInit): Promise<SafetyResponse> => {
      return customFetch<SafetyResponse>("/api/v1/safety", {
        ...options,
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          ...options?.headers,
        },
        body: JSON.stringify(config),
      });
    },
    []
  );

  const deleteSafety = useCallback(
    (options?: RequestInit): Promise<SafetyResponse> => {
      return customFetch<SafetyResponse>("/api/v1/safety", {
        ...options,
        method: "DELETE",
      });
    },
    []
  );

  return {
    getSafety,
    putSafety,
    deleteSafety,
  };
};
