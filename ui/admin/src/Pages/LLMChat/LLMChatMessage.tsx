import {
  Bot,
  Error,
  Function_2,
  InformationSquare,
  Reply
} from "@carbon/icons-react"
import hljs from "highlight.js/lib/core"
import json from "highlight.js/lib/languages/json"
import yaml from "highlight.js/lib/languages/yaml"
import xml from "highlight.js/lib/languages/xml"
import ReactMarkdown from "react-markdown"


hljs.registerLanguage("json", json)
hljs.registerLanguage("yaml", yaml)
hljs.registerLanguage("xml", xml)


export const LLMChatMessage = ({ message }) => {
  let Icon
  let title
  
  switch (message.role) {
    case "system":
      Icon = InformationSquare
      title = "System"
      break
    case "user":
      Icon = InformationSquare
      title = "User"
      break
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
    case "error":
      Icon = Error
      title = "Error"
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
        fontWeight: "bold"
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