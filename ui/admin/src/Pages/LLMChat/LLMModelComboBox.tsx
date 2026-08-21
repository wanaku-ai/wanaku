import React, {useEffect, useState} from "react"
import {ComboBox} from "@carbon/react"
import {getUrl} from "../../custom-fetch"


interface LLMModelComboBoxProps {
  value?: string
  labelText?: string
  onChange: (llmModel: string) => void
}

export const LLMModelComboBox: React.FC<LLMModelComboBoxProps> = ({ value, onChange, labelText }) => {
  
  const [models, setModels] = useState<string[]>([])
  
  
  useEffect(() => {
    (async () => {
      const response = await fetch(getUrl("/api/v1/chat/models"))
      if (response.ok) {
        const models: string[] = await response.json()
        setModels(models)
      }
    })()
  }, [setModels])
  
  return (
    <ComboBox
      id="llm-model"
      titleText={labelText}
      items={models}
      allowCustomValue
      selectedItem={value}
      onChange={(event) => {
        setTimeout(() => {
          const model = event.selectedItem || event.inputValue || ""
          onChange(model)
        }, 0)
      }}
    />
  )
}