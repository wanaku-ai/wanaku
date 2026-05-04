import {useCallback} from "react";
import {customFetch} from "../../custom-fetch";
import type {ToolReference} from "../../models";

const BASE_PATH = "/api/v1/toolset-repos";

export interface ToolsetRepoSummary {
  name: string;
  url: string;
  description?: string;
  icon?: string;
}

export interface ToolsetEntry {
  name: string;
  description: string;
  icon?: string;
}

export interface ToolsetRepoCatalog {
  name: string;
  url: string;
  icon?: string;
  toolsets: ToolsetEntry[];
}

export const useToolsetRepos = () => {
  const listRepos = useCallback(
    (options?: RequestInit) => {
      return customFetch<{ data: { data: ToolsetRepoSummary[] }; status: number; headers: Headers }>(
        BASE_PATH,
        {
          ...options,
          method: "GET",
        }
      );
    },
    []
  );

  const addRepo = useCallback(
    (repo: { name: string; url: string; description?: string; icon?: string }, options?: RequestInit) => {
      return customFetch<{ data: { data: ToolsetRepoSummary }; status: number; headers: Headers }>(
        BASE_PATH,
        {
          ...options,
          method: "POST",
          headers: {"Content-Type": "application/json", ...options?.headers},
          body: JSON.stringify(repo),
        }
      );
    },
    []
  );

  const removeRepo = useCallback(
    (name: string, options?: RequestInit) => {
      return customFetch<{ data: unknown; status: number; headers: Headers }>(
        `${BASE_PATH}/${encodeURIComponent(name)}`,
        {
          ...options,
          method: "DELETE",
        }
      );
    },
    []
  );

  const browseRepo = useCallback(
    (name: string, options?: RequestInit) => {
      return customFetch<{ data: { data: ToolsetRepoCatalog }; status: number; headers: Headers }>(
        `${BASE_PATH}/${encodeURIComponent(name)}/browse`,
        {
          ...options,
          method: "GET",
        }
      );
    },
    []
  );

  const fetchToolset = useCallback(
    (repoName: string, toolsetName: string, options?: RequestInit) => {
      return customFetch<{ data: { data: ToolReference[] }; status: number; headers: Headers }>(
        `${BASE_PATH}/${encodeURIComponent(repoName)}/toolsets/${encodeURIComponent(toolsetName)}`,
        {
          ...options,
          method: "GET",
        }
      );
    },
    []
  );

  return {
    listRepos,
    addRepo,
    removeRepo,
    browseRepo,
    fetchToolset,
  };
};
