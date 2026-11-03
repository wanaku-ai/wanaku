import React from "react"
import {Select, SelectItem} from "@carbon/react"


interface BooleanSelectProps {
  id: string
  labelText?: string
  value?: boolean
  onChange?: (value: boolean) => void
}


export const BooleanSelect: React.FC<BooleanSelectProps> = ({ id, labelText, value, onChange }) => {
  
  return (
    <Select
      id={id}
      labelText={labelText || ""}
      value={value ? "True" : "False"}
      onChange={event => {
        const value = event.target.value
        if (onChange) {
          onChange(value === "True")
        }
      }}
    >
      <SelectItem text="True" value="True" />
      <SelectItem text="False" value="False" />
    </Select>
  )
  
}