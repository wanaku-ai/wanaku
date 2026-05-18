import React, {useRef, useState} from "react"
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
  STORE_IN_LOCAL_STORAGE
} from "./config.ts"
import {BaseUrlSelect} from "./BaseUrlSelect"
import {LLMChangeHandle, LLMModelComboBox} from "./LLMModelComboBox"


interface LLMSetupProps {
  config: LlmConfig
  onChange: (config: LlmConfig) => void
}

export const LLMSetup: React.FC<LLMSetupProps> = ({ config, onChange }) => {
  
  const [isStoredInLocalStorage, setStoreInLocalStorage] = useState<boolean>(isConfigStoredInLocalStorage())
  const llmModelComboBoxRef = useRef<LLMChangeHandle>({ llmBaseUrlChanged: () => {} })
  
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
        <BaseUrlSelect
          id="base-url"
          labelText="LLM API Base URL"
          value={config.baseUrl || ""}
          onChange={(baseUrl: string) => {
            applyConfigChange({ ...config, baseUrl })
            llmModelComboBoxRef.current.llmBaseUrlChanged(baseUrl)
          }}
        />
        <LLMModelComboBox
          labelText="LLM Model"
          value={config.llmModel}
          ref={llmModelComboBoxRef}
          onChange={(llmModel) => {
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