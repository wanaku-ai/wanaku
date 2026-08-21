import React from "react"
import {
  Form,
  PasswordInput,
  Stack,
  TextArea,
  Toggle
} from "@carbon/react"
import {LlmConfig} from "./config"
import {LLMModelComboBox} from "./LLMModelComboBox"


interface LLMSetupProps {
  config: LlmConfig
  stored: boolean
  onConfigChange: (config: LlmConfig) => void
  onStoredChange: (store: boolean) => void
}

export const LLMSetup: React.FC<LLMSetupProps> = ({ config, stored, onConfigChange, onStoredChange }) => {
  
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
        <LLMModelComboBox
          labelText="LLM Model"
          apiKey={config.apiKey}
          value={config.selectedModel}
          onChange={(selectedModel: string) => {
            const newConfig = structuredClone(config)
            newConfig.selectedModel = selectedModel
            onConfigChange(newConfig)
          }}
        />
        <PasswordInput
          id="api-key"
          labelText="API Key"
          placeholder="Type your API key here..."
          value={config.apiKey}
          onChange={(event) => {
            const apiKey = event.target.value
            const newConfig = structuredClone(config)
            newConfig.apiKey = apiKey
            onConfigChange(newConfig)
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
            const newConfig = structuredClone(config)
            newConfig.extraLlmParams = extraLlmParams
            onConfigChange(newConfig)
          }}
          rows={4}
        />
      </Stack>
    </Form>
  )
}