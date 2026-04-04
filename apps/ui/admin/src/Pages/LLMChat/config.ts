import {Tool} from "./tools.ts"


export interface LlmConfig {
  baseUrl?: string | null
  llmModel?: string
  apiKey?: string
  extraLlmParams?: string
  tools: Tool[]
}

export const baseUrlItems = [
  "http://localhost:11434",
  "http://localhost:1443/api",
  "https://api.openai.com",
  "https://api.mistral.ai",
  "https://generativelanguage.googleapis.com/v1beta/openai/",
  "https://api.anthropic.com"
]

export function defaultLlmConfig(): LlmConfig {
  return {
    baseUrl: baseUrlItems[2],
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
      return JSON.parse(config)
    }
  }
  return defaultLlmConfig()
}