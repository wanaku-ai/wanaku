import {ComboBox, Modal, Tab, TabList, TabPanel, TabPanels, Tabs, TextInput} from "@carbon/react"
import React, { useState} from "react"
import {Param, ResourceReference} from "../../models"
import {commonMimeTypes, commonMimeTypesMapping} from "../../constants/mimeTypes"
import {useCapabilities} from "../../hooks/api/use-capabilities"
import {TargetTypeSelect} from "../Targets/TargetTypeSelect"
import {ParametersTable} from "./ParametersTable"
import {NamespaceSelect} from "../Namespaces/NamespaceSelect"


interface ResourceModalProps {
  openedResource?: ResourceReference
  onSubmit: (resource: ResourceReference) => void
  onCancel: () => void
}

export const ResourceModal: React.FC<ResourceModalProps> = ({ openedResource, onSubmit, onCancel }) => {
  
  const [name, setName] = useState(openedResource?.name)
  const [description, setDescription] = useState(openedResource?.description)
  const [location, setLocation] = useState(openedResource?.location)
  const [type, setType] = useState(openedResource?.type || "file")
  const [mimeType, setMimeType] = useState(openedResource?.mimeType)
  const [namespace, setNamespace] = useState(openedResource?.namespace)
  const [params, setParams] = useState<Param[]>(openedResource?.params || [])
  const [configurationURI, setConfigurationURI] = useState(openedResource?.configurationURI)
  const [secretsURI, setSecretsURI] = useState(openedResource?.secretsURI)
  const [submitDisable, setSubmitDisabled] = useState(false)
  const { listManagementResources } = useCapabilities()

  function handleSubmit() {
    onSubmit({
      id: openedResource?.id,
      name,
      description,
      location,
      type,
      mimeType,
      namespace,
      params,
      configurationURI,
      secretsURI
    })
  }

  function autoDetectMimeType(location: string) {
    const i = location.lastIndexOf(".")
    if (i != -1) {
      const suffix = location.substring(i + 1).toLowerCase()
      const autoDetection = commonMimeTypesMapping.get(suffix)
      if (autoDetection && !mimeType) {
        setMimeType(autoDetection)
      }
    }
  }

  return (
    <Modal
      open={true}
      modalHeading={openedResource ? "Edit resource" : "Add a Resource"}
      primaryButtonText={openedResource ? "Save" : "Add"}
      primaryButtonDisabled={submitDisable}
      secondaryButtonText="Cancel"
      onRequestSubmit={handleSubmit}
      onRequestClose={onCancel}
    >
      <Tabs>
        <TabList>
          <Tab>Overview</Tab>
          <Tab>Parameters</Tab>
          <Tab>External</Tab>
        </TabList>
        <div id="resource-tab-panel-wrapper">
          <TabPanels>
            <TabPanel id="resource-tab-panel">
              <TextInput
                id="resource-name"
                labelText="Resource Name"
                placeholder="e.g. example-resource"
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
              <TextInput
                id="resource-description"
                labelText="Description"
                placeholder="e.g. Description of the resource"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
              <TextInput
                id="resource-location"
                labelText="Location"
                placeholder="e.g. /path/to/resource"
                value={location}
                onChange={(event) => {
                  const location = event.target.value
                  setLocation(location)
                  autoDetectMimeType(location)
                }}
              />
              <TargetTypeSelect
                value={type}
                onChange={setType}
                apiCall={listManagementResources}
              />
              <ComboBox
                id="resource-mime-type"
                titleText="MIME Type"
                placeholder="Select or enter MIME type"
                helperText="Content type of the resource (optional)"
                items={commonMimeTypes}
                selectedItem={mimeType}
                onChange={(event) => {
                  let value = event.selectedItem
                  if (value === null) {
                    value = undefined
                  }
                  setMimeType(value)
                }}
              />
              <NamespaceSelect
                id="resource-namespace"
                labelText="Select a Namespace"
                helperText="Choose a Namespace from the list"
                value={namespace}
                onChange={namespace => setNamespace(namespace.id)}
              />
            </TabPanel>
            <TabPanel>
              <ParametersTable
                parameters={params}
                onUpdate={(parameters: Param[]) => setParams(parameters)}
                onInlineEditorOpen={() => setSubmitDisabled(true)}
                onInlineEditorClose={() => setSubmitDisabled(false)}
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
        </div>
      </Tabs>
    </Modal>
  )
}