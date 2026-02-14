import {Select, SelectItem} from "@carbon/react";
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
  const [targetTypes, settargetTypes] = useState<ServiceTarget[]>([]);

  useEffect(() => {
    apiCall().then((result) => {
      settargetTypes(result.data.data as ServiceTarget[]);
    });
  }, []);

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
  );
};