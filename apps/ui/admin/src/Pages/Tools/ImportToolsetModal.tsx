import {
  Modal,
  Stack,
  TextArea,
  TextInput
} from "@carbon/react"
import {useState} from "react"
import {ToolReference} from "../../models"


interface ImportToolsetModalProps {
  onSubmit: (tools: ToolReference[]) => void
  onCancel: () => void
}

export const ImportToolsetModal: React.FC<ImportToolsetModalProps> = ({ onSubmit, onCancel }) => {
  
  const [toolsetUrl, setToolsetUrl] = useState("")
  const [toolsetJson, setToolsetJson] = useState("")
  const [invalidJson, setInvalidJson] = useState(false)
  
  
  async function handleFetchToolset() {
    if (toolsetUrl) {
      const response = await fetch(toolsetUrl)
      if (response.ok) {
        const tools = await response.json()
        setToolsetJson(JSON.stringify(tools, null, 2))
      } else {
        console.error(`Error fetching toolset from ${toolsetUrl}`)
      }
    }
  }
  
  async function handleSubmit() {
    try {
      const tools = JSON.parse(toolsetJson)
      setInvalidJson(false)
      onSubmit(tools)
    } catch (error) {
      setInvalidJson(true)
    }
  }
  
  return (
    <Modal
      open={true}
      modalHeading="Import Toolset"
      primaryButtonText="Import"
      secondaryButtonText="Cancel"
      onRequestSubmit={handleSubmit}
      onRequestClose={onCancel}
    >
      <Stack gap={7}>
        <TextInput
          id="toolset-url"
          labelText="Fetch from Toolset URL"
          placeholder="Enter the URL of the toolset JSON"
          value={toolsetUrl}
          onChange={(event) => {
            setToolsetUrl(event.target.value)
          }}
          onBlur={handleFetchToolset}
        />
        <TextArea
          id="toolset-json"
          labelText="Toolset JSON"
          placeholder="Paste your JSON array here"
          rows={10}
          required
          value={toolsetJson}
          onChange={(event) => {
            setToolsetJson(event.target.value)
          }}
          invalid={invalidJson}
          invalidText="Invalid JSON"
        />
      </Stack>
    </Modal>
  )
}