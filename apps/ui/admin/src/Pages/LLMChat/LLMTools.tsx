import {
  Checkbox,
  CheckboxGroup,
  InlineLoading,
  Stack
} from "@carbon/react"
import {Tool} from "./tools.ts"
import {useEffect, useState} from "react"
import {getTools} from "./mcp.ts"


interface LLMToolsProps {
  selectedTools: Tool[]
  onSelectionChange: (tools: Tool[]) => void
}

export const LLMTools: React.FC<LLMToolsProps> = ({ selectedTools, onSelectionChange }) => {
  
  const [tools, setTools] = useState<Tool[]>([])
  const [isLoading, setLoading] = useState<boolean>(true)
  
  useEffect(() => {
    (async () => {
      try {
        const tools: Tool[] = await getTools()
        setTools(tools)
      } catch (error) {
        console.error("Failed to load tools", error)
        setTools([])
      } finally {
        setLoading(false)
      }
    })()
  }, [])
  
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
              id={tool.name}
              key={tool.name}
              labelText={tool.name}
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