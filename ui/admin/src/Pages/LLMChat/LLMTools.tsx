import {
  Checkbox,
  CheckboxGroup,
  InlineLoading,
  Stack
} from "@carbon/react"
import React, {useEffect, useState} from "react"
import {useNamespaces} from "../../hooks/api/use-namespaces"
import {useTools} from "../../hooks/api/use-tools"
import {getErrorMessage} from "../../utils/error"
import {NamespaceEntry, ToolEntry} from "../../models"
import {NamespaceSelect} from "../Namespaces/NamespaceSelect"


interface LLMToolsProps {
  selectedNamespace: NamespaceEntry
  selectedTools: ToolEntry[]
  onSelectionChange: (namespace: NamespaceEntry, tools: ToolEntry[]) => void
  onError?: (message: string) => void
}

export const LLMTools: React.FC<LLMToolsProps> = ({
    selectedNamespace, selectedTools, onSelectionChange, onError }) => {
  
  const [tools, setTools] = useState<ToolEntry[]>([])
  
  // namespace used for filtering tools
  const [namespace, setNamespace] = useState<NamespaceEntry>(selectedNamespace)
  
  const [isLoading, setLoading] = useState(true)
  const { listTools } = useTools()
  const { listNamespaces } = useNamespaces()
  
  
  useEffect(() => {
    (async () => {
      try {
        const tools = await fetchTools()
        setTools(tools)
        await checkSelectedNamespace()
      } catch (error) {
        onError?.(getErrorMessage(error))
        setTools([])
      } finally {
        setLoading(false)
      }
    })()
  }, [listTools, listNamespaces, selectedNamespace])

  async function fetchTools(): Promise<ToolEntry[]> {
    const response = await listTools()
    if (response.status !== 200 || !Array.isArray(response.data)) {
      throw new Error("Error while fetching tools: " + response.status)
    }
    return response.data
  }
  
  async function fetchNamespaces(): Promise<NamespaceEntry[]> {
    const response = await listNamespaces()
    if (response.status !== 200 || !Array.isArray(response.data)) {
      throw new Error("Error while fetching namespaces: " + response.status)
    }
    return response.data
  }
  
  /* Check that selectedNamespace exists. Reset it to "default" if it doesn't */
  async function checkSelectedNamespace() {
    console.log("Checking selected namespace")
    const namespaces: NamespaceEntry[] = await fetchNamespaces()
    if (!namespaces.find(namespace => namespace.name === selectedNamespace.name)) {
      console.log("Selected namespace not found, resetting to default")
      setNamespace({ name: "default" })
    }
  }

  function filteredTools(): ToolEntry[] {
    if (!namespace) {
      return tools
    }
    if (namespace.name === "default") {
      return tools.filter(tool => !tool.namespace || tool.namespace === "default")
    }
    const nsKey = namespace.name
    return tools.filter(tool => tool.namespace === nsKey).sort((a, b) => a.name!.localeCompare(b.name!))
  }

  function isAllSelected() {
    const selectedToolNames = selectedTools.map(tool => tool.name)
    return selectedTools.length > 0 && filteredTools().every((tool) => selectedToolNames.includes(tool.name))
  }

  function isSomeSelected() {
    return selectedTools.length > 0 && selectedTools.length < filteredTools().length
  }

  return (
    <Stack gap={5}>
      {isLoading &&
        <InlineLoading description="Loading tools..." />
      }
      {!isLoading &&
        <NamespaceSelect
          id="namespace"
          labelText="Select tools"
          value={namespace?.name}
          onChange={(namespace: NamespaceEntry) => {
            onSelectionChange(namespace, [])
          }}
        />
      }
      {!isLoading && filteredTools().length == 0 &&
        <div>No tools available</div>
      }
      {!isLoading && filteredTools().length > 0 && (
        <CheckboxGroup legendText="">
          <Checkbox
            id="select-all"
            labelText="Select All"
            checked={isAllSelected()}
            indeterminate={isSomeSelected()}
            onChange={(_, { checked }) => {
              const selection = checked ? [...tools] : []
              onSelectionChange(namespace, selection)
            }}
          />
          {filteredTools().map((tool) => (
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
                onSelectionChange(namespace, selection)
              }}
            />
          ))}
        </CheckboxGroup>
      )}
    </Stack>
  )
}
