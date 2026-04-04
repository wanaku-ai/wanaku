import React from "react"
import {
  Button,
  Form,
  Stack,
  TextArea,
  Tile
} from "@carbon/react"
import {Bot, Function_2, InformationSquare, Reply} from "@carbon/icons-react"
import hljs from "highlight.js/lib/core"
import json from "highlight.js/lib/languages/json"
import yaml from "highlight.js/lib/languages/yaml"
import xml from "highlight.js/lib/languages/xml"
import ReactMarkdown from "react-markdown"
import {MessageResponse} from "./messages.ts";

hljs.registerLanguage("json", json);
hljs.registerLanguage("yaml", yaml);
hljs.registerLanguage("xml", xml);


const MessageItem = ({ message }) => {
  let Icon
  let title
  
  switch (message.role) {
    case "assistant":
      Icon = Bot
      title = "Assistant"
      break
    case "tool-request":
      Icon = Function_2
      title = "Tool Request"
      break
    case "tool-response":
      Icon = Reply
      title = "Tool Response"
      break
    default:
      Icon = InformationSquare
      title = message.role
  }
  
  return (
    <div style={{ marginBottom: "2rem" }}>
      <div style={{
          display: "flex",
          alignItems: "center",
          gap: "0.5rem",
          fontWeight: "bold",
        }}
      >
        <Icon />
        {title}
      </div>
      <div className="message">
        {(message.role === "tool-response" && (
          <p dangerouslySetInnerHTML={{
              __html: hljs.highlightAuto(message.content).value
            }}
          />
        )) || <MarkdownRenderer content={ message.content } />
        }
      </div>
    </div>
  )
}


const MarkdownRenderer = ({ content }) => (
  <ReactMarkdown
    components={{
      code({ className, children, ...props }) {
        return (
          <code className={className} {...props}>
            {hljs.highlightAuto(String(children)).value}
          </code>
        )
      }
    }}
  >
    {content}
  </ReactMarkdown>
)


interface LLMChatAreaProps {
  systemMessage: string
  onSystemMessageChange: (message: string) => void
  prompt: string
  onPromptChange: (prompt: string) => void
  response: string
  messageHistory: MessageResponse[]
  running: boolean
  onSend: () => void
  onStop: () => void
}


export const LLMChatArea: React.FC<LLMChatAreaProps> = ({
    systemMessage,
    onSystemMessageChange,
    prompt,
    onPromptChange,
    response,
    messageHistory,
    running,
    onSend,
    onStop
  }) => {
  
  return (
    <Tile style={{ marginBottom: "1rem", padding: "1rem" }}>
      <Form>
        <Stack gap={7}>
          <TextArea
            id="system-input"
            labelText="System message"
            placeholder="Type system message here..."
            value={systemMessage}
            onChange={(event) => onSystemMessageChange(event.target.value)}
            rows={4}
          />
          <TextArea
            id="prompt-input"
            labelText="Enter Prompt"
            placeholder="Type your prompt here..."
            value={prompt}
            onChange={(event) => onPromptChange(event.target.value)}
            rows={4}
          />
          <Tile>
            <Button
              onClick={onSend}
              disabled={running}
            >
              Run
            </Button>
            <Button
              onClick={onStop}
              disabled={!running}
            >
              Stop
            </Button>
          </Tile>
        </Stack>
      </Form>
      <Tile style={{ padding: "1rem" }}>
        {response}
        {messageHistory.map((message) => (
          <MessageItem
            key={message.id + message.role}
            message={message}
          />
        ))}
      </Tile>
    </Tile>
  )
}