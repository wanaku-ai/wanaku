/**
 * Wanaku Evaluator Action SDK — TypeScript Definitions
 *
 * These types define the host API available to WASM action scripts
 * running inside the Wanaku evaluator engine.
 *
 * Usage:
 *   1. Place this file next to your action script
 *   2. Write your action in TypeScript
 *   3. Compile to WASM: javy compile action.ts -o action.wasm
 *
 * @packageDocumentation
 */

// -- Types ------------------------------------------------------------------

/** A tool registered in the Wanaku registry. */
export interface ToolEntry {
  /** Unique tool name. */
  name: string;
  /** Human-readable description. */
  description: string;
  /** Tool endpoint URI. */
  uri: string;
  /** Tool type (e.g., "mcp-forward", "echo-tool"). */
  toolType: string;
  /** Namespace the tool belongs to, if any. */
  namespace?: string;
}

/** A single message from the conversation history. */
export interface Message {
  /** Message role: "system", "user", or "assistant". */
  role: string;
  /** Message text content. */
  content: string;
}

/** Context passed to the action's evaluate function. */
export interface EvaluationContext {
  /** MCP method that triggered this evaluation (e.g., "tools/call", "tools/list"). */
  method: string;
  /** Namespace extracted from the request URL path. */
  namespace: string;
  /** Tool name from the request (present for tools/call). */
  toolName?: string;
  /** Tool call arguments as key-value pairs. */
  arguments: [string, string][];
  /** Raw string output from the LLM operation. */
  llmResult: string;
  /** Conversation correlation ID, if available. */
  conversationId?: string;
}

// -- Registry ---------------------------------------------------------------

/** Read and write access to the Wanaku tool registry. */
export declare namespace registry {
  /** List all tools across all namespaces. */
  function listTools(): ToolEntry[];

  /** List tools in a specific namespace. */
  function listToolsInNamespace(namespace: string): ToolEntry[];

  /** Get a single tool by name. Returns undefined if not found. */
  function getTool(name: string): ToolEntry | undefined;

  /**
   * Copy a tool into a target namespace.
   * The tool keeps its name but its namespace is updated.
   * Returns true if the tool was found and copied.
   */
  function copyToolToNamespace(
    toolName: string,
    targetNamespace: string
  ): boolean;
}

// -- Conversation -----------------------------------------------------------

/** Access to conversation history recorded by the intercept filter. */
export declare namespace conversation {
  /**
   * Get conversation messages for a given correlation ID.
   * Returns an empty array if no history exists.
   */
  function getHistory(conversationId: string): Message[];
}

// -- Response ---------------------------------------------------------------

/**
 * Control the MCP response sent back to the client.
 * Only one response function should be called per evaluation.
 * If none is called, the default behavior is pass (continue).
 */
export declare namespace response {
  /** Allow the request to proceed to the next filter. */
  function pass(): void;

  /** Block the request with a JSON-RPC error. */
  function block(reason: string): void;

  /** Log a warning but allow the request to proceed. */
  function warn(message: string): void;

  /**
   * Return a filtered tools/list response containing only
   * the named tools. Only meaningful for tools/list triggers.
   */
  function filterTools(toolNames: string[]): void;

  /** Set a metadata key on the filter context for downstream filters. */
  function setMetadata(key: string, value: string): void;
}

// -- Logging ----------------------------------------------------------------

/** Structured logging to the host's tracing infrastructure. */
export declare namespace log {
  /** Log at info level. */
  function info(message: string): void;

  /** Log at warn level. */
  function warn(message: string): void;

  /** Log at error level. */
  function error(message: string): void;
}

// -- Entry Point ------------------------------------------------------------

/**
 * The function your action must export.
 * Called by the host when an evaluator's trigger matches.
 *
 * @example
 * ```typescript
 * export function evaluate(ctx: EvaluationContext): void {
 *   const level = JSON.parse(ctx.llmResult).level;
 *   if (level === "red") {
 *     response.block("Dangerous operation detected");
 *   } else {
 *     response.pass();
 *   }
 * }
 * ```
 */
export declare function evaluate(ctx: EvaluationContext): void;
