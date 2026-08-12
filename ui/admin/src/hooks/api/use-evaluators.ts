import { useCallback } from "react";
import { customFetch } from "../../custom-fetch";

export interface EvaluatorTrigger {
  method: string;
  namespace?: string;
}

export interface EvaluatorLlm {
  operation: "classify" | "filter" | "augment";
  prompt: string;
  model: string;
  url: string;
  api_key?: string;
}

export interface EvaluatorProcessor {
  path: string;
}

export interface EvaluatorDef {
  name: string;
  trigger: EvaluatorTrigger;
  llm: EvaluatorLlm;
  processor: EvaluatorProcessor;
  on_error: "continue" | "block";
}

export interface EvaluatorsResponse {
  status: number;
  data: {
    data: EvaluatorDef[] | null;
    error: string | null;
  };
  headers: Headers;
}

export interface BindingsResponse {
  status: number;
  data: {
    data: Record<string, string> | null;
    error: string | null;
  };
  headers: Headers;
}

export interface SimpleResponse {
  status: number;
  data: {
    data: unknown;
    error: string | null;
  };
  headers: Headers;
}

export const useEvaluators = () => {
  const listEvaluators = useCallback(
    (options?: RequestInit): Promise<EvaluatorsResponse> => {
      return customFetch<EvaluatorsResponse>("/api/v1/evaluators", {
        ...options,
        method: "GET",
      });
    },
    []
  );

  const updateEvaluators = useCallback(
    (evaluators: EvaluatorDef[], options?: RequestInit): Promise<SimpleResponse> => {
      return customFetch<SimpleResponse>("/api/v1/evaluators", {
        ...options,
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          ...options?.headers,
        },
        body: JSON.stringify({ evaluators }),
      });
    },
    []
  );

  const listBindings = useCallback(
    (options?: RequestInit): Promise<BindingsResponse> => {
      return customFetch<BindingsResponse>("/api/v1/evaluators/namespaces", {
        ...options,
        method: "GET",
      });
    },
    []
  );

  const bindNamespace = useCallback(
    (namespace: string, conversationId: string, options?: RequestInit): Promise<SimpleResponse> => {
      return customFetch<SimpleResponse>(`/api/v1/evaluators/namespaces/${encodeURIComponent(namespace)}`, {
        ...options,
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          ...options?.headers,
        },
        body: JSON.stringify({ conversation_id: conversationId }),
      });
    },
    []
  );

  const unbindNamespace = useCallback(
    (namespace: string, options?: RequestInit): Promise<SimpleResponse> => {
      return customFetch<SimpleResponse>(`/api/v1/evaluators/namespaces/${encodeURIComponent(namespace)}`, {
        ...options,
        method: "DELETE",
      });
    },
    []
  );

  return {
    listEvaluators,
    updateEvaluators,
    listBindings,
    bindNamespace,
    unbindNamespace,
  };
};
