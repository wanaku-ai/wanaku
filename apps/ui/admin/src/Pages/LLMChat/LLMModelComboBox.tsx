import React, {RefObject, useImperativeHandle, useState} from "react"
import {ComboBox} from "@carbon/react"


export interface LLMChangeHandle {
  llmBaseUrlChanged: (baseUrl: string) => void
}

interface LLMModelComboBoxProps {
  value?: string
  labelText?: string
  onChange: (llmModel: string) => void
  ref?: RefObject<LLMChangeHandle>
}

export const LLMModelComboBox: React.FC<LLMModelComboBoxProps> = ({ value, onChange, labelText, ref }) => {
  
  const [modelSuggestions, setModelSuggestions] = useState<string[]>([])
  
  const llmModelSuggestions = {
    "https://api.openai.com": ["gpt-4o", "gpt-4o-mini", "o3-mini", "o1", "o1-mini"],
    "https://api.mistral.ai": ["mistral-small-latest"],
    "https://generativelanguage.googleapis.com/v1beta/openai/": ["gemini-3.1-pro", "gemini-2.5-pro"],
    "https://api.anthropic.com": ["claude-4.7-opus", "claude-4.6-opus"]
  }
  
  useImperativeHandle(ref, (): LLMChangeHandle => ({
    llmBaseUrlChanged(baseUrl: string) {
      setModelSuggestions(createItems(baseUrl))
    }
  }), [])

  function createItems(baseUrl: string) {
    return llmModelSuggestions[baseUrl] || []
  }
  
  return (
    <ComboBox
      id="llm-model"
      titleText={labelText}
      items={modelSuggestions}
      allowCustomValue
      value={value}
      onChange={(event) => {
        const llmModel = event.selectedItem || event.inputValue || ""
        onChange(llmModel)
      }}
    />
    
  )

}