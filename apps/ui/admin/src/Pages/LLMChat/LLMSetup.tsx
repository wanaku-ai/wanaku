import React, {useState} from "react"
import {
  Form,
  PasswordInput, Stack,
  TextArea,
  Toggle
} from "@carbon/react"
import {
  isConfigStoredInLocalStorage,
  LLM_CONFIG,
  LlmConfig,
  persistConfig,
  STORE_IN_LOCAL_STORAGE
} from "./config.ts"
import {LLMSelect} from "./LLMSelect"
import {LLMModelComboBox} from "./LLMModelComboBox"


interface LLMSetupProps {
  config: LlmConfig
  onChange: (config: LlmConfig) => void
}

export const LLMSetup: React.FC<LLMSetupProps> = ({ config, onChange }) => {
  
  const [isStoredInLocalStorage, setStoreInLocalStorage] = useState<boolean>(isConfigStoredInLocalStorage())
  
  function applyConfigChange(config: LlmConfig) {
    if (isStoredInLocalStorage) {
      persistConfig(config)
    }
    onChange(config)
  }
  
  return (
    <Form>
      <Stack gap={5}>
        <Toggle
          labelText="Store LLM settings in Local Storage (the API key is never saved)"
          labelA="Off"
          labelB="On"
          toggled={isStoredInLocalStorage}
          onToggle={(value: boolean) => {
            localStorage.setItem(STORE_IN_LOCAL_STORAGE, value.toString())
            setStoreInLocalStorage(value)
            if (value) {
              persistConfig(config)
            } else {
              localStorage.removeItem(LLM_CONFIG)
            }
          }}
          id="enabledLocalStorage"
        />
        <LLMSelect
          id="base-url"
          labelText="LLM API"
          value={config.llm || ""}
          onChange={(llm: string) => {
            applyConfigChange({ ...config, llm })
          }}
        />
        <LLMModelComboBox
          labelText="LLM Model"
          llm={config.llm || ""}
          value={config.llmModel}
          onChange={(llmModel) => {
            applyConfigChange({ ...config, llmModel })
          }}
        />
        <PasswordInput
          id="api-key"
          labelText="API Key"
          helperText="For your security the API key is kept only in memory for this session and is never saved to local storage."
          placeholder="Type your API key here..."
          value={config.apiKey}
          onChange={(event) => {
            const apiKey = event.target.value
            applyConfigChange({ ...config, apiKey })
          }}
          size="md"
        />
        <TextArea
          id="extra-llm-input"
          labelText="Extra LLM Parameters"
          placeholder='Json format, e.g. {"max_tokens":400,"temperature":0.7,"tool_choice":"auto"}'
          value={config.extraLlmParams}
          onChange={(event) => {
            const extraLlmParams = event.target.value
            applyConfigChange({ ...config, extraLlmParams })
          }}
          rows={4}
        />
      </Stack>
    </Form>
  )
}