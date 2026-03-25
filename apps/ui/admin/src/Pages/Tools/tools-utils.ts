import {InputSchema} from "../../models";

export function formatInputSchema(inputSchema?: InputSchema): string {
  return (inputSchema) ? (JSON.stringify(inputSchema, null, 1)) : ""
}

export function parseInputSchema(inputSchema: string): InputSchema | undefined {
    return inputSchema === "" ? undefined : JSON.parse(inputSchema)
}