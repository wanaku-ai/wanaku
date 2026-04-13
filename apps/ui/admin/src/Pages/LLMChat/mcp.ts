import {Client} from "@modelcontextprotocol/sdk/client/index.js"
import {SSEClientTransport} from "@modelcontextprotocol/sdk/client/sse.js"
import {getUrl} from "../../custom-fetch.ts"


export class McpClient {
  
  realMcpClient?: Client
  
  async callTool(name: string, args) {
    if (!this.realMcpClient) {
      this.realMcpClient = await connectedMCPClient()
    }
    return await this.realMcpClient.callTool({
      name: name,
      arguments: args
    })
  }
  
  async close() {
    return this.realMcpClient?.close()
  }
  
}

async function connectedMCPClient() {
  const mcpClient = new Client(
    { name: "wanaku-test-client", version: "0.0.2" },
    { capabilities: {} }
  )
  const url = new URL(getUrl("/public/mcp/sse"))
  await mcpClient.connect(new SSEClientTransport(url))
  return mcpClient
}