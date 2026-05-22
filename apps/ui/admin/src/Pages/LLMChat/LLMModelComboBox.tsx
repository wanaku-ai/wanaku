import React from "react"
import {ComboBox} from "@carbon/react"
import {LlmConfig} from "./config"


interface LLMModelComboBoxProps {
  config?: LlmConfig
  labelText?: string
  onChange: (llmModel: string) => void
}

export const LLMModelComboBox: React.FC<LLMModelComboBoxProps> = ({ config, onChange, labelText }) => {
  
  const llmModelSuggestions = {
    "https://api.openai.com": ["gpt-4o", "gpt-4o-mini", "o3-mini", "o1", "o1-mini"],
    "https://api.mistral.ai": ["mistral-small-latest"],
    "https://generativelanguage.googleapis.com/v1beta/openai/": ["gemini-3.1-pro", "gemini-2.5-pro"],
    "https://api.anthropic.com": ["claude-4.7-opus", "claude-4.6-opus"]
  }
  
  function createItems(): string[] {
    const baseUrl = config?.baseUrl || undefined
    return (baseUrl && llmModelSuggestions[baseUrl]) ? llmModelSuggestions[baseUrl] : []
  }
  
  return (
    <ComboBox
      id="llm-model"
      titleText={labelText}
      items={createItems()}
      allowCustomValue
      selectedItem={config?.llmModel}
      onChange={(event) => {
        setTimeout(() => {
          const llmModel = event.selectedItem || event.inputValue || ""
          onChange(llmModel)
        }, 0)
      }}
    />
  )
}