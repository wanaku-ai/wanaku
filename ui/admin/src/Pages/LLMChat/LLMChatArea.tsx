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
import {LLMChatMessage} from "./LLMChatMessage"
import {getUrl} from "../../custom-fetch"
import {ToolReference} from "../../models"
import {getErrorMessage} from "../../utils/error"


interface ChatMessage {
  role: "user" | "assistant" | "error"
  content: string
}

export interface LlmChatConfig {
  llm: string
  model?: string
  apiKey?: string
  selectedTools: ToolReference[]
  extraLlmParams?: string
  systemPrompt?: string
}

interface LLMChatAreaProps {
  config: LlmChatConfig
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
  
  function filteredChatHistory() {
    return chatHistory.current.filter(message => message.role === "user" || message.role === "assistant")
  }
  
  async function runPrompt(signal: AbortSignal) {
    try {
      const userMessage = { role: "user", content: userPrompt } as const
      setDisplayedMessages([...chatHistory.current, userMessage])
      setIsRunning(true)
      
      const response = await fetch(getUrl("/api/v1/chat/completions"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          llm: config.llm,
          model: config.model,
          apiKey: config.apiKey,
          systemPrompt: config.systemPrompt,
          userPrompt: userPrompt,
          chatHistory: filteredChatHistory(),
          selectedTools: config.selectedTools.map(tool => tool.name),
          extraLlmParams: config.extraLlmParams ? JSON.parse(config.extraLlmParams) : {}
        })
      })
      if (signal.aborted) {
        // The chat has been aborted, do not display any response and end immediately
        return
      }
      if (response.ok) {
        const responseText = await response.text()
        const aiMessage = { role: "assistant", content: responseText } as const
        chatHistory.current.push(userMessage)
        chatHistory.current.push(aiMessage)
        setDisplayedMessages(chatHistory.current)
      } else {
        const errorMessage = { role: "error", content: `Error: ${response.status} ${response.statusText}` } as const
        chatHistory.current.push(errorMessage)
        setDisplayedMessages(chatHistory.current)
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
          {displayedMessages.map((message, index) => (
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