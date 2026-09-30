import React from "react"
import {
  Form,
  InlineNotification,
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
  apiKeyStored: boolean
  onConfigChange: (config: LlmConfig) => void
  onStoredChange: (store: boolean) => void
  onApiKeyStoredChange: (store: boolean) => void
}

export const LLMSetup: React.FC<LLMSetupProps> = ({ config, stored, apiKeyStored, onConfigChange, onStoredChange, onApiKeyStoredChange }) => {
  
  return (
    <Form>
      <Stack gap={5}>
        <Toggle
          labelText="Store LLM settings in Local Storage"
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
        <Toggle
          labelText="Also remember the API key in Local Storage"
          labelA="Off"
          labelB="On"
          toggled={apiKeyStored}
          disabled={!stored}
          onToggle={onApiKeyStoredChange}
          id="enabledApiKeyStorage"
        />
        {apiKeyStored && (
          <InlineNotification
            kind="warning"
            title="Security warning"
            subtitle="The API key is saved in your browser Local Storage. Any script or browser extension that runs on this page can read it. Enable this only on a trusted device."
            lowContrast
            hideCloseButton
          />
        )}
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