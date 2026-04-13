import React, {useRef, useState} from "react"
import {
  Button,
  ButtonSet,
  Form,
  Stack,
  TextArea,
  Tile
} from "@carbon/react"
import {Send, Stop} from "@carbon/icons-react"
import {LlmConfig} from "./config"
import {McpClient} from "./mcp"
import {LLMChatMessage} from "./LLMChatMessage"


interface ChatMessage {
  id?: string
  role: string
  content?: string
  name?: string
  tool_calls?: []
  tool_call_id?: string
}

interface DisplayMessage {
  role: string
  content: string
}

interface LLMChatAreaProps {
  config: LlmConfig
}

export const LLMChatArea: React.FC<LLMChatAreaProps> = ({ config }) => {
  
  const [systemPrompt, setSystemPrompt] = useState("You are an assistant that can use tools.")
  const [userPrompt, setUserPrompt] = useState("")
  const [displayMessages, setDisplayMessages] = useState<DisplayMessage[]>([])
  const [isRunning, setIsRunning] = useState(false)
  
  const stopRunning = useRef(false)
  const chatMessages = useRef<ChatMessage[]>([])
  
  function clear() {
    chatMessages.current = []
    setDisplayMessages([])
  }
  
  async function runPrompt() {
    
    const messages: DisplayMessage[] = [...displayMessages]
    
    const tools = config.tools.map(tool => {
      return {
        type: "function",
        function: {
          name: tool.name,
          description: tool.description || "No description provided.",
          parameters: tool.inputSchema || {}
        }
      }
    })
    
    async function chat(): Promise<Response> {
      return fetch(window.location.origin + "/api/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          baseUrl: config.baseUrl,
          apiKey: config.apiKey,
          chatParams: {
            model: config.llmModel,
            messages: chatMessages.current,
            tools,
            ...(config.extraLlmParams ? JSON.parse(config.extraLlmParams) : {})
          }
        })
      })
    }
    
    function isFirstMessage() {
      return chatMessages.current.length === 0
    }
    
    function isToolAvailable(tool: string): boolean {
      return config.tools.map(tool => tool.name).includes(tool)
    }
    
    function displayMessage(message: DisplayMessage) {
      messages.push(message)
      setDisplayMessages([...messages])
    }
    
    let mcpClient = new McpClient()
    
    if (isFirstMessage()) {
      chatMessages.current.push({ role: "system", content: systemPrompt })
      displayMessage({ role: "system", content: systemPrompt })
    }
    chatMessages.current.push({ role: "user", content: userPrompt })
    displayMessage({ role: "user", content: userPrompt })
    
    setIsRunning(true)
    
    try {
      while (!stopRunning.current) {
        const response = await chat()
        if (response.status != 200) {
          displayMessage({ role: "error", content: "Error: " + response.status})
          break
        }
        
        const data = await response.json()
        if (data?.choices[0]?.message?.content) {
          const messageContent = data.choices[0].message.content
          chatMessages.current.push({
            role: data.choices[0].message.role,
            content: messageContent
          })
          displayMessage({
            role: data.choices[0].message.role,
            content: messageContent
          })
        }
        if (data?.choices[0]?.finish_reason === "stop") {
          console.log("Stopping conversation from server side")
          break
        }
        if (data?.choices[0]?.finish_reason === "tool_calls") {
          chatMessages.current.push({
            role: data.choices[0].message.role,
            tool_calls: data.choices[0].message.tool_calls
          })
          displayMessage({
            role: data.choices[0].message.role,
            content: "tool_calls"
          })
          for (const toolCall of data.choices[0].message.tool_calls) {
            if (isToolAvailable(toolCall.function.name)) {
              const toolArgs = JSON.parse(toolCall.function.arguments || "{}")
              displayMessage({
                role: "tool-request",
                content: toolCall.function.name + "(" + JSON.stringify(toolArgs) + ")"
              })
              const toolResult = await mcpClient.callTool(
                toolCall.function.name,
                toolArgs
              )
              const toolResultText = (toolResult.content as Array<{ text: string }>)[0].text
              chatMessages.current.push({
                name: toolCall.function.name,
                content: toolResultText,
                role: "tool",
                tool_call_id: toolCall.id
              })
              displayMessage({
                content: toolResultText,
                role: "tool-response"
              })
            }
          }
        }
      }
    } catch (error) {
      console.error("Error during conversation:", error)
    } finally {
      if (mcpClient) {
        await mcpClient.close()
      }
      stopRunning.current = false
      setIsRunning(false)
    }
  }
  
  return (
    <Tile style={{ marginBottom: "1rem", padding: "1rem" }}>
      <Form>
        <ButtonSet>
          <Button
            kind="ghost"
            size="lg"
            renderIcon={Send}
            iconDescription="Send"
            disabled={isRunning}
            onClick={runPrompt}>
            Send
          </Button>
          <Button
            kind="ghost"
            size="lg"
            renderIcon={Stop}
            iconDescription="Stop"
            disabled={!isRunning}
            onClick={() => {
              stopRunning.current = true
            }}>
            Stop
          </Button>
          <Button
            kind="ghost"
            size="lg"
            iconDescription="Clear chat"
            disabled={displayMessages.length == 0}
            onClick={clear}>
            Clear
          </Button>
        </ButtonSet>
        <Stack gap={7}>
          <TextArea
            id="system-input"
            labelText="System message"
            placeholder="Type system message here..."
            value={systemPrompt}
            onChange={(event) => {
              setSystemPrompt(event.target.value)
            }}
            rows={4}
          />
          <TextArea
            id="prompt-input"
            labelText="Enter Prompt"
            placeholder="Type your prompt here..."
            value={userPrompt}
            onChange={(event) => {
              setUserPrompt(event.target.value)
            }}
            rows={4}
          />
        </Stack>
        <Stack>
          {displayMessages.map((message, index) => (
            <LLMChatMessage
              key={index}
              message={message}
            />
          ))}
        </Stack>
      </Form>
    </Tile>
  )
}