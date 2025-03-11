import React, { useCallback, useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import {
  Button,
  Tile,
  Form,
  TextArea,
  Grid,
  Column,
  Tab,
  TabPanels,
  TabPanel,
  Tabs,
  TabList,
  Stack,
  ComboBox,
  PasswordInput,
  TextInput,
  Toggle,
  FormGroup,
  Checkbox,
  Accordion,
  AccordionItem,
} from "@carbon/react";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { SSEClientTransport } from "@modelcontextprotocol/sdk/client/sse.js";
import hljs from "highlight.js/lib/core";
import "highlight.js/styles/atom-one-dark.css";
import json from "highlight.js/lib/languages/json";
import yaml from "highlight.js/lib/languages/yaml";
import xml from "highlight.js/lib/languages/xml";
import { Bot, Function_2, InformationSquare, Reply } from "@carbon/icons-react";

export const LLMChatPage: React.FC = () => {
  hljs.registerLanguage("json", json);
  hljs.registerLanguage("yaml", yaml);
  hljs.registerLanguage("xml", xml);

  const baseUrlItems = [
    "http://0.0.0.0:8000",
    "https://api.openai.com",
    "https://api.mistral.ai",
    "https://generativelanguage.googleapis.com/v1beta/openai/",
    "https://api.anthropic.com",
  ];

  const [isStoreInLocalStorage, setIsStoreInLocalStorage] = useState<boolean>(
    localStorage.getItem("isStoreInLocalStorage") === "true" || false
  );

  const [tabIndex, setTabIndex] = useState(
    parseInt(localStorage.getItem("tabIndex") || "0", 10)
  );

  useEffect(() => {
    localStorage.setItem("tabIndex", tabIndex.toString());
  }, [tabIndex]);

  const [baseUrl, setBaseUrl] = useState<string | null | undefined>(
    isStoreInLocalStorage
      ? localStorage.getItem("baseUrl") || baseUrlItems[2]
      : baseUrlItems[2]
  );
  const [llmModel, setLLMModel] = useState(
    isStoreInLocalStorage
      ? localStorage.getItem("llmModel") || "mistral-small-latest"
      : "mistral-small-latest"
  );
  const [apiKey, setApiKey] = useState(
    isStoreInLocalStorage ? localStorage.getItem("apiKey") || "" : ""
  );
  const [extraLLMParams, setExtraLLMParams] = useState(
    JSON.parse(
      isStoreInLocalStorage
        ? localStorage.getItem("extraLLMParams") ||
            '{"max_tokens": 400, "temperature": 0.7, "tool_choice": "auto"}'
        : '{"max_tokens": 400, "temperature": 0.7, "tool_choice": "auto"}'
    )
  );
  const [rawExtraLLMParams, setRawExtraLLMParams] = useState(
    JSON.stringify(extraLLMParams)
  );

  interface Tool {
    name?: string;
    description?: string;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    inputSchema?: any;
  }
  const [tools, setTools] = useState<Tool[]>([]);
  const [selectedTools, setSelectedTools] = useState<string[]>(() => {
    if (isStoreInLocalStorage) {
      const stored = localStorage.getItem("selectedTools");
      console.log(stored);
      return stored ? JSON.parse(stored) : [];
    }
    return [];
  });
  const [isLoadingTools, setIsLoadingTools] = useState<boolean>(true);

  const [systemMessage, setSystemMessage] = useState(
    "You are an assistant that can use tools."
  );
  const [prompt, setPrompt] = useState("");

  type MessageResponse = {
    id: string;
    content: string;
    role: string;
  };
  const [messageHistory, setMessageHistory] = useState<MessageResponse[]>([]);
  const [response, setResponse] = useState("");
  const [isRunning, setIsRunning] = useState(false);
  const [isRunningTarget, setIsRunningTarget] = useState(false);

  useEffect(() => {
    if (isStoreInLocalStorage) {
      localStorage.setItem("baseUrl", baseUrl || "");
    }
  }, [baseUrl, isStoreInLocalStorage]);

  useEffect(() => {
    localStorage.setItem(
      "isStoreInLocalStorage",
      String(isStoreInLocalStorage)
    );
  }, [isStoreInLocalStorage]);

  useEffect(() => {
    if (isStoreInLocalStorage) {
      localStorage.setItem("apiKey", apiKey);
    }
  }, [apiKey, isStoreInLocalStorage]);

  useEffect(() => {
    if (isStoreInLocalStorage) {
      localStorage.setItem("llmModel", llmModel || "");
    }
  }, [llmModel, isStoreInLocalStorage]);

  useEffect(() => {
    if (isStoreInLocalStorage) {
      localStorage.setItem("extraLLMParams", JSON.stringify(extraLLMParams));
    }
  }, [extraLLMParams, isStoreInLocalStorage]);

  useEffect(() => {
    if (isStoreInLocalStorage) {
      localStorage.setItem("selectedTools", JSON.stringify(selectedTools));
    }
  }, [selectedTools, isStoreInLocalStorage]);

  const transformTools = useCallback(
    (selectedToolNames: string[]) => {
      return selectedToolNames.map((toolName) => {
        const tool = tools.find((t) => t.name === toolName);
        if (!tool) return null;
        return {
          type: "function",
          function: {
            name: tool.name,
            description: tool.description || "No description provided.",
            parameters: Object.fromEntries(
              Object.entries(tool.inputSchema.properties || {}).map(
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                ([key, value]: [string, any]) => [key, value.type]
              )
            ),
          },
        };
      });
    },
    [tools]
  );

  const connectMCPClient = async () => {
    const mcpClient = new Client(
      { name: "wanaku-test-client", version: "0.0.2" },
      { capabilities: {} }
    );

    try {
      await mcpClient.connect(
        // try with /mcp/sse
        new SSEClientTransport(new URL("http://localhost:8080/mcp/sse"))
      );
      return mcpClient;
    } catch (error) {
      console.error("WebSocket connection error:", error);
      setResponse("Connection error occurred");
    }
    return null;
  };

  const getTools = useCallback(async (mcpClient?) => {
    if (!mcpClient) {
      mcpClient = await connectMCPClient();
      if (mcpClient) {
        const { tools } = await mcpClient.listTools();
        return tools;
      }
      mcpClient.close();
    } else {
      const { tools } = await mcpClient.listTools();
      return tools;
    }
  }, []);

  useEffect(() => {
    const fetchTools = async () => {
      try {
        const fetchedTools = await getTools();
        setTools([...fetchedTools]);
      } catch (error) {
        console.error("Failed to load tools", error);
      } finally {
        setIsLoadingTools(false);
      }
    };

    fetchTools();
  }, [getTools]);

  const isAllSelected =
    tools.length > 0 &&
    tools.every((tool) => selectedTools.includes(tool.name!));

  const toggleSelectAll = (_event, { checked }) => {
    setSelectedTools(checked ? tools.map((tool) => tool.name!) : []);
  };

  const toggleItem = (_event, { checked, id }) => {
    setSelectedTools((prev) =>
      checked ? [...prev, id] : prev.filter((toolName) => toolName !== id)
    );
  };

  const runPrompt = useCallback(async () => {
    const mcpClient = await connectMCPClient();
    //const tools = await getTools(mcpClient);
    if (!mcpClient) {
      return;
    }

    setResponse(""); // clear previous response

    type ConversationMessage = {
      role: string;
      content?: string | null;
      name?: string;
      tool_calls?: [];
      tool_call_id?: string;
    };

    const conversationHistory: ConversationMessage[] = [
      { role: "system", content: systemMessage },
      { role: "user", content: prompt },
    ];

    const chat = async (messages) => {
      console.log({
        model: llmModel,
        messages,
        tools: transformTools(selectedTools),
        ...extraLLMParams,
      });
      return fetch(baseUrl + "/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: "Bearer " + apiKey,
        },
        body: JSON.stringify({
          model: llmModel,
          messages,
          tools: transformTools(selectedTools),
          ...extraLLMParams,
        }),
      });
    };
    console.log(conversationHistory);

    setIsRunning(true);
    const newMessageHistory: MessageResponse[] = [];

    try {
      while (isRunningTarget) {
        const response = await chat(conversationHistory);
        if (!response.body) break;

        const data = await response.json();
        if (data?.choices[0].message?.content) {
          const messageContent = data.choices[0].message?.content;
          conversationHistory.push({
            role: data?.choices[0].message.role,
            content: messageContent,
          });
          newMessageHistory.push({
            role: data?.choices[0].message.role,
            content: messageContent,
            id: data.id,
          });
          setMessageHistory([...newMessageHistory]);
        }
        if (data?.choices[0].finish_reason === "stop") {
          break;
        }
        if (data?.choices[0].finish_reason === "tool_calls") {
          newMessageHistory.push({
            role: data?.choices[0].message.role,
            content: "tool_calls",
            id: data.id,
          });
          conversationHistory.push({
            role: data?.choices[0].message.role,
            tool_calls: data.choices[0].message.tool_calls,
          });
          for (const toolCall of data.choices[0].message.tool_calls) {
            if (selectedTools.includes(toolCall.function.name)) {
              const toolArgs = JSON.parse(toolCall.function.arguments || "{}");
              newMessageHistory.push({
                role: "tool-request",
                content:
                  toolCall.function.name + "(" + JSON.stringify(toolArgs) + ")",
                id: toolCall.id,
              });
              const toolResult = await mcpClient.callTool({
                name: toolCall.function.name,
                arguments: toolArgs,
              });
              const toolResultText = (
                toolResult.content as Array<{ text: string }>
              )[0].text;
              conversationHistory.push({
                role: "tool",
                name: toolCall.function.name,
                tool_call_id: toolCall.id,
                content: toolResultText,
              });
              newMessageHistory.push({
                role: "tool-response",
                content: toolResultText,
                id: toolCall.id,
              });
              setMessageHistory([...newMessageHistory]);
            }
          }
        }
      }
    } catch (error) {
      console.error("Error calling OpenAI API:", error);
    } finally {
      await mcpClient.close();
    }

    setIsRunning(false);
    setIsRunningTarget(false);
  }, [
    apiKey,
    baseUrl,
    extraLLMParams,
    isRunningTarget,
    llmModel,
    prompt,
    selectedTools,
    systemMessage,
    transformTools,
  ]);

  useEffect(() => {
    if (isRunningTarget) {
      setMessageHistory([]);
      runPrompt();
    }
  }, [isRunningTarget, runPrompt]);

  const MarkdownRenderer = ({ content }) => (
    <ReactMarkdown
      components={{
        code({ className, children, ...props }) {
          return (
            <code className={className} {...props}>
              {hljs.highlightAuto(String(children)).value}
            </code>
          );
        },
      }}
    >
      {content}
    </ReactMarkdown>
  );

  const MessageItem = ({ message }) => {
    let Icon, title;
    switch (message.role) {
      case "assistant":
        Icon = Bot;
        title = "Assistant";
        break;
      case "tool-request":
        Icon = Function_2;
        title = "Tool Request";
        break;
      case "tool-response":
        Icon = Reply;
        title = "Tool Response";
        break;
      default:
        Icon = InformationSquare;
        title = message.role;
    }

    return (
      <div style={{ marginBottom: "2rem" }}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "0.5rem",
            fontWeight: "bold",
          }}
        >
          <Icon />
          {title}
        </div>
        <div
          style={{
            padding: "0.5rem",
            background: "#393939",
            borderRadius: "4px",
            width: "100%",
            wordWrap: "break-word",
            border: "1px solid #525252",
          }}
        >
          {(message.role === "tool-response" && (
            <p
              dangerouslySetInnerHTML={{
                __html: hljs.highlightAuto(message.content).value,
              }}
            />
          )) || <MarkdownRenderer content={message.content} />}
        </div>
      </div>
    );
  };

  return (
    <div>
      <h1 className="title">LLM Chat for testing</h1>
      <Tabs selectedIndex={tabIndex} onChange={({ selectedIndex }) => setTabIndex(selectedIndex)}>
        <TabList scrollDebounceWait={200} style={{ padding: "0 2rem" }}>
          <Tab>LLM Setup</Tab>
          <Tab>Tools Selection</Tab>
          <Tab>Chat</Tab>
        </TabList>
        <TabPanels>
          <TabPanel style={{ background: "#161616" }}>
            <Grid fullWidth>
              <Column sm={4} md={8} lg={8}>
                <Tile style={{ marginBottom: "1rem", padding: "1rem" }}>
                  <div style={{ display: "flex", justifyContent: "flex-end" }}>
                    <Toggle
                      labelText="Store in LocalStorage"
                      labelA="Off"
                      labelB="On"
                      toggled={isStoreInLocalStorage}
                      onToggle={(e) => setIsStoreInLocalStorage(e.valueOf())}
                      id="enabledLocalStorage"
                    />
                  </div>
                  <Form aria-label="sample form">
                    <Grid fullWidth>
                      <Column sm={4} md={4} lg={4}>
                        <Stack gap={7}>
                          <ComboBox
                            allowCustomValue
                            onChange={(data) => {
                              setBaseUrl(data.selectedItem);
                            }}
                            id="base-url"
                            items={baseUrlItems}
                            selectedItem={baseUrl}
                            titleText="LLM API Base URL"
                          />
                          <TextInput
                            id="llm-model"
                            labelText="LLM Model"
                            value={llmModel}
                            placeholder="Type LLM model here..."
                            onChange={(e) => setLLMModel(e.target.value)}
                          />
                        </Stack>
                      </Column>
                      <Column sm={4} md={4} lg={4}>
                        <Stack gap={7}>
                          <PasswordInput
                            id="api-key"
                            labelText="API Key"
                            onChange={(e) => setApiKey(e.target.value)}
                            value={apiKey}
                            placeholder="Type your API key here..."
                            size="md"
                          />
                          <TextArea
                            labelText="Extra LLM Parameters"
                            placeholder='{"max_tokens":400,"temperature":0.7,"tool_choice":"auto"}'
                            id="extra-llm-input"
                            rows={4}
                            value={rawExtraLLMParams}
                            onBlur={() => {
                              try {
                                const newParams = JSON.parse(rawExtraLLMParams);
                                setExtraLLMParams(newParams);
                              } catch (error) {
                                console.error("Invalid JSON:", error);
                                setRawExtraLLMParams(
                                  JSON.stringify(extraLLMParams)
                                );
                              }
                            }}
                            onChange={(e) =>
                              setRawExtraLLMParams(e.target.value)
                            }
                            style={{
                              background: "#393939",
                            }}
                          />
                        </Stack>
                      </Column>
                    </Grid>
                  </Form>
                </Tile>
              </Column>
            </Grid>
          </TabPanel>
          <TabPanel style={{ background: "#161616" }}>
            <Tile style={{ marginBottom: "1rem", padding: "1rem" }}>
              <Stack gap={7}>
                {isLoadingTools && <p>Loading tools...</p>}
                {!isLoadingTools && (
                  <FormGroup legendText="Select tools">
                    <Checkbox
                      id="select-all"
                      labelText="Select All"
                      checked={isAllSelected}
                      onChange={toggleSelectAll}
                    />
                    {/* Grid layout for 100 tools */}
                    <div
                      style={{
                        display: "grid",
                        gridTemplateColumns:
                          "repeat(auto-fit, minmax(200px, 1fr))",
                        gap: "1rem",
                        marginTop: "1rem",
                      }}
                    >
                      {tools.map((tool) => (
                        <Checkbox
                          key={tool.name}
                          id={tool.name + ""}
                          labelText={tool.name + ""}
                          helperText={tool.description}
                          checked={selectedTools.includes(tool.name!)}
                          onChange={toggleItem}
                        />
                      ))}
                    </div>
                  </FormGroup>
                )}
              </Stack>
            </Tile>
          </TabPanel>
          <TabPanel style={{ background: "#161616" }}>
            <Grid fullWidth>
              <Column sm={4} md={8} lg={5}>
                <Tile style={{ marginBottom: "1rem", padding: "1rem" }}>
                  <p style={{ fontWeight: "bold", marginBottom: "0.5rem" }}>
                    LLM Model: {llmModel}
                  </p>
                  <Form
                    aria-label="sample form"
                    style={{ marginBottom: "2rem" }}
                  >
                    <Stack gap={7}>
                      <TextArea
                        labelText="System message"
                        placeholder="Type system message here..."
                        id="system-input"
                        rows={4}
                        value={systemMessage}
                        onChange={(e) => setSystemMessage(e.target.value)}
                        style={{
                          background: "#393939",
                        }}
                      />
                      <TextArea
                        labelText="Enter Prompt"
                        placeholder="Type your prompt here..."
                        id="prompt-input"
                        rows={4}
                        value={prompt}
                        onChange={(e) => setPrompt(e.target.value)}
                        style={{
                          background: "#393939",
                        }}
                      />
                      <div
                        style={{
                          display: "flex",
                          justifyContent: "flex-end",
                        }}
                      >
                        <Button onClick={() => setIsRunningTarget(!isRunning)}>
                          {isRunning ? "Stop" : "Send"}
                        </Button>
                      </div>
                    </Stack>
                  </Form>
                  <Accordion>
                    <AccordionItem
                      title={`List of Selected Tools (${selectedTools.length})`}
                    >
                      <ul style={{ paddingLeft: "1rem" }}>
                        {selectedTools.map((toolName, index) => (
                          <li key={index}>{toolName}</li>
                        ))}
                      </ul>
                    </AccordionItem>
                  </Accordion>
                </Tile>
              </Column>
              <Column sm={4} md={8} lg={11}>
                <Tile style={{ padding: "1rem" }}>
                  {response}
                  {messageHistory.map((message) => (
                    <MessageItem
                      key={message.id + message.role}
                      message={message}
                    />
                  ))}
                </Tile>
              </Column>
            </Grid>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </div>
  );
};
