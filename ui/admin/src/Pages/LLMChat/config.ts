import {NamespaceEntry, ToolEntry} from "../../models"

export const STORE_IN_LOCAL_STORAGE = "storeInLocalStorage"
export const LLM_CONFIG = "llmConfig"

const DEFAULT_EXTRA_LLM_PARAMS = ""
const DEFAULT_SYSTEM_PROMPT = "You are helpful assistant that can use tools."
const DEFAULT_NAMESPACE = { name: "default", path: "default" }

export interface LlmConfig {
  selectedModel: string
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

export function persistConfig(config: LlmConfig) {
  localStorage.setItem(LLM_CONFIG, JSON.stringify(config))
}