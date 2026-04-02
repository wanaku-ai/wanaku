import React, {useState} from "react"
import {
  ComboBox,
  Form,
  PasswordInput, Stack,
  TextArea,
  TextInput,
  Toggle
} from "@carbon/react"
import {
  baseUrlItems,
  isConfigStoredInLocalStorage,
  LLM_CONFIG,
  LlmConfig,
  STORE_IN_LOCAL_STORAGE
} from "./config.ts"


interface LLMSetupProps {
  config: LlmConfig
  onChange: (config: LlmConfig) => void
}

export const LLMSetup: React.FC<LLMSetupProps> = ({ config, onChange }) => {
  
  const [isStoredInLocalStorage, setStoreInLocalStorage] = useState<boolean>(isConfigStoredInLocalStorage())
  
  function applyConfigChange(config: LlmConfig) {
    if (isStoredInLocalStorage) {
      localStorage.setItem(LLM_CONFIG, JSON.stringify(config))
    }
    onChange(config)
  }
  
  return (
    <Form>
      <Stack gap={5}>
        <Toggle
          labelText="Store in Local Storage"
          labelA="Off"
          labelB="On"
          toggled={isStoredInLocalStorage}
          onToggle={(value: boolean) => {
            localStorage.setItem(STORE_IN_LOCAL_STORAGE, value.toString())
            setStoreInLocalStorage(value)
            if (value) {
              localStorage.setItem(LLM_CONFIG, JSON.stringify(config))
            } else {
              localStorage.removeItem(LLM_CONFIG)
            }
          }}
          id="enabledLocalStorage"
        />
        <ComboBox
          id="base-url"
          titleText="LLM API Base URL"
          items={baseUrlItems}
          selectedItem={config.baseUrl}
          allowCustomValue
          onChange={(event) => {
            const baseUrl = event.selectedItem
            applyConfigChange({ ...config, baseUrl })
          }}
        />
        <TextInput
          id="llm-model"
          labelText="LLM Model"
          placeholder="Type LLM model here..."
          value={config.llmModel}
          onChange={(event) => {
            const llmModel = event.target.value
            applyConfigChange({ ...config, llmModel })
          }}
        />
        <PasswordInput
          id="api-key"
          labelText="API Key"
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