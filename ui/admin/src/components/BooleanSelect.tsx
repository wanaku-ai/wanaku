import React from "react"
import {Select, SelectItem} from "@carbon/react"


interface BooleanSelectProps {
  id: string
  labelText?: string
}


export const BooleanSelect: React.FC<BooleanSelectProps> = ({ id, labelText }) => {
  
  return (
    <Select
      id={id}
      labelText={labelText}
    >
      <SelectItem text="True" value="True" />
      <SelectItem text="False" value="False" />
    </Select>
  )
  
}