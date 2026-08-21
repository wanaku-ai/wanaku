import {ToolEntry} from "../../models"
import {InputSchema} from "../../models/inputSchema"


export interface SelectedTool {
  type: "function"
  function: {
    name: string
    description: string
    parameters: InputSchema
  }
}

function selectedToolJson(selectedTool: ToolEntry): SelectedTool {
  return {
    type: "function",
    function: {
      name: selectedTool.name,
      description: selectedTool.description,
      parameters: selectedTool.inputSchema as InputSchema
    }
  }
}

export function selectedToolsJson(selectedTools: ToolEntry[]): SelectedTool[] {
  return selectedTools.map(selectedTool => selectedToolJson(selectedTool))
}