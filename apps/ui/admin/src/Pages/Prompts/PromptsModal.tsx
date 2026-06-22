import React, {useState} from "react"
import {PromptReference} from "../../models"
import {InlineNotification, Modal, Stack, Tab, TabList, TabPanel, TabPanels, Tabs, TextArea, TextInput} from "@carbon/react"
import {NamespaceSelect} from "../Namespaces/NamespaceSelect"


interface PromptModalProps {
  onSubmit: (newPrompt: PromptReference) => void
  onRequestClose: () => void
}

export const PromptModal: React.FC<PromptModalProps> = ({ onSubmit, onRequestClose }) => {
  
  const [promptName, setPromptName] = useState("")
  const [description, setDescription] = useState("")
  const [messagesJson, setMessagesJson] = useState("")
  const [argumentsJson, setArgumentsJson] = useState("")
  const [toolReferences, setToolReferences] = useState("")
  const [selectedNamespace, setSelectedNamespace] = useState("")
  const [jsonError, setJsonError] = useState<string | null>(null)

  const handleSubmit = () => {
    setJsonError(null)
    try {
      const messages = messagesJson ? JSON.parse(messagesJson) : []
      const args = argumentsJson ? JSON.parse(argumentsJson) : []
      const toolRefs = toolReferences ? toolReferences.split(',').map(t => t.trim()) : []
      
      onSubmit({
        name: promptName,
        description,
        messages,
        arguments: args,
        toolReferences: toolRefs,
        namespace: selectedNamespace
      })
    } catch {
      setJsonError("Invalid JSON in messages or arguments fields")
    }
  }
  
  return (
    <Modal
      open={true}
      modalHeading="Add a Prompt"
      primaryButtonText="Add"
      secondaryButtonText="Cancel"
      onRequestSubmit={handleSubmit}
      onRequestClose={onRequestClose}
    >
      {jsonError && (
        <InlineNotification
          kind="error"
          title="Invalid JSON"
          subtitle={jsonError}
          onCloseButtonClick={() => setJsonError(null)}
        />
      )}
      <Tabs>
        <TabList>
          <Tab>Overview</Tab>
          <Tab>Arguments</Tab>
          <Tab>Tools</Tab>
        </TabList>
        <div id="prompts-tab-panel-wrapper">
          <TabPanels>
            <TabPanel id="prompts-tab-panel">
              <Stack gap={5}>
                <TextInput
                  id="prompt-name"
                  labelText="Prompt Name"
                  placeholder="e.g. code-reviewer"
                  value={promptName}
                  onChange={(event) => setPromptName(event.target.value)}
                />
                <TextInput
                  id="prompt-description"
                  labelText="Description"
                  placeholder="e.g. A prompt for reviewing code"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                />
                <TextArea
                  id="messages-json"
                  labelText="Messages (JSON)"
                  placeholder='Examples:
        Text: {"role": "user", "content": {"type": "text", "text": "Review {{code}}"}}
        Image: {"role": "user", "content": {"type": "image", "data": "base64...", "mimeType": "image/png"}}
        Audio: {"role": "user", "content": {"type": "audio", "data": "base64...", "mimeType": "audio/wav"}}
        Resource: {"role": "user", "content": {"type": "resource", "resource": {"location": "file:///path", "description": "File content", "mimeType": "text/plain"}}}'
                  rows={6}
                  value={messagesJson}
                  onChange={(event) => setMessagesJson(event.target.value)}
                />
                <NamespaceSelect
                  id="namespace"
                  labelText="Select a Namespace"
                  value={selectedNamespace}
                  onChange={namespace => setSelectedNamespace(namespace.id!)}
                />
              </Stack>
            </TabPanel>
            <TabPanel>
              <TextArea
                id="arguments-json"
                labelText="Arguments (JSON, optional)"
                placeholder='e.g. [{"name": "code", "description": "The code to review", "required": true}]'
                rows={16}
                value={argumentsJson}
                onChange={(event) => setArgumentsJson(event.target.value)}
              />
            </TabPanel>
            <TabPanel>
              <TextInput
                id="tool-references"
                labelText="Tool References (comma-separated, optional)"
                placeholder="e.g. code-analyzer, linter"
                value={toolReferences}
                onChange={(event) => setToolReferences(event.target.value)}
              />
            </TabPanel>
          </TabPanels>
        </div>
      </Tabs>
    </Modal>
  )
}