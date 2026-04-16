import {
  Modal,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
  TextInput
} from "@carbon/react";
import React, {useState} from "react";
import {ToolReference} from "../../models";
import {TargetTypeSelect} from "../Targets/TargetTypeSelect";
import {useCapabilities} from "../../hooks/api/use-capabilities";
import {formatInputSchema, parseInputSchema} from "./tools-utils.ts";
import {NamespaceSelect} from "../Namespaces/NamespaceSelect.tsx";


interface ToolModalProps {
  tool?: ToolReference
  onSubmit: (tool: ToolReference) => void
  onRequestClose: () => void
}

export const ToolModal: React.FC<ToolModalProps> = ({
  tool,
  onSubmit,
  onRequestClose
}) => {
  const [toolName, setToolName] = useState(tool?.name || "")
  const [description, setDescription] = useState(tool?.description || "")
  const [uri, setUri] = useState(tool?.uri || "")
  const [toolType, setToolType] = useState(tool?.type || "http")
  const [inputSchema, setInputSchema] = useState(formatInputSchema(tool?.inputSchema))
  const [selectedNamespace, setSelectedNamespace] = useState(tool?.namespace)
  const [configurationURI, setConfigurationURI] = useState(tool?.configurationURI || "")
  const [secretsURI, setSecretsURI] = useState(tool?.secretsURI || "")
  const { listManagementTools } = useCapabilities()

  const handleSubmit = () => {
    try {
      const schema = parseInputSchema(inputSchema)
      onSubmit({
        id: tool?.id,
        name: toolName,
        description,
        uri,
        type: toolType,
        inputSchema: schema,
        namespace: selectedNamespace,
        configurationURI,
        secretsURI
      })
    } catch (error) {
      console.error("Invalid JSON in input schema:", error)
    }
  }

  return (
    <Modal
      open={true}
      modalHeading={tool ? "Edit Tool" : "Add a Tool"}
      primaryButtonText={tool ? "Save" : "Add"}
      secondaryButtonText="Cancel"
      onRequestSubmit={handleSubmit}
      onRequestClose={onRequestClose}
    >
      <Tabs>
        <TabList>
          <Tab>Overview</Tab>
          <Tab>External</Tab>
        </TabList>
        <TabPanels>
          <TabPanel>
            <TextInput
              id="tool-name"
              labelText="Tool Name"
              placeholder="e.g. meow-facts"
              value={toolName}
              onChange={(event) => setToolName(event.target.value)}
            />
            <TextInput
              id="tool-description"
              labelText="Description"
              placeholder="e.g. Retrieve random facts about cats"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
            <TextInput
              id="tool-uri"
              labelText="URI"
              placeholder="e.g. https://meowfacts.herokuapp.com?count={count}"
              value={uri}
              onChange={(event) => setUri(event.target.value)}
            />
            <TargetTypeSelect
              value={toolType}
              onChange={setToolType}
              apiCall={listManagementTools}
            />
            <TextInput
              id="input-schema"
              labelText="Input Schema"
              placeholder='e.g. {"type": "object", "properties": {"count": {"type": "int", "description": "The count of facts to retrieve"}}, "required": ["count"]}'
              value={inputSchema}
              onChange={(event) => setInputSchema(event.target.value)}
            />
            <NamespaceSelect
              id="namespace"
              labelText="Select a Namespace"
              helperText="Choose a Namespace from the list"
              value={selectedNamespace}
              onChange={namespace => setSelectedNamespace(namespace.id)}
            />
          </TabPanel>
          <TabPanel>
            <TextInput
              id="configuration-uri"
              labelText="Configuration URI"
              placeholder="e.g., file:///config/tool-config.json"
              value={configurationURI}
              onChange={(event) => setConfigurationURI(event.target.value)}
            />
            <TextInput
              id="secrets-uri"
              labelText="Secrets URI"
              placeholder="e.g., vault://secrets/api-keys/tool-name"
              value={secretsURI}
              onChange={(event) => setSecretsURI(event.target.value)}
            />
          </TabPanel>
        </TabPanels>
      </Tabs>
    </Modal>
  )
}