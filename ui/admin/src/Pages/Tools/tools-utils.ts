import {InputSchema} from "../../models";

export function formatInputSchema(inputSchema?: InputSchema): string {
  return (inputSchema) ? (JSON.stringify(inputSchema, null, 1)) : ""
}

export function parseInputSchema(inputSchema: string): InputSchema {
    return inputSchema === "" ? { type: "object" } : JSON.parse(inputSchema)
}