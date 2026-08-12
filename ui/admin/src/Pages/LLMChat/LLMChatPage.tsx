import React, {useEffect, useState} from "react"
import "highlight.js/styles/atom-one-dark.css"
import {LLMSetup} from "./LLMSetup.tsx"
import {LLMTools} from "./LLMTools.tsx"
import {LLMChatArea, LlmChatConfig} from "./LLMChatArea"
import {Column, Grid, ToastNotification} from "@carbon/react"
import {
  isConfigStoredInLocalStorage,
  LLM_CONFIG,
  LlmConfig,
  loadConfig,
  persistConfig,
  STORE_IN_LOCAL_STORAGE
} from "./config"


export const LLMChatPage: React.FC = () => {
  
  const [isStoredInLocalStorage, setStoreInLocalStorage] = useState(isConfigStoredInLocalStorage())
  const [config, setConfig] = useState<LlmConfig>(loadConfig())
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => setErrorMessage(null), 10000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);
  
  
  function applyConfigChange(config: LlmConfig) {
    if (isStoredInLocalStorage) {
      persistConfig(config)
    }
    setConfig(config)
  }
  
  function createChatConfig(): LlmChatConfig {
    const llm = config.selectedLlm
    return {
      llm: llm,
      model: config.llms[llm].selectedModel,
      apiKey: config.llms[llm].apiKey,
      extraLlmParams: config.llms[llm].extraLlmParams,
      selectedTools: config.selectedTools,
      systemPrompt: config.systemPrompt
    }
  }
  
  return (
    <div>
      {errorMessage && (
        <ToastNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          timeout={10000}
          style={{float: "right"}}
        />
      )}
      <h1 className="title">LLM Chat for testing</h1>
      <Grid fullWidth>
        <Column lg={4}>
          <LLMSetup
            config={config}
            stored={isStoredInLocalStorage}
            onConfigChange={(config: LlmConfig) => {
              applyConfigChange(config)
            }}
            onStoredChange={(value: boolean) => {
              localStorage.setItem(STORE_IN_LOCAL_STORAGE, value.toString())
              setStoreInLocalStorage(value)
              if (value) {
                persistConfig(config)
              } else {
                localStorage.removeItem(LLM_CONFIG)
              }
            }}
          />
          <LLMTools
            selectedNamespace={config.selectedNamespace}
            selectedTools={config.selectedTools}
            onSelectionChange={(selectedNamespace, selectedTools) => {
              applyConfigChange({ ...config, selectedNamespace, selectedTools })
            }}
            onError={(msg) => setErrorMessage(msg)}
          />
        </Column>
        <Column lg={12}>
          <LLMChatArea
            config={createChatConfig()}
            onSystemPromptChange={(systemPrompt) => {
              applyConfigChange({ ...config, systemPrompt })
            }}
          />
        </Column>
      </Grid>
    </div>
  )
}