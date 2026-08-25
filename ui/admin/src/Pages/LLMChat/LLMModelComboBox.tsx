import React, {useEffect, useState} from "react"
import {ComboBox} from "@carbon/react"
import {getInferenceUrl} from "../../custom-fetch"


interface LLMModelComboBoxProps {
  llm?: string
  value?: string
  labelText?: string
  apiKey?: string
  onChange: (llmModel: string) => void
}

export const LLMModelComboBox: React.FC<LLMModelComboBoxProps> = ({ llm, value, onChange, labelText, apiKey }) => {

  const [modelCatalog, setModelCatalog] = useState<{ [llm: string]: string[] }>({})


  useEffect(() => {
    if (!llm || !apiKey) {
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
            setModelCatalog({ [llm]: data.data.map(m => m.id) })
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
  }, [llm, apiKey])

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
