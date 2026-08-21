import React, {useEffect, useState} from "react"
import {ComboBox} from "@carbon/react"
import {getInferenceUrl} from "../../custom-fetch"


interface LLMModelComboBoxProps {
  value?: string
  labelText?: string
  apiKey?: string
  onChange: (llmModel: string) => void
}


export const LLMModelComboBox: React.FC<LLMModelComboBoxProps> = ({ value, onChange, labelText, apiKey }) => {
  
  const [models, setModels] = useState<string[]>([])


  useEffect(() => {
    if (!apiKey) {
      return
    }

    const controller = new AbortController()

    // Debounced: apiKey changes on every keystroke in the password input,
    // and firing a request per character would spam the backend with
    // partial/invalid tokens.
    const timeoutId = setTimeout(() => {
      (async () => {
        try {
          const response = await fetch(getInferenceUrl("/v1/models"), {
            headers: { Authorization: `Bearer ${apiKey}` },
            signal: controller.signal
          })
          if (response.ok) {
            const data: { data: { id: string }[] } = await response.json()
            setModels(data.data.map(m => m.id))
          }
        } catch {
          // ignore (network error or aborted), leave catalog empty for this llm
        }
      })()
    }, 500)

    return () => {
      clearTimeout(timeoutId)
      controller.abort()
    }
  }, [apiKey])
  
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
