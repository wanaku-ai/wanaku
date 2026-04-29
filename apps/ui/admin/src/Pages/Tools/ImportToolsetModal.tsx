import {
  Column,
  ContentSwitcher,
  Grid,
  Modal,
  Stack,
  Switch,
  TextArea,
  TextInput
} from "@carbon/react"
import React, {useRef, useState} from "react"
import {ToolReference} from "../../models"
import {ToolParserError, Tools} from "./tools"
import {ImportToolsetTable} from "./ImportToolsetTable"


interface ImportToolsetModalProps {
  onSubmit: (tools: ToolReference[]) => void
  onCancel: () => void
}

export const ImportToolsetModal: React.FC<ImportToolsetModalProps> = ({ onSubmit, onCancel }) => {
  
  const FINAL_STEP = 1
  const VIEW_MODE_TABLE = "table"
  const VIEW_MODE_JSON = "json"
  
  const [toolsetUrl, setToolsetUrl] = useState<string>()
  const [invalidUrl, setInvalidUrl] = useState(false)
  const [toolset, setToolset] = useState<ToolReference[]>([])
  const [toolsetJson, setToolsetJson] = useState<string>()
  const [invalidJson, setInvalidJson] = useState(false)
  const [invalidJsonText, setInvalidJsonText] = useState<string>()
  const [step, setStep] = useState(0)
  const [contentSwitcherEnabled, setContentSwitcherEnabled] = useState(true)
  const [viewMode, setViewMode] = useState(VIEW_MODE_TABLE)
  const selectedTools = useRef<ToolReference[]>([])

  
  function setSelectedTools(tools: ToolReference[]) {
    selectedTools.current = tools
    setToolsetJson(tools.length > 0 ? Tools.stringify(tools) : undefined)
  }
  
  function primaryButtonText(): string {
    return step === FINAL_STEP ? "Import" : "Next"
  }
  
  function primaryButtonDisabled(): boolean {
    return step === FINAL_STEP && !toolsetJson
  }
  
  function isToolsetUrlEmpty(): boolean {
    return !toolsetUrl
  }
  
  function isToolsetUrlValid(): boolean {
    try {
      new URL(toolsetUrl!)
      return true
    } catch (error) {
      return false
    }
  }
  
  async function fetchToolset() {
    const response = await fetch(toolsetUrl!)
    if (response.ok) {
      const tools = await response.json()
      setToolset(tools)
      setSelectedTools(tools)
    } else {
      throw new Error(`Error fetching toolset from ${toolsetUrl}: ${response.status}`)
    }
  }
  
  async function handlePrimaryButton() {
    if (step === FINAL_STEP) {
      handleSubmit()
    } else {
      if (isToolsetUrlEmpty()) {
        // skip fetching toolset, user can copy-paste in the next step
        setStep(step + 1)
        setViewMode(VIEW_MODE_JSON)
        setContentSwitcherEnabled(false)
      } else if (isToolsetUrlValid()) {
        // try to fetch toolset and proceed to next step
        try {
          await fetchToolset()
          setStep(step + 1)
        } catch (error) {
          console.error(error)
        }
      } else {
        // URL is invalid
        setInvalidUrl(true)
      }
    }
  }
  
  function handleSubmit() {
    try {
      const tools: ToolReference[] = Tools.parse(toolsetJson!)
      onSubmit(tools)
    } catch (error) {
      if (error instanceof SyntaxError || error instanceof ToolParserError) {
        setInvalidJson(true)
        setInvalidJsonText(error.message)
      } else {
        console.error(error)
      }
    }
  }
  
  return (
    <Modal
      open={true}
      modalHeading="Import Toolset"
      primaryButtonText={primaryButtonText()}
      primaryButtonDisabled={primaryButtonDisabled()}
      secondaryButtonText="Cancel"
      onRequestSubmit={handlePrimaryButton}
      onRequestClose={onCancel}
    >
      <Stack gap={7}>
        {step === 0 && (
          <TextInput
            id="toolset-url"
            labelText="Fetch from Toolset URL"
            placeholder="Enter the URL of the toolset JSON"
            helperText="You can skip this step and enter the data manually"
            invalid={invalidUrl}
            invalidText="Invalid URL"
            onChange={(event) => {
              setToolsetUrl(event.target.value)
              setInvalidUrl(false)
            }}
          />
        )}
        {step === 1 && (
          <>
            {contentSwitcherEnabled && (
            <Grid>
              <Column></Column>
              <Column lg={{ span: 4, offset: 12 }} md={{ span: 4, offset: 4 }} sm={4}>
                <ContentSwitcher
                  onChange={({ name }) => { setViewMode(name as string) }}
                  selectedIndex={viewMode === VIEW_MODE_TABLE ? 0 : 1}
                >
                  <Switch
                    name={VIEW_MODE_TABLE}
                    text="List view"
                  />
                  <Switch
                    name={VIEW_MODE_JSON}
                    text="Json"
                  />
                </ContentSwitcher>
              </Column>
            </Grid>
            )}
            {viewMode === VIEW_MODE_TABLE && (
              <ImportToolsetTable
                tools={toolset}
                selectedTools={selectedTools.current}
                onSelectionChange={(tools: ToolReference[]) => {
                  setSelectedTools(tools)
                }}
              />
            )}
            {viewMode === VIEW_MODE_JSON && (
              <TextArea
                id="toolset-json"
                labelText="Toolset JSON"
                placeholder="Paste your JSON array here"
                rows={10}
                required
                value={toolsetJson}
                invalid={invalidJson}
                invalidText={invalidJsonText}
                onChange={(event) => {
                  setToolsetJson(event.target.value)
                }}
              />
            )}
          </>
        )}
      </Stack>
    </Modal>
  )
}