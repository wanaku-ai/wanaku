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
import {LLMChatMessage} from "./LLMChatMessage"
import {getInferenceUrl} from "../../custom-fetch"
import {getErrorMessage} from "../../utils/error"
import {selectedToolsJson} from "./utils"
import {connectMCPClient} from "./mcp"


interface ChatMessage {
  role: "system" | "user" | "assistant" | "error" | "tool"
  content: string | null
  name?: string
  tool_call_id?: number
  tool_calls?: any[]
}

interface LLMChatAreaProps {
  config: LlmConfig
  onSystemPromptChange: (systemPrompt: string) => void
}

export const LLMChatArea: React.FC<LLMChatAreaProps> = ({ config, onSystemPromptChange }) => {
  
  const [userPrompt, setUserPrompt] = useState("")
  const [displayedMessages, setDisplayedMessages] = useState<ChatMessage[]>([])
  const [isRunning, setIsRunning] = useState(false)
  
  const chatHistory = useRef<ChatMessage[]>([])
  const abortController = useRef(new AbortController())
  
  function clear() {
    chatHistory.current = []
    setDisplayedMessages([])
  }
  
  function filteredChatHistory(): ChatMessage[] {
    return chatHistory.current.filter(message =>
      message.role === "user"
      || message.role === "assistant"
      || message.role === "tool")
  }
  
  async function runPrompt(signal: AbortSignal) {
    try {
      chatHistory.current.push({ role: "user", content: userPrompt })
      setDisplayedMessages(chatHistory.current)
      setIsRunning(true)
      
      async function send(): Promise<Response> {
        const extraLlmParams = config.extraLlmParams ? JSON.parse(config.extraLlmParams) : {}
        return await fetch(getInferenceUrl("/v1/chat/completions"), {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(config.apiKey ? {Authorization: `Bearer ${config.apiKey}`} : {})
          },
          body: JSON.stringify({
            model: config.selectedModel,
            messages: createMessages(),
            ...extraLlmParams,
            tools: selectedToolsJson(config.selectedTools),
          })
        })
      }
      
      function createMessages(): ChatMessage[] {
        return [
          ...(config.systemPrompt ? [{ role: "system", content: config.systemPrompt } as ChatMessage] : []),
          ...filteredChatHistory()
        ]
      }
      
      while (true) {
        if (signal.aborted) {
          break
        }
        const response = await send()
        if (response.ok) {
          const data = await response.json()
          
          if (data?.choices[0].message?.content) {
            const responseText = data?.choices?.[0]?.message?.content ?? ""
            chatHistory.current.push({ role: "assistant", content: responseText })
            setDisplayedMessages(chatHistory.current)
            break
          }
          
          if (data?.choices[0].finish_reason === "stop") {
            break
          }
          
          if (data?.choices[0].finish_reason === "tool_calls") {
            chatHistory.current.push({
              role: "assistant",
              content: null,
              tool_calls: data.choices[0].message.tool_calls
            })
            
            const mcpClient = await connectMCPClient(config)
            try {
              for (const toolCall of data.choices[0].message.tool_calls) {
                const toolName = toolCall.function.name
                const toolArgs = JSON.parse(toolCall.function.arguments || "{}")
                
                const toolResult = await mcpClient!.callTool({
                  name: toolName,
                  arguments: toolArgs
                })
                const toolResultText = (toolResult.content as Array<{ text: string }>)[0].text
                chatHistory.current.push({
                  role: "tool",
                  name: toolName,
                  tool_call_id: toolCall.id,
                  content: toolResultText,
                })
              }
              setDisplayedMessages(chatHistory.current)
            } finally {
              await mcpClient.close()
            }
          }
        } else {
          let errorText = `${response.status} ${response.statusText}`
          try {
            const data = await response.json()
            errorText = data?.error?.message ?? errorText
          } catch {
            // response body was not JSON, fall back to status text
          }
          const errorMessage = {role: "error", content: `Error: ${errorText}`} as const
          chatHistory.current.push(errorMessage)
          setDisplayedMessages(chatHistory.current)
          break
        }
      }
    } catch (error) {
      if (!signal.aborted) {
        const networkError = { role: "error", content: `Network error: ${getErrorMessage(error)}` } as const
        chatHistory.current.push(networkError)
        setDisplayedMessages([...chatHistory.current])
      }
    } finally {
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
            onClick={() => {
              runPrompt(abortController.current.signal)
            }}>
            Send
          </Button>
          <Button
            kind="ghost"
            size="lg"
            renderIcon={Stop}
            iconDescription="Stop"
            disabled={!isRunning}
            onClick={() => {
              abortController.current.abort()
              abortController.current = new AbortController()
              setIsRunning(false)
            }}>
            Stop
          </Button>
          <Button
            kind="ghost"
            size="lg"
            iconDescription="Clear chat"
            disabled={displayedMessages.length == 0}
            onClick={clear}>
            Clear
          </Button>
        </ButtonSet>
        <Stack gap={7}>
          <TextArea
            id="system-input"
            labelText="System message"
            placeholder="Type system message here..."
            value={config.systemPrompt}
            onChange={(event) => {
              const systemPrompt = event.target.value
              onSystemPromptChange(systemPrompt)
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
          {displayedMessages.map((message, index) => {
            const displayMessage = { role: message.role as string, content: message.content }
            if (message.role === "tool") {
              displayMessage.role = "tool-response"
            }
            else if (message.role === "assistant" && message.tool_calls) {
              displayMessage.role = "tool-request"
              for (const toolCall of message.tool_calls) {
                displayMessage.content = `${toolCall.function.name}\n`
                displayMessage.content += `${toolCall.function.arguments}\n`
              }
            }
            return (
              <LLMChatMessage
                key={index}
                message={displayMessage}
              />
            )
          })}
        </Stack>
      </Form>
    </Tile>
  )
}