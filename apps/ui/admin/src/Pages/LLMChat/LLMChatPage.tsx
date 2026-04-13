import React, {useState} from "react"
import "highlight.js/styles/atom-one-dark.css"
import {LLMSetup} from "./LLMSetup.tsx"
import {LLMTools} from "./LLMTools.tsx"
import {LLMChatArea} from "./LLMChatArea.tsx"
import {Column, Grid} from "@carbon/react"
import {LlmConfig, loadConfig} from "./config.ts"


export const LLMChatPage: React.FC = () => {
  
  const [config, setConfig] = useState<LlmConfig>(loadConfig())
  
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
          <LLMChatArea config={config} />
        </Column>
      </Grid>
    </div>
  )
}