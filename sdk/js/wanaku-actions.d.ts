/**
 * Wanaku Evaluator Action SDK — TypeScript Definitions
 *
 * These types define the host API available to WASM action scripts
 * running inside the Wanaku evaluator engine.
 *
 * This file declares the `wanaku:evaluator/*` modules that an action imports.
 *
 * Usage:
 *   1. Place this file next to your action script
 *   2. Write your action in JavaScript, importing the host functions from
 *      the `wanaku:evaluator/*` modules
 *   3. Compile to WASM:
 *      jco componentize action.js --wit evaluator.wit \
 *        --world-name evaluator-action --disable all -o action.wasm
 *
 * @packageDocumentation
 */

// -- Types ------------------------------------------------------------------

declare module 'wanaku:evaluator/types' {
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
}

// -- Registry ---------------------------------------------------------------

/** Read and write access to the Wanaku tool registry. */
declare module 'wanaku:evaluator/registry' {
  import type { ToolEntry } from 'wanaku:evaluator/types';

  /** List all tools across all namespaces. */
  export function listTools(): ToolEntry[];

  /** List tools in a specific namespace. */
  export function listToolsInNamespace(namespace: string): ToolEntry[];

  /** Get a single tool by name. Returns undefined if not found. */
  export function getTool(name: string): ToolEntry | undefined;

  /**
   * Copy a tool into a target namespace.
   * The tool keeps its name but its namespace is updated.
   * Returns true if the tool was found and copied.
   */
  export function copyToolToNamespace(
    toolName: string,
    targetNamespace: string
  ): boolean;
}

// -- Conversation -----------------------------------------------------------

/** Access to conversation history recorded by the intercept filter. */
declare module 'wanaku:evaluator/conversation' {
  import type { Message } from 'wanaku:evaluator/types';

  /**
   * Get conversation messages for a given correlation ID.
   * Returns an empty array if no history exists.
   */
  export function getHistory(conversationId: string): Message[];
}

// -- Response ---------------------------------------------------------------

/**
 * Control the MCP response sent back to the client.
 * Only one response function should be called per evaluation.
 * If none is called, the default behavior is pass (continue).
 */
declare module 'wanaku:evaluator/response' {
  /** Allow the request to proceed to the next filter. */
  export function pass(): void;

  /** Block the request with a JSON-RPC error. */
  export function block(reason: string): void;

  /**
   * Signal that the request cannot be evaluated because the LLM result
   * or input data is malformed. Distinct from block: this means
   * "I cannot make a decision" rather than "I decided to reject."
   */
  export function rejectMalformed(reason: string): void;

  /** Log a warning but allow the request to proceed. */
  export function warn(message: string): void;

  /**
   * Return a filtered tools/list response containing only the named
   * tools in the request namespace. Only meaningful for tools/list triggers.
   */
  export function filterTools(toolNames: string[]): void;

  /** Set a metadata key on the filter context for downstream filters. */
  export function setMetadata(key: string, value: string): void;
}

// -- Validation -------------------------------------------------------------

/** Validate LLM results against the evaluator's declared schema. */
declare module 'wanaku:evaluator/validation' {
  /**
   * Validate a raw string against the evaluator's declared result schema.
   * Returns the validated string. Throws an error whose payload is the
   * error description if validation fails. If no schema is declared,
   * validation always succeeds.
   */
  export function verifyLlmResult(raw: string): string;
}

// -- Logging ----------------------------------------------------------------

/** Structured logging to the host's tracing infrastructure. */
declare module 'wanaku:evaluator/log' {
  /** Log at info level. */
  export function info(message: string): void;

  /** Log at warn level. */
  export function warn(message: string): void;

  /** Log at error level. */
  export function error(message: string): void;
}

// -- Entry Point ------------------------------------------------------------
//
// Your action must export an `evaluate` function. The host calls it when an
// evaluator's trigger matches:
//
//   import type { EvaluationContext } from 'wanaku:evaluator/types';
//   import { block, pass } from 'wanaku:evaluator/response';
//
//   export function evaluate(ctx: EvaluationContext): void {
//     const level = JSON.parse(ctx.llmResult).level;
//     if (level === "red") {
//       block("Dangerous operation detected");
//     } else {
//       pass();
//     }
//   }
