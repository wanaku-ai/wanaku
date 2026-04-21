import {Select, SelectItem, SelectSkeleton} from "@carbon/react";
import React, {useEffect, useState} from "react";
import {ServiceTarget} from "../../models";

interface TargetTypeSelectProps {
  value: string;
  onChange: (value: string) => void;
  apiCall: () => Promise<any>;
}

export const TargetTypeSelect: React.FC<TargetTypeSelectProps> = ({
  value,
  onChange,
  apiCall
}) => {
  
  const [targetTypes, setTargetTypes] = useState<ServiceTarget[]>([])
  const [isLoading, setLoading] = useState(true)

  useEffect(() => {
    (async () => {
      try {
        const result = await apiCall()
        if (result.status === 200) {
          const targetTypes: ServiceTarget[] = result.data.data
          setTargetTypes(targetTypes)
        } else {
          console.error(`Error fetching target types: ${result.status}`)
          setTargetTypes([])
        }
      } finally {
        setLoading(false)
      }
    })()
  }, []);

  if (isLoading) {
    return <SelectSkeleton />
  } else {
    return (
      <Select
        id="target-type"
        labelText="Type"
        defaultValue="file"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        {targetTypes.map((targetType) => (
          <SelectItem
            key={targetType.id}
            value={targetType.serviceName}
            text={targetType.serviceName || ""}
          />
        ))}
      </Select>
    )
  }
};