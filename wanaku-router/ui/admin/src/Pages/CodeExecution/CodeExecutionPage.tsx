import React, { useState, useRef, useCallback, useEffect } from "react";
import {
  Button,
  Select,
  SelectItem,
  TextArea,
  ToastNotification,
  InlineLoading,
  NumberInput,
  Tile,
  Grid,
  Column,
} from "@carbon/react";
import { Play, Stop } from "@carbon/icons-react";
import { postApiV2CodeExecutionEngineEngineTypeLanguage, getApiV1Capabilities } from "../../api/wanaku-router-api";
import { CodeExecutionRequest, ServiceTarget } from "../../models";
import "./CodeExecutionPage.scss";

interface ExecutionEvent {
  eventType: string;
  taskId: string;
  output?: string;       // Standard output content (for OUTPUT events)
  error?: string;        // Error output content (for ERROR events)
  message?: string;      // Human-readable message (for FAILED/ERROR events)
  exitCode?: number;     // Exit code (for COMPLETED/FAILED events)
  status?: string;       // Execution status
  timestamp: string;     // Local timestamp when event was received
}

// Default fallback data (used if API fails)
const FALLBACK_ENGINE_TYPES = [
  { value: "jvm", label: "JVM" },
  { value: "interpreted", label: "Interpreted" },
  { value: "native", label: "Native" },
];

const FALLBACK_LANGUAGES: Record<string, { value: string; label: string }[]> = {
  jvm: [
    { value: "java", label: "Java" },
    { value: "kotlin", label: "Kotlin" },
    { value: "scala", label: "Scala" },
  ],
  interpreted: [
    { value: "python", label: "Python" },
    { value: "javascript", label: "JavaScript" },
    { value: "ruby", label: "Ruby" },
  ],
  native: [
    { value: "go", label: "Go" },
    { value: "rust", label: "Rust" },
  ],
};

const DEFAULT_CODE: Record<string, string> = {
  java: `public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}`,
  python: `print("Hello, World!")`,
  javascript: `console.log("Hello, World!");`,
  kotlin: `fun main() {
    println("Hello, World!")
}`,
  scala: `object Main extends App {
  println("Hello, World!")
}`,
  ruby: `puts "Hello, World!"`,
  go: `package main

import "fmt"

func main() {
    fmt.Println("Hello, World!")
}`,
  rust: `fn main() {
    println!("Hello, World!");
}`,
};

const CodeExecutionPage: React.FC = () => {
  const [engineType, setEngineType] = useState<string>("");
  const [language, setLanguage] = useState<string>("");
  const [code, setCode] = useState<string>("");
  const [timeout, setTimeout] = useState<number>(30);
  const [output, setOutput] = useState<ExecutionEvent[]>([]);
  const [isExecuting, setIsExecuting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [taskId, setTaskId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Dynamic data from API
  const [engineTypes, setEngineTypes] = useState<{ value: string; label: string }[]>([]);
  const [languagesByEngine, setLanguagesByEngine] = useState<Record<string, { value: string; label: string }[]>>({});

  const eventSourceRef = useRef<EventSource | null>(null);
  const outputRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = useCallback(() => {
    if (outputRef.current) {
      outputRef.current.scrollTop = outputRef.current.scrollHeight;
    }
  }, []);

  // Auto-scroll when output changes
  useEffect(() => {
    console.log("🔄 Output state changed, length:", output.length, "items:", output);
    scrollToBottom();
  }, [output, scrollToBottom]);

  // Fetch available code execution engines from the API
  useEffect(() => {
    const fetchEngines = async () => {
      try {
        setIsLoading(true);
        const response = await getApiV1Capabilities();

        if (response.data && 'data' in response.data) {
          const services = response.data.data as ServiceTarget[];

          // Filter for code-execution-engine services with valid languageName
          const codeExecutionEngines = services.filter(
            (service) =>
              service.serviceType === "code-execution-engine" &&
              service.languageName &&
              service.languageName.trim() !== ""
          );

          if (codeExecutionEngines.length > 0) {
            // Group by engine type (serviceSubType)
            const engineTypesMap = new Map<string, { value: string; label: string }>();
            const languagesMap: Record<string, { value: string; label: string }[]> = {};

            codeExecutionEngines.forEach((engine) => {
              const engineTypeValue = engine.serviceSubType || "unknown";
              const languageValue = engine.languageName?.toLowerCase() || "";

              // Add engine type if not already present
              if (!engineTypesMap.has(engineTypeValue)) {
                engineTypesMap.set(engineTypeValue, {
                  value: engineTypeValue,
                  label: engineTypeValue.charAt(0).toUpperCase() + engineTypeValue.slice(1),
                });
              }

              // Add language to the engine type group
              if (!languagesMap[engineTypeValue]) {
                languagesMap[engineTypeValue] = [];
              }

              // Avoid duplicates
              const languageExists = languagesMap[engineTypeValue].some(
                (lang) => lang.value === languageValue
              );

              if (!languageExists && languageValue) {
                languagesMap[engineTypeValue].push({
                  value: languageValue,
                  label: engine.languageName || languageValue,
                });
              }
            });

            const engineTypesArray = Array.from(engineTypesMap.values());
            setEngineTypes(engineTypesArray);
            setLanguagesByEngine(languagesMap);

            // Set default values
            if (engineTypesArray.length > 0) {
              const defaultEngine = engineTypesArray[0].value;
              setEngineType(defaultEngine);

              if (languagesMap[defaultEngine] && languagesMap[defaultEngine].length > 0) {
                const defaultLanguage = languagesMap[defaultEngine][0].value;
                setLanguage(defaultLanguage);
                setCode(DEFAULT_CODE[defaultLanguage] || "");
              }
            }
          } else {
            // No code execution engines available, use fallback
            console.warn("No code execution engines found, using fallback data");
            setEngineTypes(FALLBACK_ENGINE_TYPES);
            setLanguagesByEngine(FALLBACK_LANGUAGES);
            setEngineType("interpreted");
            setLanguage("python");
            setCode(DEFAULT_CODE["python"]);
          }
        }
      } catch (error) {
        console.error("Failed to fetch code execution engines:", error);
        setErrorMessage("Failed to load available engines. Using default configuration.");

        // Use fallback data on error
        setEngineTypes(FALLBACK_ENGINE_TYPES);
        setLanguagesByEngine(FALLBACK_LANGUAGES);
        setEngineType("interpreted");
        setLanguage("python");
        setCode(DEFAULT_CODE["python"]);
      } finally {
        setIsLoading(false);
      }
    };

    fetchEngines();
  }, []);

  const handleEngineTypeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newEngineType = e.target.value;
    setEngineType(newEngineType);

    // Reset language to first available for the new engine type
    const availableLanguages = languagesByEngine[newEngineType] || [];
    if (availableLanguages.length > 0) {
      const newLanguage = availableLanguages[0].value;
      setLanguage(newLanguage);
      setCode(DEFAULT_CODE[newLanguage] || "");
    }
  };

  const handleLanguageChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newLanguage = e.target.value;
    setLanguage(newLanguage);
    setCode(DEFAULT_CODE[newLanguage] || "");
  };

  const stopExecution = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    setIsExecuting(false);
  }, []);

  const executeCode = async () => {
    console.log("🎬 Starting code execution...");

    // Clear previous output
    setOutput([]);
    console.log("🧹 Cleared previous output");
    setErrorMessage(null);
    setIsExecuting(true);

    try {
      // Encode code as base64
      const encodedCode = btoa(code);

      const request: CodeExecutionRequest = {
        code: encodedCode,
        timeout: timeout,
        arguments: [],
        environment: {},
      };

      const response = await postApiV2CodeExecutionEngineEngineTypeLanguage(
        engineType,
        language,
        request
      );

      // The response has status 201 (Created) and contains the task info
      if (response.status !== 201) {
        throw new Error(`Unexpected response status: ${response.status}`);
      }

      // Extract the response body from the data field
      // The actual response is in the body, not wrapped in a data field
      const result = response.data as any;

      console.log("Code execution response:", result);

      if (!result || !result.streamUrl) {
        throw new Error("Response missing streamUrl field");
      }

      const streamUrl = result.streamUrl;
      const taskId = result.taskId;

      setTaskId(taskId);
      console.log("Connecting to SSE stream:", streamUrl);

      // Connect to SSE stream
      const eventSource = new EventSource(streamUrl);
      eventSourceRef.current = eventSource;

      eventSource.onopen = () => {
        console.log("✅ SSE connection opened successfully");
      };

      // Generic message handler - catches ALL messages including those without specific event names
      eventSource.onmessage = (event) => {
        console.log("📨 SSE message received (generic handler):", event);
        console.log("  - Event type:", event.type);
        console.log("  - Data:", event.data);

        try {
          const data = JSON.parse(event.data) as ExecutionEvent;
          console.log("  - Parsed data:", data);
          setOutput((prev) => {
            const newOutput = [...prev, { ...data, timestamp: new Date().toISOString() }];
            console.log("  - Updated output array, length:", newOutput.length);
            return newOutput;
          });
          scrollToBottom();

          // Check if this is a terminal event and stop execution
          const eventType = data.eventType?.toLowerCase();
          if (eventType === "completed" || eventType === "failed" || eventType === "error") {
            console.log("🛑 Terminal event received, stopping execution");
            stopExecution();
          }
        } catch (error) {
          console.error("  - Failed to parse message data:", error);
        }
      };

      // Specific event handlers (uppercase names match backend enum.name())
      eventSource.addEventListener("STARTED", (event) => {
        console.log("🚀 SSE event: STARTED", event.data);
        try {
          const data = JSON.parse(event.data) as ExecutionEvent;
          setOutput((prev) => [...prev, { ...data, timestamp: new Date().toISOString() }]);
          scrollToBottom();
        } catch (error) {
          console.error("Error parsing 'STARTED' event:", error);
        }
      });

      eventSource.addEventListener("OUTPUT", (event) => {
        console.log("📝 SSE event: OUTPUT", event.data);
        try {
          const data = JSON.parse(event.data) as ExecutionEvent;
          setOutput((prev) => [...prev, { ...data, timestamp: new Date().toISOString() }]);
          scrollToBottom();
        } catch (error) {
          console.error("Error parsing 'OUTPUT' event:", error);
        }
      });

      eventSource.addEventListener("COMPLETED", (event) => {
        console.log("✅ SSE event: COMPLETED", event.data);
        try {
          const data = JSON.parse(event.data) as ExecutionEvent;
          setOutput((prev) => [...prev, { ...data, timestamp: new Date().toISOString() }]);
          scrollToBottom();
          stopExecution();
        } catch (error) {
          console.error("Error parsing 'COMPLETED' event:", error);
        }
      });

      eventSource.addEventListener("FAILED", (event) => {
        console.log("❌ SSE event: FAILED", event.data);
        try {
          const data = JSON.parse(event.data) as ExecutionEvent;
          setOutput((prev) => [...prev, { ...data, timestamp: new Date().toISOString() }]);
          scrollToBottom();
          stopExecution();
        } catch (error) {
          console.error("Error parsing 'FAILED' event:", error);
        }
      });

      eventSource.addEventListener("ERROR", (event) => {
        console.log("⚠️ SSE event: ERROR", event);
        const messageEvent = event as MessageEvent;
        if (messageEvent.data) {
          console.log("  - Error event data:", messageEvent.data);
          try {
            const data = JSON.parse(messageEvent.data) as ExecutionEvent;
            setOutput((prev) => [...prev, { ...data, timestamp: new Date().toISOString() }]);
            stopExecution();
          } catch (error) {
            console.error("Error parsing 'ERROR' event:", error);
          }
        }
      });

      eventSource.onerror = (error) => {
        console.error("💥 SSE connection error:", error);
        console.log("  - ReadyState:", eventSource.readyState);
        console.log("  - CONNECTING:", EventSource.CONNECTING);
        console.log("  - OPEN:", EventSource.OPEN);
        console.log("  - CLOSED:", EventSource.CLOSED);
        stopExecution();
      };

    } catch (error) {
      console.error("Execution error:", error);
      setErrorMessage(error instanceof Error ? error.message : "Failed to execute code");
      setIsExecuting(false);
    }
  };

  const getOutputClass = (eventType: string): string => {
    switch (eventType?.toLowerCase()) {
      case "started":
        return "output-started";
      case "completed":
        return "output-completed";
      case "failed":
      case "error":
        return "output-error";
      default:
        return "output-standard";
    }
  };

  const formatOutput = (event: ExecutionEvent): string => {
    switch (event.eventType?.toLowerCase()) {
      case "started":
        return `[STARTED] ${event.message || "Execution started"} (Task: ${event.taskId})`;
      case "completed":
        return `[COMPLETED] ${event.message || "Execution finished"} (Exit code: ${event.exitCode ?? "N/A"})`;
      case "failed":
        return `[FAILED] ${event.message || "Execution failed"} (Exit code: ${event.exitCode ?? "N/A"})`;
      case "error":
        return `[ERROR] ${event.error || event.message || "Unknown error"}`;
      case "output":
        return event.output || "";
      default:
        return event.output || event.error || event.message || JSON.stringify(event);
    }
  };

  return (
    <div className="code-execution-page">
      {errorMessage && (
        <ToastNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          timeout={10000}
          style={{ position: "fixed", top: "60px", right: "20px", zIndex: 1000 }}
        />
      )}

      <h1 className="title">Code Execution</h1>
      <p className="description">
        Execute code remotely using the Wanaku code execution engine.
        {!isLoading && engineTypes.length === 0 && (
          <span style={{ color: "orange", marginLeft: "10px" }}>
            ⚠ No code execution engines available. Using fallback configuration.
          </span>
        )}
      </p>

      <Grid className="execution-grid">
        <Column lg={8} md={8} sm={4}>
          <Tile className="code-input-tile">
            <div className="controls-row">
              <Select
                id="engine-type"
                labelText="Engine Type"
                value={engineType}
                onChange={handleEngineTypeChange}
                disabled={isExecuting || isLoading}
              >
                {engineTypes.map((engine) => (
                  <SelectItem key={engine.value} value={engine.value} text={engine.label} />
                ))}
              </Select>

              <Select
                id="language"
                labelText="Language"
                value={language}
                onChange={handleLanguageChange}
                disabled={isExecuting || isLoading}
              >
                {(languagesByEngine[engineType] || []).map((lang) => (
                  <SelectItem key={lang.value} value={lang.value} text={lang.label} />
                ))}
              </Select>

              <NumberInput
                id="timeout"
                label="Timeout (seconds)"
                value={timeout}
                min={1}
                max={300}
                onChange={(_, { value }) => setTimeout(value as number)}
                disabled={isExecuting}
              />
            </div>

            <TextArea
              id="code-editor"
              labelText="Code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              rows={15}
              disabled={isExecuting}
              className="code-textarea"
            />

            <div className="action-buttons">
              {isLoading ? (
                <InlineLoading description="Loading engines..." />
              ) : !isExecuting ? (
                <Button
                  kind="primary"
                  renderIcon={Play}
                  onClick={executeCode}
                  disabled={!code.trim() || !engineType || !language}
                >
                  Execute
                </Button>
              ) : (
                <>
                  <InlineLoading description="Executing..." />
                  <Button kind="danger" renderIcon={Stop} onClick={stopExecution}>
                    Stop
                  </Button>
                </>
              )}
            </div>
          </Tile>
        </Column>

        <Column lg={8} md={8} sm={4}>
          <Tile className="output-tile">
            <h4>
              Output {taskId && <span className="task-id">(Task: {taskId})</span>}
              {output.length > 0 && <span className="output-count"> - {output.length} events</span>}
            </h4>
            <div className="output-console" ref={outputRef}>
              {output.length === 0 ? (
                <div className="output-placeholder">
                  Output will appear here after execution...
                  {isExecuting && <div style={{ marginTop: "10px", fontSize: "0.9em", color: "#888" }}>
                    Waiting for events... Check console for debug info.
                  </div>}
                </div>
              ) : (
                output.map((event, index) => {
                  console.log(`Rendering event ${index}:`, event);
                  return (
                    <div key={index} className={`output-line ${getOutputClass(event.eventType)}`}>
                      {formatOutput(event)}
                    </div>
                  );
                })
              )}
            </div>
          </Tile>
        </Column>
      </Grid>
    </div>
  );
};

export const Component = CodeExecutionPage;
