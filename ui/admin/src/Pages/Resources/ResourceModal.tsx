import {ComboBox, Modal, Tab, TabList, TabPanel, TabPanels, Tabs, TextInput} from "@carbon/react"
import React, { useState} from "react"
import {ResourceEntry} from "../../models"
import {commonMimeTypes, commonMimeTypesMapping} from "../../constants/mimeTypes"


import {ParametersTable} from "./ParametersTable"
import {NamespaceSelect} from "../Namespaces/NamespaceSelect"

// Inline Param type (was removed from models)
interface Param {
  name?: string;
  value?: string;
}

interface ResourceModalProps {
  openedResource?: ResourceEntry
  onSubmit: (resource: ResourceEntry) => void
  onCancel: () => void
  onError?: (message: string) => void
}

export const ResourceModal: React.FC<ResourceModalProps> = ({ openedResource, onSubmit, onCancel }) => {

  const [name, setName] = useState(openedResource?.name ?? "")
  const [description, setDescription] = useState(openedResource?.description ?? "")
  const [location, setLocation] = useState(openedResource?.location ?? "")
  const [type, setType] = useState(openedResource?.type ?? "file")
  const [mimeType, setMimeType] = useState<string>(openedResource?.mimeType ?? "")
  const [namespace, setNamespace] = useState(openedResource?.namespace)
  const [params, setParams] = useState<Param[]>([])
  const [submitDisable, setSubmitDisabled] = useState(false)
  function handleSubmit() {
    onSubmit({
      id: openedResource?.id,
      name,
      description,
      location,
      type,
      mimeType,
      namespace,
      // params removed from API schema
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
              <TextInput
                id="resource-type"
                labelText="Type"
                placeholder="e.g. file"
                value={type}
                onChange={(event) => setType(event.target.value)}
              />
              <ComboBox
                id="resource-mime-type"
                titleText="MIME Type"
                placeholder="Select or enter MIME type"
                helperText="Content type of the resource (optional)"
                items={commonMimeTypes}
                selectedItem={mimeType}
                onChange={(event) => {
                  const value = event.selectedItem ?? ""
                  setMimeType(value)
                }}
              />
              <NamespaceSelect
                id="resource-namespace"
                labelText="Select a Namespace"
                helperText="Choose a Namespace from the list"
                value={namespace ?? undefined}
                onChange={namespace => setNamespace(namespace.name)}
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
          </TabPanels>
        </div>
      </Tabs>
    </Modal>
  )
}
