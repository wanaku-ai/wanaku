import {Select, SelectItem, SelectSkeleton} from "@carbon/react"
import React, {useEffect, useState} from "react"
import {getUrl} from "../../custom-fetch.ts"


interface BaseUrlSelectProps {
  id?: string
  labelText?: string
  helperText?: string
  value?: string
  onChange: (baseUrl: string) => void
}

export const BaseUrlSelect : React.FC<BaseUrlSelectProps> = ({ id, labelText, helperText, value, onChange }) => {
  
  const [baseUrls, setBaseUrls] = useState<string[]>([])
  const [isLoading, setLoading] = useState(true)
  
  useEffect(() => {
    (async () => {
      const response = await fetch(getUrl("/api/v1/chat/allowlist"))
      if (response.ok) {
        const data: string[] = await response.json()
        setBaseUrls(data)
        setLoading(false)
        if (data.length > 0) {
          onChange(selectedValue(data)!)
        }
      }
    })()
  }, [setBaseUrls, setLoading])
  
  function selectedValue(urls: string[]): string | undefined {
    return value || (urls.length > 0 ? urls[0] : undefined)
  }
  
  if (isLoading) {
    return (<SelectSkeleton />)
  } else {
    return (
      <Select
        id={id || "baseUrl"}
        labelText={labelText}
        helperText={helperText}
        value={selectedValue(baseUrls)}
        onChange={(event) => {
          const baseUrl = event.target.value
          onChange(baseUrl)
        }}
      >
        {baseUrls.map((url: string) => (
          <SelectItem
            id={url}
            key={url}
            text={url}
            value={url}
          />
        ))}
      </Select>
    )
  }
}