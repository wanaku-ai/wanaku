import React from "react"
import {
  Form,
  PasswordInput,
  Stack,
  TextArea,
  Toggle
} from "@carbon/react"
import {LlmConfig, OLLAMA} from "./config"
import {LLMSelect} from "./LLMSelect"
import {LLMModelComboBox} from "./LLMModelComboBox"


interface LLMSetupProps {
  config: LlmConfig
  stored: boolean
  onConfigChange: (config: LlmConfig) => void
  onStoredChange: (store: boolean) => void
}

export const LLMSetup: React.FC<LLMSetupProps> = ({ config, stored, onConfigChange, onStoredChange }) => {

  const selectedLlm = config.selectedLlm
  const selectedModel = config.llms[selectedLlm].selectedModel
  const apiKey = config.llms[selectedLlm].apiKey
  const extraLlmParams = config.llms[selectedLlm].extraLlmParams
  
  const isApiKeyRequired = selectedLlm !== OLLAMA

  return (
    <Form>
      <Stack gap={5}>
        <Toggle
          labelText="Store LLM settings in Local Storage (the API key is never saved)"
          labelA="Off"
          labelB="On"
          toggled={stored}
          onToggle={onStoredChange}
          id="enabledLocalStorage"
        />
        <LLMSelect
          id="base-url"
          labelText="LLM API"
          value={selectedLlm}
          onChange={(selectedLlm: string) => {
            onConfigChange({ ...config, selectedLlm })
          }}
        />
        <LLMModelComboBox
          labelText="LLM Model"
          llm={selectedLlm}
          value={selectedModel}
          onChange={(selectedModel: string) => {
            const newConfig = structuredClone(config)
            newConfig.llms[selectedLlm].selectedModel = selectedModel
            onConfigChange(newConfig)
          }}
        />
        {isApiKeyRequired && (
          <PasswordInput
            id="api-key"
            labelText="API Key"
            placeholder="Type your API key here..."
            value={apiKey}
            onChange={(event) => {
              const apiKey = event.target.value
              const newConfig = structuredClone(config)
              newConfig.llms[selectedLlm].apiKey = apiKey
              onConfigChange(newConfig)
            }}
            size="md"
          />
        )}
        <TextArea
          id="extra-llm-input"
          labelText="Extra LLM Parameters"
          placeholder='Json format, e.g. {"max_tokens":400,"temperature":0.7,"tool_choice":"auto"}'
          value={extraLlmParams}
          onChange={(event) => {
            const extraLlmParams = event.target.value
            const newConfig = structuredClone(config)
            newConfig.llms[selectedLlm].extraLlmParams = extraLlmParams
            onConfigChange(newConfig)
          }}
          rows={4}
        />
      </Stack>
    </Form>
  )
}