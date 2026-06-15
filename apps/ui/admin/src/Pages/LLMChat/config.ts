import {ToolReference} from "../../models"


export interface LlmConfig {
  llm?: string | null
  llmModel?: string
  apiKey?: string
  extraLlmParams?: string
  tools: ToolReference[]
}

export function defaultLlmConfig(): LlmConfig {
  return {
    llm: "Mistral",
    llmModel: "mistral-small-latest",
    extraLlmParams: '{"max_tokens": 400, "temperature": 0.7, "tool_choice": "auto"}',
    tools: []
  }
}

export const STORE_IN_LOCAL_STORAGE = "storeInLocalStorage"
export const LLM_CONFIG = "llmConfig"

export function isConfigStoredInLocalStorage() {
  return localStorage.getItem(STORE_IN_LOCAL_STORAGE) === "true"
}

export function loadConfig(): LlmConfig {
  if (isConfigStoredInLocalStorage()) {
    const config = localStorage.getItem(LLM_CONFIG)
    if (config) {
      try {
        return JSON.parse(config)
      } catch {
        console.log("Error loading config: " + config)
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
  const safe: LlmConfig = { ...config, apiKey: undefined }
  localStorage.setItem(LLM_CONFIG, JSON.stringify(safe))
}