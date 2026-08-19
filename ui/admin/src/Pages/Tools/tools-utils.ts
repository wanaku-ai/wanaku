export interface InputSchema {
  type?: string;
  properties?: Record<string, Property>;
  required?: string[];
}

export interface Property {
  type?: string;
  description?: string;
}

export function formatInputSchema(inputSchema?: InputSchema): string {
  return (inputSchema) ? (JSON.stringify(inputSchema, null, 1)) : ""
}

export function parseInputSchema(inputSchema: string): InputSchema {
    return inputSchema === "" ? { type: "object" } : JSON.parse(inputSchema)
}
