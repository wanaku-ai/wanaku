import React, {useEffect, useState} from "react"
import {ComboBox} from "@carbon/react"
import {getUrl} from "../../custom-fetch"


interface LLMModelComboBoxProps {
  llm?: string
  value?: string
  labelText?: string
  onChange: (llmModel: string) => void
}

export const LLMModelComboBox: React.FC<LLMModelComboBoxProps> = ({ llm, value, onChange, labelText }) => {
  
  const [modelCatalog, setModelCatalog] = useState<{ [llm: string]: string[] }>({})
  
  
  useEffect(() => {
    (async () => {
      const response = await fetch(getUrl("/api/v1/chat/llms"))
      if (response.ok) {
        const llms: string[] = await response.json()
        const modelCatalog = {}
        for (const llm of llms) {
          const response = await fetch(getUrl(`/api/v1/chat/${llm}/models`))
          if (response.ok) {
            const models: string[] = await response.json()
            modelCatalog[llm] = models
          }
        }
        setModelCatalog(modelCatalog)
      }
    })()
  }, [setModelCatalog])
  
  function createItems(): string[] {
    return (llm && modelCatalog[llm]) ? modelCatalog[llm] : []
  }
  
  return (
    <ComboBox
      id="llm-model"
      titleText={labelText}
      items={createItems()}
      allowCustomValue
      selectedItem={value}
      onChange={(event) => {
        setTimeout(() => {
          const llmModel = event.selectedItem || event.inputValue || ""
          onChange(llmModel)
        }, 0)
      }}
    />
  )
}