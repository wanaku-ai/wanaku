import React, {useState} from "react"
import {LLMSetup} from "./LLMSetup.tsx"
import {LLMTools} from "./LLMTools.tsx"
import {LLMChatArea} from "./LLMChatArea"
import {Column, Grid} from "@carbon/react"
import {
  isConfigStoredInLocalStorage,
  LLM_CONFIG,
  LlmConfig,
  loadConfig,
  persistConfig,
  STORE_IN_LOCAL_STORAGE
} from "./config"
import {useErrorNotification} from "../../hooks/error-notifications"
import {ErrorNotification} from "../../components/ErrorNotification"


export const LLMChatPage: React.FC = () => {
  
  const [isStoredInLocalStorage, setStoreInLocalStorage] = useState(isConfigStoredInLocalStorage())
  const [config, setConfig] = useState<LlmConfig>(loadConfig())
  const { errorMessage, setErrorMessage } = useErrorNotification()
  
  
  function applyConfigChange(config: LlmConfig) {
    if (isStoredInLocalStorage) {
      persistConfig(config)
    }
    setConfig(config)
  }
  
  return (
    <div>
      {errorMessage && (
        <ErrorNotification
          errorMessage={errorMessage}
          onClose={() => setErrorMessage(null)}
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
            config={config}
            onSystemPromptChange={(systemPrompt) => {
              applyConfigChange({ ...config, systemPrompt })
            }}
          />
        </Column>
      </Grid>
    </div>
  )
}