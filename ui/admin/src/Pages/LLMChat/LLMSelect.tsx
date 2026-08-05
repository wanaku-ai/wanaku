import {Select, SelectItem, SelectSkeleton} from "@carbon/react"
import React, {useEffect, useState} from "react"
import {getUrl} from "../../custom-fetch.ts"


interface LLMSelectProps {
  id?: string
  labelText?: string
  helperText?: string
  value?: string
  onChange: (baseUrl: string) => void
}

export const LLMSelect : React.FC<LLMSelectProps> = ({ id, labelText, helperText, value, onChange }) => {
  
  const [llms, setLlms] = useState<string[]>([])
  const [isLoading, setLoading] = useState(true)
  
  useEffect(() => {
    (async () => {
      const response = await fetch(getUrl("/api/v1/chat/llms"))
      if (response.ok) {
        const data: string[] = await response.json()
        setLlms(data)
        setLoading(false)
        if (data.length > 0) {
          onChange(selectedValue(data)!)
        }
      }
    })()
  }, [setLlms, setLoading])
  
  function selectedValue(llms: string[]): string | undefined {
    return value || (llms.length > 0 ? llms[0] : undefined)
  }
  
  if (isLoading) {
    return (<SelectSkeleton />)
  } else {
    return (
      <Select
        id={id || "llm"}
        labelText={labelText}
        helperText={helperText}
        value={selectedValue(llms)}
        onChange={(event) => {
          const llm = event.target.value
          onChange(llm)
        }}
      >
        {llms.map((name: string) => (
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
}