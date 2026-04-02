import {Client} from "@modelcontextprotocol/sdk/client/index.js"
import {SSEClientTransport} from "@modelcontextprotocol/sdk/client/sse.js"
import {Tool} from "./tools.ts";
import {getUrl} from "../../custom-fetch.ts"


export async function connectedMCPClient() {
  const mcpClient = new Client(
    { name: "wanaku-test-client", version: "0.0.2" },
    { capabilities: {} }
  )
  const url = new URL(getUrl("/public/mcp/sse"))
  await mcpClient.connect(new SSEClientTransport(url))
  return mcpClient
}

export async function getTools(): Promise<Tool[]> {
  const mcpClient = await connectedMCPClient()
  try {
    const { tools } = await mcpClient.listTools()
    return [...tools]
  } finally {
    await mcpClient.close()
  }
}