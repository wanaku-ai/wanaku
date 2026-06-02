import React, {useRef, useState} from "react"
import {
  Button,
  ButtonSet,
  Form,
  Stack,
  TextArea,
  Tile
} from "@carbon/react"
import {Send} from "@carbon/icons-react"
import {LlmConfig} from "./config"
import {LLMChatMessage} from "./LLMChatMessage"
import {getUrl} from "../../custom-fetch"


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
  
  const chatMessages = useRef<ChatMessage[]>([])
  
  function clear() {
    chatMessages.current = []
    setDisplayMessages([])
  }
  
  async function runPrompt() {
    
    const messages: DisplayMessage[] = [...displayMessages]
    
    function isFirstMessage() {
      return chatMessages.current.length === 0
    }
    
    function displayMessage(message: DisplayMessage) {
      messages.push(message)
      setDisplayMessages([...messages])
    }
    
    try {
      const response = await fetch(getUrl("/api/v1/chat/completions"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          llm: config.llm,
          model: config.llmModel,
          apiKey: config.apiKey,
          systemPrompt: isFirstMessage() ? systemPrompt : null,
          userPrompt: userPrompt,
          chatHistory: chatMessages.current,
          selectedTools: config.tools.map(tool => tool.name),
          chatParams: config.extraLlmParams ? JSON.parse(config.extraLlmParams) : {}
        })
      })
      if (response.ok) {
        const responseText = await response.text()
        chatMessages.current.push({ role: "user", content: userPrompt })
        chatMessages.current.push({ role: "assistant", content: responseText })
        displayMessage({ role: "assistant", content: responseText })
      } else {
        displayMessage({ role: "error", content: "Error: " + response.status })
      }
    } catch (error) {
      console.error("Error during conversation:", error)
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
            onClick={runPrompt}>
            Send
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