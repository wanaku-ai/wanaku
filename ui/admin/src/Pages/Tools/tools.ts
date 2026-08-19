import {ToolEntry} from "../../models"


export class ToolParserError extends Error {

  constructor(message: string) {
    super(message)
  }

}

function convertToTool(json: any): ToolEntry {
  if (!json.name) {
    throw new ToolParserError("Tool must have a name")
  }
  if (typeof json.name !== "string") {
    throw new ToolParserError("Tool name must be a string")
  }
  return {
    name: json.name,
    description: json.description ?? "",
    inputSchema: json.inputSchema ?? json.input_schema ?? {},
    type: json.type ?? "http",
    uri: json.uri ?? "",
    ...json
  }
}

export class Tools {

  static stringify(tools: ToolEntry | ToolEntry[]): string {
    return JSON.stringify(tools, null, 2)
  }

  static parse(string: string): ToolEntry[] {
    const json: unknown = JSON.parse(string)
    if (Array.isArray(json)) {
      const tools: ToolEntry[] = []
      for (const object of json) {
        tools.push(convertToTool(object))
      }
      return tools
    } else {
      return [convertToTool(json)]
    }
  }
  
  static isInputSchemaInvalid(inputSchema: string): boolean {
    const invalidMessage = Tools.validateInputSchema(inputSchema)
    return !!invalidMessage
  }
  
  static validateInputSchema(inputSchema: string): string {
    if (!inputSchema) {
      return ""
    }
    try {
      JSON.parse(inputSchema)
      return ""
    } catch (error) {
      if (error instanceof SyntaxError) {
        return error.message
      }
      return "Invalid input schema"
    }
  }
  
}