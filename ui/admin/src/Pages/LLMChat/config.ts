import {NamespaceEntry, ToolEntry} from "../../models"

export const STORE_IN_LOCAL_STORAGE = "storeInLocalStorage"
export const LLM_CONFIG = "llmConfig"

const DEFAULT_EXTRA_LLM_PARAMS = ""
const DEFAULT_SYSTEM_PROMPT = "You are helpful assistant that can use tools."
const DEFAULT_NAMESPACE = { name: "default", path: "default" }

export interface LlmConfig {
  selectedModel: string
  apiKey?: string
  selectedNamespace: NamespaceEntry
  selectedTools: ToolEntry[]
  systemPrompt: string
  extraLlmParams: string
}

export function defaultLlmConfig(): LlmConfig {
  return {
    selectedModel: "",
    selectedNamespace: DEFAULT_NAMESPACE,
    selectedTools: [],
    systemPrompt: DEFAULT_SYSTEM_PROMPT,
    extraLlmParams: DEFAULT_EXTRA_LLM_PARAMS
  }
}

export function isConfigStoredInLocalStorage() {
  return localStorage.getItem(STORE_IN_LOCAL_STORAGE) === "true"
}

function parseConfig(json: string): LlmConfig {
  const config: LlmConfig = JSON.parse(json)
  config.selectedModel ??= ""
  config.selectedNamespace ??= DEFAULT_NAMESPACE
  config.selectedTools ??= []
  config.systemPrompt ??= DEFAULT_SYSTEM_PROMPT
  config.extraLlmParams ??= DEFAULT_EXTRA_LLM_PARAMS
  return config
}

export function loadConfig(): LlmConfig {
  if (isConfigStoredInLocalStorage()) {
    const configJson = localStorage.getItem(LLM_CONFIG)
    if (configJson) {
      try {
        return parseConfig(configJson)
      } catch (error) {
        console.log(`Error loading config: ${error}`)
        return defaultLlmConfig()
      }
    }
  }
  return defaultLlmConfig()
}

/**
 * Persists the LLM config to local storage, excluding the API key. The API key is sensitive and is
 * kept in memory for the current session only — it is never written to local storage (where it
 * would be readable by any script/extension on the page).
 */
export function persistConfig(config: LlmConfig) {
  // JSON.stringify drops undefined values, so the api key is omitted from the stored payload.
  const safeConfig: LlmConfig = structuredClone(config)
  safeConfig.apiKey = undefined
  localStorage.setItem(LLM_CONFIG, JSON.stringify(safeConfig))
}