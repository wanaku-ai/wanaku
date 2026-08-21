import {Client} from "@modelcontextprotocol/sdk/client/index.js"
import {StreamableHTTPClientTransport} from "@modelcontextprotocol/sdk/client/streamableHttp.js"
import {LlmConfig} from "./config.ts"


function getMcpServerUrl(config: LlmConfig): URL {
  // TODO replace with reading it from selected namespace
  const baseUrl = VITE_INFERENCE_URL || `${window.location.protocol}//${window.location.hostname}:8081`
  const url = new URL(`/${config.selectedNamespace.name}/mcp`, baseUrl)
  const pathname = url.pathname
  const search = url.search
  return new URL(`${baseUrl}${pathname}${search}`)
}

export async function connectMCPClient(config: LlmConfig) {
  const mcpClient = new Client(
    { name: "wanaku-test-client", version: "0.0.2" },
    { capabilities: {} }
  )
  await mcpClient.connect(new StreamableHTTPClientTransport(getMcpServerUrl(config)))
  return mcpClient
}