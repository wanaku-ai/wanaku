import React, {useEffect, useState} from "react"
import {LLMSetup} from "./LLMSetup.tsx"
import {LLMTools} from "./LLMTools.tsx"
import {LLMChatArea} from "./LLMChatArea"
import {Column, Grid} from "@carbon/react"
import {
  isApiKeyStoredInLocalStorage,
  isConfigStoredInLocalStorage,
  LLM_CONFIG,
  LlmConfig,
  loadConfig,
  persistConfig,
  STORE_API_KEY_IN_LOCAL_STORAGE,
  STORE_IN_LOCAL_STORAGE
} from "./config"
import {useErrorNotification} from "../../hooks/error-notifications"
import {ErrorNotification} from "../../components/ErrorNotification"


export const LLMChatPage: React.FC = () => {
  
  const [isStoredInLocalStorage, setStoreInLocalStorage] = useState(isConfigStoredInLocalStorage())
  // The API key opt-in only holds when config storage is also enabled, so gate the initial state on
  // both flags to avoid showing an "on" opt-in (and its warning) while nothing is actually stored.
  const [isApiKeyStored, setApiKeyStored] = useState(isConfigStoredInLocalStorage() && isApiKeyStoredInLocalStorage())
  const [config, setConfig] = useState<LlmConfig>(loadConfig())
  const { errorMessage, setErrorMessage } = useErrorNotification()

  useEffect(() => {
    // Another tab in the same browser can change the storage flags. Keep the toggles (and the
    // security warning) in sync so this tab does not keep showing storage as enabled after another
    // tab turned it off. The in-memory config is left untouched to preserve the current session.
    const syncFromStorage = () => {
      setStoreInLocalStorage(isConfigStoredInLocalStorage())
      setApiKeyStored(isConfigStoredInLocalStorage() && isApiKeyStoredInLocalStorage())
    }
    window.addEventListener("storage", syncFromStorage)
    return () => window.removeEventListener("storage", syncFromStorage)
  }, [])

  function applyConfigChange(config: LlmConfig) {
    // persistConfig reads the storage flags itself and is a no-op when storage is disabled.
    persistConfig(config)
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
            apiKeyStored={isApiKeyStored}
            onConfigChange={(config: LlmConfig) => {
              applyConfigChange(config)
            }}
            onStoredChange={(value: boolean) => {
              localStorage.setItem(STORE_IN_LOCAL_STORAGE, value.toString())
              setStoreInLocalStorage(value)
              if (value) {
                persistConfig(config)
              } else {
                // Turning off storage clears the persisted config and the API key opt-in.
                localStorage.removeItem(LLM_CONFIG)
                localStorage.removeItem(STORE_API_KEY_IN_LOCAL_STORAGE)
                setApiKeyStored(false)
              }
            }}
            onApiKeyStoredChange={(value: boolean) => {
              localStorage.setItem(STORE_API_KEY_IN_LOCAL_STORAGE, value.toString())
              setApiKeyStored(value)
              // Re-persist so the stored payload immediately gains or loses the API key.
              persistConfig(config)
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
            onError={(error: string) => {
              setErrorMessage(error)
            }}
            onSystemPromptChange={(systemPrompt) => {
              applyConfigChange({ ...config, systemPrompt })
            }}
          />
        </Column>
      </Grid>
    </div>
  )
}