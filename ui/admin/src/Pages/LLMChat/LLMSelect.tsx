import {Select, SelectItem} from "@carbon/react"
import React, {useEffect} from "react"


interface LLMSelectProps {
  id?: string
  labelText?: string
  helperText?: string
  value?: string
  onChange: (baseUrl: string) => void
}

const LLMS = ["Inference"]

export const LLMSelect : React.FC<LLMSelectProps> = ({ id, labelText, helperText, value, onChange }) => {

  function selectedValue(llms: string[]): string | undefined {
    if (value && llms.includes(value)) return value
    return llms.length > 0 ? llms[0] : undefined
  }

  useEffect(() => {
    onChange(selectedValue(LLMS)!)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <Select
      id={id || "llm"}
      labelText={labelText}
      helperText={helperText}
      value={selectedValue(LLMS)}
      onChange={(event) => {
        const llm = event.target.value
        onChange(llm)
      }}
    >
      {LLMS.map((name: string) => (
        <SelectItem
          id={name}
          key={name}
          text={name}
          value={name}
        />
      ))}
    </Select>
  )
}
