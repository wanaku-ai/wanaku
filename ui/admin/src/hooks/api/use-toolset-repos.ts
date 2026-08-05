import {useCallback} from "react";
import {
  getApiV1ToolsetRepos,
  postApiV1ToolsetRepos,
  putApiV1ToolsetReposName,
  deleteApiV1ToolsetReposName,
  getApiV1ToolsetReposNameBrowse,
  getApiV1ToolsetReposNameToolsetsToolsetName,
} from "../../api/wanaku-router-api";
import type {
  PostApiV1ToolsetReposBody,
  PutApiV1ToolsetReposNameBody,
} from "../../models";

// Custom interface types maintained for backward compatibility with ToolsetReposTab.tsx
// These represent the expected structure, which may differ from the raw API response
export interface ToolsetRepoSummary {
  name: string;
  url: string;
  branch?: string;
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

// Helper type to represent the hook's return types while using generated API functions
interface ListReposResult {
  data: {
    data?: ToolsetRepoSummary[];
  };
  status: number;
  headers: Headers;
}

interface AddRepoResult {
  data: {
    data?: ToolsetRepoSummary;
  };
  status: number;
  headers: Headers;
}

interface UpdateRepoResult {
  data: {
    data?: ToolsetRepoSummary;
  };
  status: number;
  headers: Headers;
}

interface RemoveRepoResult {
  data: unknown;
  status: number;
  headers: Headers;
}

interface BrowseRepoResult {
  data: {
    data?: ToolsetRepoCatalog;
  };
  status: number;
  headers: Headers;
}

interface FetchToolsetResult {
  data: {
    data?: ToolsetRepoSummary[];
  };
  status: number;
  headers: Headers;
}

export const useToolsetRepos = () => {
  const listRepos = useCallback(
    (options?: RequestInit): Promise<ListReposResult> => {
      // Cast to expected type - runtime conversion may be needed if API shape differs
      return getApiV1ToolsetRepos(options) as unknown as Promise<ListReposResult>;
    },
    []
  );

  const addRepo = useCallback(
    (repo: PostApiV1ToolsetReposBody, options?: RequestInit): Promise<AddRepoResult> => {
      return postApiV1ToolsetRepos(repo, options) as unknown as Promise<AddRepoResult>;
    },
    []
  );

  const updateRepo = useCallback(
    (name: string, repo: PutApiV1ToolsetReposNameBody, options?: RequestInit): Promise<UpdateRepoResult> => {
      return putApiV1ToolsetReposName(name, repo, options) as unknown as Promise<UpdateRepoResult>;
    },
    []
  );

  const removeRepo = useCallback(
    (name: string, options?: RequestInit): Promise<RemoveRepoResult> => {
      return deleteApiV1ToolsetReposName(name, options) as unknown as Promise<RemoveRepoResult>;
    },
    []
  );

  const browseRepo = useCallback(
    (name: string, options?: RequestInit): Promise<BrowseRepoResult> => {
      return getApiV1ToolsetReposNameBrowse(name, options) as unknown as Promise<BrowseRepoResult>;
    },
    []
  );

  const fetchToolset = useCallback(
    (repoName: string, toolsetName: string, options?: RequestInit): Promise<FetchToolsetResult> => {
      return getApiV1ToolsetReposNameToolsetsToolsetName(repoName, toolsetName, options) as unknown as Promise<FetchToolsetResult>;
    },
    []
  );

  return {
    listRepos,
    addRepo,
    updateRepo,
    removeRepo,
    browseRepo,
    fetchToolset,
  };
};
