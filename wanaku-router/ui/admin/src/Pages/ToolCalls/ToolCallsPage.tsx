import React, { useState, useEffect, useRef } from "react";
import {
  Button,
  Search,
  Tag,
  Accordion,
  AccordionItem,
  Toggle,
  ToastNotification,
} from "@carbon/react";
import { Download, TrashCan } from "@carbon/icons-react";
import { ToolCallEvent, ToolCallEventType, ToolCallErrorCategory } from "../../models";

const MAX_EVENTS = 1000;

interface FilterOptions {
  connectionId: string;
  toolName: string;
  errorCategory: string;
}

const ToolCallsPage: React.FC = () => {
  const [events, setEvents] = useState<ToolCallEvent[]>([]);
  const [filteredEvents, setFilteredEvents] = useState<ToolCallEvent[]>([]);
  const [filters, setFilters] = useState<FilterOptions>({
    connectionId: "",
    toolName: "",
    errorCategory: "",
  });
  const [autoScroll, setAutoScroll] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  const setupSSE = () => {
    const baseUrl = VITE_API_URL || window.location.origin;
    const eventSource = new EventSource(baseUrl + "/api/v2/tool-calls/notifications");

    eventSource.onopen = () => {
      console.log("Tool Call Events SSE connection opened");
    };

    eventSource.addEventListener("started", (event) => {
      const toolCallEvent = JSON.parse(event.data) as ToolCallEvent;
      addEvent(toolCallEvent);
    });

    eventSource.addEventListener("completed", (event) => {
      const toolCallEvent = JSON.parse(event.data) as ToolCallEvent;
      updateEvent(toolCallEvent);
    });

    eventSource.addEventListener("failed", (event) => {
      const toolCallEvent = JSON.parse(event.data) as ToolCallEvent;
      updateEvent(toolCallEvent);
    });

    eventSource.onerror = (error) => {
      console.error("SSE error:", error);
      setErrorMessage("Connection to event stream lost. Attempting to reconnect...");
    };

    return eventSource;
  };

  const addEvent = (event: ToolCallEvent) => {
    setEvents((prev) => {
      const updated = [event, ...prev];
      return updated.slice(0, MAX_EVENTS);
    });
  };

  const updateEvent = (event: ToolCallEvent) => {
    setEvents((prev) => {
      const index = prev.findIndex((e) => e.eventId === event.eventId);
      if (index !== -1) {
        const updated = [...prev];
        updated[index] = { ...updated[index], ...event };
        return updated;
      }
      return prev;
    });
  };

  const applyFilters = () => {
    let filtered = events;

    if (filters.connectionId) {
      filtered = filtered.filter((e) =>
        e.connectionId?.toLowerCase().includes(filters.connectionId.toLowerCase())
      );
    }

    if (filters.toolName) {
      filtered = filtered.filter((e) =>
        e.toolName?.toLowerCase().includes(filters.toolName.toLowerCase())
      );
    }

    if (filters.errorCategory && filters.errorCategory !== "all") {
      filtered = filtered.filter((e) => e.errorCategory === filters.errorCategory);
    }

    setFilteredEvents(filtered);
  };

  const clearEvents = () => {
    setEvents([]);
    setFilteredEvents([]);
  };

  const exportToJSON = () => {
    const dataStr = JSON.stringify(filteredEvents, null, 2);
    const dataBlob = new Blob([dataStr], { type: "application/json" });
    const url = URL.createObjectURL(dataBlob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `tool-call-events-${new Date().toISOString()}.json`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const getEventTypeColor = (eventType?: ToolCallEventType): "blue" | "green" | "red" | "gray" => {
    switch (eventType) {
      case ToolCallEventType.STARTED:
        return "blue";
      case ToolCallEventType.COMPLETED:
        return "green";
      case ToolCallEventType.FAILED:
        return "red";
      default:
        return "gray";
    }
  };

  const getErrorCategoryLabel = (category?: ToolCallErrorCategory): string => {
    switch (category) {
      case ToolCallErrorCategory.SERVICE_UNAVAILABLE:
        return "Service Unavailable - Check integration service";
      case ToolCallErrorCategory.TOOL_DEFINITION_ERROR:
        return "Tool Definition - Check tool schema";
      case ToolCallErrorCategory.INVALID_ARGUMENTS:
        return "Invalid Arguments - Check LLM prompt/schema";
      case ToolCallErrorCategory.EXECUTION_ERROR:
        return "Execution Error - Tool returned error";
      case ToolCallErrorCategory.UNKNOWN:
        return "Unknown Error";
      default:
        return "";
    }
  };

  const formatTimestamp = (timestamp?: string): string => {
    if (!timestamp) return "N/A";
    return new Date(timestamp).toLocaleString();
  };

  const formatDuration = (duration?: number): string => {
    if (duration === undefined || duration === null) return "N/A";
    return `${duration}ms`;
  };

  useEffect(() => {
    eventSourceRef.current = setupSSE();

    return () => {
      eventSourceRef.current?.close();
    };
  }, []);

  useEffect(() => {
    applyFilters();
  }, [events, filters]);

  useEffect(() => {
    if (autoScroll && scrollRef.current) {
      scrollRef.current.scrollTop = 0;
    }
  }, [filteredEvents, autoScroll]);

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => {
        setErrorMessage(null);
      }, 10000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);

  return (
    <div style={{ padding: "2rem" }}>
      {errorMessage && (
        <ToastNotification
          kind="error"
          title="Connection Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          timeout={10000}
          style={{ position: "fixed", top: "3rem", right: "1rem", zIndex: 9999 }}
        />
      )}

      <h1 className="title">Tool Call Debugger</h1>
      <p className="description">
        Real-time monitoring and debugging of tool invocations, errors, and performance.
      </p>

      <div style={{ margin: "2rem 0", display: "flex", gap: "1rem", alignItems: "center" }}>
        <Search
          placeholder="Filter by Connection ID"
          labelText="Connection ID"
          value={filters.connectionId}
          onChange={(e) => setFilters({ ...filters, connectionId: e.target.value })}
          size="lg"
          style={{ flex: 1 }}
        />
        <Search
          placeholder="Filter by Tool Name"
          labelText="Tool Name"
          value={filters.toolName}
          onChange={(e) => setFilters({ ...filters, toolName: e.target.value })}
          size="lg"
          style={{ flex: 1 }}
        />
        <select
          value={filters.errorCategory}
          onChange={(e) => setFilters({ ...filters, errorCategory: e.target.value })}
          style={{
            padding: "0.75rem",
            border: "1px solid #8d8d8d",
            backgroundColor: "#fff",
            cursor: "pointer",
          }}
        >
          <option value="">All Error Categories</option>
          <option value={ToolCallErrorCategory.SERVICE_UNAVAILABLE}>Service Unavailable</option>
          <option value={ToolCallErrorCategory.TOOL_DEFINITION_ERROR}>Tool Definition Error</option>
          <option value={ToolCallErrorCategory.INVALID_ARGUMENTS}>Invalid Arguments</option>
          <option value={ToolCallErrorCategory.EXECUTION_ERROR}>Execution Error</option>
          <option value={ToolCallErrorCategory.UNKNOWN}>Unknown Error</option>
        </select>
      </div>

      <div style={{ margin: "1rem 0", display: "flex", gap: "1rem", alignItems: "center" }}>
        <Toggle
          id="auto-scroll-toggle"
          labelText="Auto-scroll to latest"
          toggled={autoScroll}
          onToggle={(checked) => setAutoScroll(checked)}
        />
        <Button kind="danger" renderIcon={TrashCan} onClick={clearEvents}>
          Clear Events
        </Button>
        <Button kind="tertiary" renderIcon={Download} onClick={exportToJSON}>
          Export to JSON
        </Button>
        <span style={{ marginLeft: "auto", color: "#525252" }}>
          Showing {filteredEvents.length} of {events.length} events (max {MAX_EVENTS})
        </span>
      </div>

      <div ref={scrollRef} style={{ maxHeight: "calc(100vh - 25rem)", overflow: "auto" }}>
        {filteredEvents.map((event, index) => (
          <div
            key={`${event.eventId}-${index}`}
            style={{
              marginBottom: "1rem",
              border: "1px solid #e0e0e0",
              borderRadius: "4px",
              backgroundColor: "#fff",
            }}
          >
            <div
              style={{
                padding: "1rem",
                display: "flex",
                gap: "1rem",
                alignItems: "center",
                borderBottom: "1px solid #e0e0e0",
              }}
            >
              <Tag type={getEventTypeColor(event.eventType)}>
                {event.eventType?.toUpperCase()}
              </Tag>
              <span style={{ fontWeight: "bold" }}>{event.toolName || "N/A"}</span>
              <span style={{ color: "#525252" }}>{formatTimestamp(event.timestamp)}</span>
              <span style={{ color: "#525252" }}>Duration: {formatDuration(event.duration)}</span>
              {event.isError ? (
                <Tag type="red">ERROR</Tag>
              ) : event.eventType === ToolCallEventType.COMPLETED ? (
                <Tag type="green">SUCCESS</Tag>
              ) : (
                <Tag type="blue">IN PROGRESS</Tag>
              )}
              <span style={{ marginLeft: "auto", color: "#8d8d8d", fontSize: "0.875rem" }}>
                Connection: {event.connectionId || "N/A"}
              </span>
            </div>

            <Accordion>
              <AccordionItem title="Event Details">
                <div style={{ padding: "1rem" }}>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
                    <div>
                      <h4>Request Information</h4>
                      <p><strong>Tool Type:</strong> {event.toolType || "N/A"}</p>
                      <p><strong>Service ID:</strong> {event.serviceId || "N/A"}</p>
                      <p><strong>Service Address:</strong> {event.serviceAddress || "N/A"}</p>
                      {event.arguments && (
                        <details>
                          <summary><strong>Arguments</strong></summary>
                          <pre style={{ fontSize: "0.75rem", overflow: "auto" }}>
                            {JSON.stringify(event.arguments, null, 2)}
                          </pre>
                        </details>
                      )}
                      {event.headers && (
                        <details>
                          <summary><strong>Headers</strong></summary>
                          <pre style={{ fontSize: "0.75rem", overflow: "auto" }}>
                            {JSON.stringify(event.headers, null, 2)}
                          </pre>
                        </details>
                      )}
                      {event.body && (
                        <details>
                          <summary><strong>Body</strong></summary>
                          <pre style={{ fontSize: "0.75rem", overflow: "auto" }}>{event.body}</pre>
                        </details>
                      )}
                    </div>
                    <div>
                      <h4>Response Information</h4>
                      {event.isError && event.errorCategory && (
                        <div style={{ marginBottom: "1rem" }}>
                          <Tag type="red" style={{ marginBottom: "0.5rem" }}>
                            {getErrorCategoryLabel(event.errorCategory)}
                          </Tag>
                          {event.errorMessage && (
                            <p><strong>Error:</strong> {event.errorMessage}</p>
                          )}
                          {event.errorDetails && (
                            <details>
                              <summary><strong>Error Details</strong></summary>
                              <pre style={{ fontSize: "0.75rem", overflow: "auto" }}>
                                {event.errorDetails}
                              </pre>
                            </details>
                          )}
                        </div>
                      )}
                      {event.content && (
                        <details open>
                          <summary><strong>Response Content</strong></summary>
                          <pre style={{ fontSize: "0.75rem", overflow: "auto", maxHeight: "200px" }}>
                            {event.content}
                          </pre>
                        </details>
                      )}
                    </div>
                  </div>
                </div>
              </AccordionItem>
            </Accordion>
          </div>
        ))}
        {filteredEvents.length === 0 && (
          <div style={{ textAlign: "center", padding: "3rem", color: "#525252" }}>
            <p>No tool call events yet. Events will appear here when tools are invoked.</p>
          </div>
        )}
      </div>
    </div>
  );
};

export { ToolCallsPage };
export const Component = ToolCallsPage;
