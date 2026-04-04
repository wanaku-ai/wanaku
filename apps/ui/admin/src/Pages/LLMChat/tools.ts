export interface Tool {
  name: string
  description?: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  inputSchema?: any
}