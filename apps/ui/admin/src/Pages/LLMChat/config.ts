import {ToolReference} from "../../models"

export const STORE_IN_LOCAL_STORAGE = "storeInLocalStorage"
export const LLM_CONFIG = "llmConfig"

export const ANTHROPIC = "Anthropic"
export const GEMINI = "Gemini"
export const MISTRAL = "Mistral"
export const OLLAMA = "Ollama"
export const OPENAI = "OpenAi"

const DEFAULT_EXTRA_LLM_PARAMS = ""
const DEFAULT_SYSTEM_PROMPT = "You are helpful assistant that can use tools."
const DEFAULT_LLM = "Mistral"
const DEFAULT_ANTHROPIC_MODEL = "claude-4-6-sonnet"
const DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"
const DEFAULT_MISTRAL_MODEL = "mistral-small-latest"
const DEFAULT_OPENAI_MODEL = "gpt-5.4-mini"

export interface LlmConfig {
  selectedLlm: string
  llms: Record<string, { selectedModel: string, apiKey?: string, extraLlmParams: string } >
  selectedTools: ToolReference[]
  systemPrompt: string
}

export function defaultLlmConfig(): LlmConfig {
  return {
    selectedLlm: DEFAULT_LLM,
    selectedTools: [],
    llms: {
      [ANTHROPIC]: createSubconfig(DEFAULT_ANTHROPIC_MODEL),
      [GEMINI]: createSubconfig(DEFAULT_GEMINI_MODEL),
      [MISTRAL]: createSubconfig(DEFAULT_MISTRAL_MODEL),
      [OLLAMA]: createSubconfig(""),
      [OPENAI]: createSubconfig(DEFAULT_OPENAI_MODEL)
    },
    systemPrompt: DEFAULT_SYSTEM_PROMPT
  }
}

export function isConfigStoredInLocalStorage() {
  return localStorage.getItem(STORE_IN_LOCAL_STORAGE) === "true"
}

function createSubconfig(model: string) {
  return { selectedModel: model, extraLlmParams: DEFAULT_EXTRA_LLM_PARAMS }
}

function parseConfig(json: string): LlmConfig {
  const config: LlmConfig = JSON.parse(json)
  config.selectedLlm ??= DEFAULT_LLM
  config.llms ??= {}
  config.llms[ANTHROPIC] ??= createSubconfig(DEFAULT_ANTHROPIC_MODEL)
  config.llms[ANTHROPIC].selectedModel ??= DEFAULT_ANTHROPIC_MODEL
  config.llms[ANTHROPIC].extraLlmParams ??= DEFAULT_EXTRA_LLM_PARAMS
  config.llms[GEMINI] ??= createSubconfig(DEFAULT_GEMINI_MODEL)
  config.llms[GEMINI].selectedModel ??= DEFAULT_GEMINI_MODEL
  config.llms[GEMINI].extraLlmParams ??= DEFAULT_EXTRA_LLM_PARAMS
  config.llms[MISTRAL] ??= createSubconfig(DEFAULT_MISTRAL_MODEL)
  config.llms[MISTRAL].selectedModel ??= DEFAULT_MISTRAL_MODEL
  config.llms[MISTRAL].extraLlmParams ??= DEFAULT_EXTRA_LLM_PARAMS
  config.llms[OLLAMA] ??= createSubconfig("")
  config.llms[OLLAMA].selectedModel ??= ""
  config.llms[OLLAMA].extraLlmParams ??= DEFAULT_EXTRA_LLM_PARAMS
  config.llms[OPENAI] ??= createSubconfig(DEFAULT_OPENAI_MODEL)
  config.llms[OPENAI].selectedModel ??= DEFAULT_OPENAI_MODEL
  config.llms[OPENAI].extraLlmParams ??= DEFAULT_EXTRA_LLM_PARAMS
  config.selectedTools ??= []
  config.systemPrompt ??= DEFAULT_SYSTEM_PROMPT
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
  for (let llm in safeConfig.llms) {
    safeConfig.llms[llm].apiKey = undefined
  }
  localStorage.setItem(LLM_CONFIG, JSON.stringify(safeConfig))
}