import {
  Checkbox,
  CheckboxGroup,
  InlineLoading,
  Stack
} from "@carbon/react"
import React, {useEffect, useState} from "react"
import {useTools} from "../../hooks/api/use-tools"
import {ToolReference} from "../../models"


interface LLMToolsProps {
  selectedTools: ToolReference[]
  onSelectionChange: (tools: ToolReference[]) => void
}

export const LLMTools: React.FC<LLMToolsProps> = ({ selectedTools, onSelectionChange }) => {
  
  const [tools, setTools] = useState<ToolReference[]>([])
  const [isLoading, setLoading] = useState(true)
  const { listTools } = useTools()
  
  useEffect(() => {
    (async () => {
      try {
        const tools = await fetchTools()
        setTools(tools)
      } catch (error) {
        console.error("Failed to load tools", error)
        setTools([])
      } finally {
        setLoading(false)
      }
    })()
  }, [listTools])
  
  async function fetchTools(): Promise<ToolReference[]> {
    const response = await listTools()
    if (response.status !== 200 || !Array.isArray(response.data.data)) {
      throw new Error("Error while fetching tools: " + response.status)
    }
    return response.data.data
  }
  
  function isAllSelected() {
    const selectedToolNames = selectedTools.map(tool => tool.name)
    return tools.length > 0 && tools.every((tool) => selectedToolNames.includes(tool.name))
  }
  
  function isSomeSelected() {
    return selectedTools.length > 0 && selectedTools.length < tools.length
  }
  
  return (
    <Stack gap={7}>
      {isLoading &&
        <InlineLoading description="Loading tools..." />
      }
      {!isLoading && tools.length == 0 &&
        <div>No tools available</div>
      }
      {!isLoading && tools.length > 0 && (
        <CheckboxGroup legendText="Select tools">
          <Checkbox
            id="select-all"
            labelText="Select All"
            checked={isAllSelected()}
            indeterminate={isSomeSelected()}
            onChange={(_, { checked }) => {
              const selection = checked ? [...tools] : []
              onSelectionChange(selection)
            }}
          />
          {tools.map((tool) => (
            <Checkbox
              id={tool.name!}
              key={tool.name}
              labelText={tool.name!}
              helperText={tool.description}
              checked={selectedTools.map(tool => tool.name).includes(tool.name)}
              onChange={(_, { checked }) => {
                const selection = checked
                  ? [...selectedTools, tool]
                  : selectedTools.filter(item => item.name != tool.name)
                onSelectionChange(selection)
              }}
            />
          ))}
        </CheckboxGroup>
      )}
    </Stack>
  )
}