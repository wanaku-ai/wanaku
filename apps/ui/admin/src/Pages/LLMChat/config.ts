import {ToolReference} from "../../models"


export interface LlmConfig {
  baseUrl?: string | null
  llmModel?: string
  apiKey?: string
  extraLlmParams?: string
  tools: ToolReference[]
}

export function defaultLlmConfig(): LlmConfig {
  return {
    baseUrl: "https://api.mistral.ai",
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
      } catch (error) {
        console.log("Error loading config: " + config)
        return defaultLlmConfig()
      }
    }
  }
  return defaultLlmConfig()
}