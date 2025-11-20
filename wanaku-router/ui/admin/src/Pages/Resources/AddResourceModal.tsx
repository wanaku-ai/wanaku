import {
  Modal,
  TextInput,
  Select,
  SelectItem, ComboBox,
} from "@carbon/react";
import React, { useEffect, useState } from "react";
import { Namespace, ResourceReference } from "../../models";
import { commonMimeTypes, commonMimeTypesMapping } from "../../constants/mimeTypes.ts";
import { listNamespaces } from "../../hooks/api/use-namespaces";
import { useCapabilities } from "../../hooks/api/use-capabilities";
import { TargetTypeSelect } from "../Targets/TargetTypeSelect";

interface AddResourceModalProps {
  onRequestClose: () => void;
  onSubmit: (newResource: ResourceReference) => void;
}

export const AddResourceModal: React.FC<AddResourceModalProps> = ({
  onRequestClose,
  onSubmit,
}) => {
  const [resourceName, setResourceName] = useState("");
  const [description, setDescription] = useState("");
  const [location, setLocation] = useState("");
  const [resourceType, setResourceType] = useState("file");
  const [mimeType, setMimeType] = useState("");
  const [fetchedData, setFetchedData] = useState<Namespace[]>([]);
  const [selectedNamespace, setSelectedNamespace] = useState('');
  const { listManagementResources } = useCapabilities();
  
  useEffect(() => {
    listNamespaces().then((result) => {
      setFetchedData(result.data.data as Namespace[]);
    });
  }, [listNamespaces]);

  const handleSelectionChange = (event) => {
    setSelectedNamespace(event.target.value);
  };

  const handleSubmit = () => {
    onSubmit({
      name: resourceName,
      description,
      location,
      type: resourceType,
      mimeType,
      namespace: selectedNamespace,
    });
  };

  function autoDetectMimeType(location: string) {
    const i = location.lastIndexOf(".")
    if (i != -1) {
      const suffix = location.substring(i + 1).toLowerCase()
      const autoDetection = commonMimeTypesMapping.get(suffix)
      if (autoDetection && mimeType == "") {
        setMimeType(autoDetection)
      }
    }
  }

  return (
    <Modal
      open={true}
      modalHeading="Add a Resource"
      primaryButtonText="Add"
      secondaryButtonText="Cancel"
      onRequestClose={onRequestClose}
      onRequestSubmit={handleSubmit}
    >
      <TextInput
        id="resource-name"
        labelText="Resource Name"
        placeholder="e.g. example-resource"
        value={resourceName}
        onChange={(e) => setResourceName(e.target.value)}
      />
      <TextInput
        id="resource-description"
        labelText="Description"
        placeholder="e.g. Description of the resource"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      <TextInput
        id="resource-location"
        labelText="Location"
        placeholder="e.g. /path/to/resource"
        value={location}
        onChange={(e) => {
          const location = e.target.value
          setLocation(location)
          autoDetectMimeType(location)
        }}
      />
      <TargetTypeSelect
        value={resourceType}
        onChange={setResourceType}
        apiCall={listManagementResources}
      />
      <ComboBox
          id="resource-mime-type"
          titleText="MIME Type"
          placeholder="Select or enter MIME type"
          items={commonMimeTypes}
          selectedItem={mimeType}
          onChange={(e) => {
            let value = e.selectedItem
            if (value === undefined || value === null) {
              value = ""
            }
            setMimeType(value)
          }}
          helperText="Content type of the resource (optional)"
      />
      <Select
        id="namespace"
        labelText="Select a Namespace"
        helperText="Choose a Namespace from the list"
        value={selectedNamespace}
        onChange={handleSelectionChange}
      >
        <SelectItem text="Choose an option" value="" />
        {fetchedData.map((namespace) => (
          <SelectItem
            id={namespace.id}
            text={namespace.path || "default"}
            value={namespace.id}
          />
        ))}
      </Select>
    </Modal>
  );
};
