import {
  Modal,
  TextInput,
  Select,
  SelectItem,
} from "@carbon/react";
import React, { useEffect, useState } from "react";
import { Namespace, ResourceReference } from "../../models";
import { listNamespaces } from "../../hooks/api/use-namespaces";
import { useTargets } from "../../hooks/api/use-targets";
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
  const [fetchedData, setFetchedData] = useState<Namespace[]>([]);
  const [selectedNamespace, setSelectedNamespace] = useState('');
  const { listManagementResources } = useTargets();
  
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
      namespace: selectedNamespace,
    });
  };

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
        onChange={(e) => setLocation(e.target.value)}
      />
      <TargetTypeSelect
        value={resourceType}
        onChange={setResourceType}
        apiCall={listManagementResources}
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
