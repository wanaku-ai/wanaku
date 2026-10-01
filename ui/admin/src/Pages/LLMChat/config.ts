import { NamespaceEntry, ToolEntry } from '../../models';

export const STORE_IN_LOCAL_STORAGE = 'storeInLocalStorage';
export const STORE_API_KEY_IN_LOCAL_STORAGE = 'storeApiKeyInLocalStorage';
export const LLM_CONFIG = 'llmConfig';

const DEFAULT_EXTRA_LLM_PARAMS = '';
const DEFAULT_SYSTEM_PROMPT = 'You are helpful assistant that can use tools.';
const DEFAULT_NAMESPACE = { name: 'default', path: 'default' };

export interface LlmConfig {
  selectedModel: string;
  apiKey?: string;
  selectedNamespace: NamespaceEntry;
  selectedTools: ToolEntry[];
  systemPrompt: string;
  extraLlmParams: string;
}

export function defaultLlmConfig(): LlmConfig {
  return {
    selectedModel: '',
    selectedNamespace: DEFAULT_NAMESPACE,
    selectedTools: [],
    systemPrompt: DEFAULT_SYSTEM_PROMPT,
    extraLlmParams: DEFAULT_EXTRA_LLM_PARAMS,
  };
}

export function isConfigStoredInLocalStorage() {
  return localStorage.getItem(STORE_IN_LOCAL_STORAGE) === 'true';
}

export function isApiKeyStoredInLocalStorage() {
  return localStorage.getItem(STORE_API_KEY_IN_LOCAL_STORAGE) === 'true';
}

function parseConfig(json: string): LlmConfig {
  const config: LlmConfig = JSON.parse(json);
  config.selectedModel ??= '';
  config.selectedNamespace ??= DEFAULT_NAMESPACE;
  config.selectedTools ??= [];
  config.systemPrompt ??= DEFAULT_SYSTEM_PROMPT;
  config.extraLlmParams ??= DEFAULT_EXTRA_LLM_PARAMS;
  return config;
}

export function loadConfig(): LlmConfig {
  if (isConfigStoredInLocalStorage()) {
    const configJson = localStorage.getItem(LLM_CONFIG);
    if (configJson) {
      try {
        const config = parseConfig(configJson);
        if (!isApiKeyStoredInLocalStorage()) {
          // The API key is restored only when the user opted in to storing it. Otherwise strip any
          // key that may be present in the stored payload.
          config.apiKey = undefined;
        }
        return config;
      } catch (error) {
        console.log(`Error loading config: ${error}`);
        return defaultLlmConfig();
      }
    }
  }
  return defaultLlmConfig();
}

/**
 * Persists the LLM config to local storage.
 *
 * Local storage is the source of truth for both persistence decisions, so this reads the flags at
 * write time rather than trusting caller state. That prevents a stale caller (for example another
 * browser tab that still holds outdated state) from writing after storage was turned off.
 *
 * When config storage is disabled, nothing is written. The API key is sensitive: it is written only
 * when the user explicitly opted in. Otherwise it is stripped and kept in memory for the current
 * session only, since a stored key is readable by any script or extension that runs on the page.
 */
export function persistConfig(config: LlmConfig) {
  if (!isConfigStoredInLocalStorage()) {
    return;
  }
  const safeConfig: LlmConfig = structuredClone(config);
  if (!isApiKeyStoredInLocalStorage()) {
    // JSON.stringify drops undefined values, so the api key is omitted from the stored payload.
    safeConfig.apiKey = undefined;
  }
  localStorage.setItem(LLM_CONFIG, JSON.stringify(safeConfig));
}
