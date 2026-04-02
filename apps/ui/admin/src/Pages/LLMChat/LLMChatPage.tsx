import React, {useState} from "react"
import "highlight.js/styles/atom-one-dark.css"
import {Tool} from "./tools.ts"
import {LLMSetup} from "./LLMSetup.tsx"
import {LLMTools} from "./LLMTools.tsx"
import {LLMChatArea} from "./LLMChatArea.tsx"
import {MessageResponse} from "./messages.ts"
import {Column, Grid} from "@carbon/react"
import {connectedMCPClient} from "./mcp.ts"
import {LlmConfig, loadConfig} from "./config.ts"


export const LLMChatPage: React.FC = () => {
  
  const [config, setConfig] = useState<LlmConfig>(loadConfig())
  
  // LLM Chat
  const [response, setResponse] = useState("")
  const [messageHistory, setMessageHistory] = useState<MessageResponse[]>([])
  const [isRunning, setIsRunning] = useState(false)
  const [_, setIsRunningTarget] = useState(false)
  const [systemMessage, setSystemMessage] = useState("You are an assistant that can use tools.")
  const [prompt, setPrompt] = useState("")
  
  type ConversationMessage = {
    role: string
    content?: string | null
    name?: string
    tool_calls?: []
    tool_call_id?: string
  }
  
  async function runPrompt() {
    
    function transformTools(tools: Tool[]) {
      return tools.map((tool) => {
        return {
          type: "function",
          function: {
            name: tool.name,
            description: tool.description || "No description provided.",
            parameters: tool.inputSchema || {}
          }
        }
      })
    }
    
    async function chat(messages) {
      console.log({
        model: config.llmModel,
        messages,
        tools: transformTools(config.tools),
        extraLlmParams: config.extraLlmParams
      })
      return fetch(config.baseUrl + "/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: "Bearer " + config.apiKey,
        },
        body: JSON.stringify({
          model: config.llmModel,
          messages,
          tools: transformTools(config.tools),
          ...(config.extraLlmParams ? JSON.parse(config.extraLlmParams) : {})
        })
      })
    }
    
    const mcpClient = await connectedMCPClient()
    
    setResponse("") // clear previous response
    setIsRunning(true)
    
    const conversationHistory: ConversationMessage[] = [
      { role: "system", content: systemMessage },
      { role: "user", content: prompt }
    ]
    console.log(conversationHistory)
    
    const newMessageHistory: MessageResponse[] = []
    
    try {
      while (true) {
        const response = await chat(conversationHistory)
        if (!response.body) {
          break
        }
        
        const data = await response.json()
        if (data?.choices[0].message?.content) {
          const messageContent = data.choices[0].message?.content
          conversationHistory.push({
            role: data?.choices[0].message.role,
            content: messageContent
          })
          newMessageHistory.push({
            role: data?.choices[0].message.role,
            content: messageContent,
            id: data.id
          })
          setMessageHistory([...newMessageHistory])
        }
        if (data?.choices[0].finish_reason === "stop") {
          break
        }
        if (data?.choices[0].finish_reason === "tool_calls") {
          newMessageHistory.push({
            id: data.id,
            role: data?.choices[0].message.role,
            content: "tool_calls"
          })
          conversationHistory.push({
            role: data?.choices[0].message.role,
            tool_calls: data.choices[0].message.tool_calls
          })
          for (const toolCall of data.choices[0].message.tool_calls) {
            if (config.tools.map(tool => tool.name).includes(toolCall.function.name)) {
              const toolArgs = JSON.parse(toolCall.function.arguments || "{}")
              newMessageHistory.push({
                id: toolCall.id,
                role: "tool-request",
                content: toolCall.function.name + "(" + JSON.stringify(toolArgs) + ")"
              })
              const toolResult = await mcpClient.callTool({
                name: toolCall.function.name,
                arguments: toolArgs
              })
              const toolResultText = (toolResult.content as Array<{ text: string }>)[0].text
              conversationHistory.push({
                name: toolCall.function.name,
                content: toolResultText,
                role: "tool",
                tool_call_id: toolCall.id
              })
              newMessageHistory.push({
                id: toolCall.id,
                content: toolResultText,
                role: "tool-response"
              })
              setMessageHistory([...newMessageHistory])
            }
          }
        }
      }
    } catch (error) {
      console.error("Error calling OpenAI API:", error)
    } finally {
      await mcpClient.close()
    }
    
    setIsRunning(false)
    setIsRunningTarget(false)
  }
  
  return (
    <div>
      <h1 className="title">LLM Chat for testing</h1>
      <Grid fullWidth>
        <Column lg={4}>
          <LLMSetup config={config} onChange={setConfig} />
          <LLMTools
            selectedTools={config.tools}
            onSelectionChange={(tools) => {
              setConfig({ ...config, tools })
            }}
          />
        </Column>
        <Column lg={12}>
          <LLMChatArea
            systemMessage={systemMessage}
            onSystemMessageChange={setSystemMessage}
            prompt={prompt}
            onPromptChange={setPrompt}
            response={response}
            messageHistory={messageHistory}
            running={isRunning}
            onSend={() => {
              setMessageHistory([])
              runPrompt()
            }}
            onStop={() => {
              setIsRunningTarget(false)
            }}
          />
        </Column>
      </Grid>
    </div>
  )
}