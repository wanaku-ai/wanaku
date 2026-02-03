import {
  Modal,
  TextInput,
  Select,
  SelectItem,
  ComboBox,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs
} from "@carbon/react";
import React, { useEffect, useState } from "react";
import { Namespace, Param, ResourceReference } from "../../models";
import { commonMimeTypes, commonMimeTypesMapping } from "../../constants/mimeTypes.ts";
import { listNamespaces } from "../../hooks/api/use-namespaces";
import { useCapabilities } from "../../hooks/api/use-capabilities";
import { TargetTypeSelect } from "../Targets/TargetTypeSelect";
import { ParametersTable } from "./ParametersTable.tsx";

interface ResourceModalProps {
  resource?: ResourceReference
  onRequestClose: () => void;
  onSubmit: (resource: ResourceReference) => void;
}

export const ResourceModal: React.FC<ResourceModalProps> = ({
  resource,
  onRequestClose,
  onSubmit,
}) => {
  const [resourceName, setResourceName] = useState(resource?.name || "");
  const [description, setDescription] = useState(resource?.description || "");
  const [location, setLocation] = useState(resource?.location || "");
  const [resourceType, setResourceType] = useState(resource?.type || "file");
  const [mimeType, setMimeType] = useState(resource?.mimeType || "");
  const [fetchedData, setFetchedData] = useState<Namespace[]>([]);
  const [selectedNamespace, setSelectedNamespace] = useState(resource?.namespace || "");
  const [params, setParams] = useState<Param[]>(resource?.params || []);
  const [configurationURI, setConfigurationURI] = useState(resource?.configurationURI || "")
  const [secretsURI, setSecretsURI] = useState(resource?.secretsURI || "")
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
      id: resource?.id,
      name: resourceName,
      description,
      location,
      type: resourceType,
      mimeType,
      namespace: selectedNamespace,
      params: nonEmptyParameters(),
      configurationURI,
      secretsURI
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

  function newParameter() {
    const temp = [...params]
    temp.push({name: '', value: ''})
    setParams(temp)
  }

  function removeParameter(i: number) {
    const temp = [...params]
    temp.splice(i, 1)
    setParams(temp)
  }

  function nonEmptyParameters() {
    return params.filter(parameter => parameter.name !== '')
  }

  return (
    <Modal
      open={true}
      modalHeading={resource ? "Edit resource" : "Add a Resource"}
      primaryButtonText={resource ? "Save" : "Add"}
      secondaryButtonText="Cancel"
      onRequestClose={onRequestClose}
      onRequestSubmit={handleSubmit}
    >
      <Tabs>
        <TabList>
          <Tab>Overview</Tab>
          <Tab>Parameters</Tab>
          <Tab>External</Tab>
        </TabList>
        <TabPanels>
          <TabPanel>
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
          </TabPanel>
          <TabPanel>
            <ParametersTable
                parameters={params}
                onAdd={newParameter}
                onSetName={(i, name) => params[i].name = name}
                onSetValue={(i, value) => params[i].value = value}
                onDelete={removeParameter}
            />
          </TabPanel>
          <TabPanel>
            <TextInput
              id="resource-configuration-uri"
              labelText="Configuration URI"
              placeholder="e.g. file:///config/resource-config.json"
              value={configurationURI}
              onChange={(event) => setConfigurationURI(event.target.value)}
            />
            <TextInput
              id="resource-secrets-uri"
              labelText="Secrets URI"
              placeholder="e.g. vault://secrets/db-credentials"
              value={secretsURI}
              onChange={(event) => setSecretsURI(event.target.value)}
            />
          </TabPanel>
        </TabPanels>
      </Tabs>
    </Modal>
  );
};
