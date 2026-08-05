/**
 * Tool Call Event model for debugging and monitoring tool invocations.
 */

export enum ToolCallEventType {
  STARTED = "started",
  COMPLETED = "completed",
  FAILED = "failed",
}

export enum ToolCallErrorCategory {
  NONE = "none",
  SERVICE_UNAVAILABLE = "service_unavailable",
  TOOL_DEFINITION_ERROR = "tool_definition_error",
  INVALID_ARGUMENTS = "invalid_arguments",
  EXECUTION_ERROR = "execution_error",
  UNKNOWN = "unknown",
}

export interface ToolCallEvent {
  // Event metadata
  eventId?: string;
  eventType?: ToolCallEventType;
  timestamp?: string; // ISO 8601 timestamp

  // Tool information
  toolName?: string;
  toolType?: string;
  connectionId?: string;

  // Service information
  serviceId?: string;
  serviceAddress?: string;

  // Request information
  arguments?: Record<string, string>;
  headers?: Record<string, string>;
  body?: string;
  configurationURI?: string;
  secretsURI?: string;

  // Response information
  isError?: boolean;
  content?: string;
  duration?: number; // milliseconds

  // Error information
  errorCategory?: ToolCallErrorCategory;
  errorMessage?: string;
  errorDetails?: string;
}
