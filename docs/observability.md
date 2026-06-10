# Observability: Tracing, Logging, and Auditing

This document describes the observability architecture in Wanaku, covering distributed tracing, request ID propagation, and structured logging with traceability.

## Overview

Wanaku integrates OpenTelemetry (OTel) for distributed tracing and Micrometer for metrics, providing end-to-end observability across the entire request chain:

- **MCP Client** → **Router Backend** → **gRPC** → **Capability Service** → **Downstream Services**

Every request flowing through the system carries:

1. **W3C `traceparent`** header for distributed trace context propagation (automatic via OTel)
2. **`x-wanaku-request-id`** for MCP request correlation (explicit propagation)
3. **`request_id`** field in gRPC proto messages for contract-level traceability

## Architecture

### Request Flow and Trace Propagation

```text
┌──────────┐     HTTP/MCP      ┌────────────────┐     gRPC        ┌──────────────────┐
│ MCP Client │ ──────────────→ │ Router Backend  │ ─────────────→ │ Capability Service│
│             │  traceparent    │                 │  traceparent   │                   │
│             │  x-wanaku-      │  MDC: requestId │  x-wanaku-     │  MDC: requestId   │
│             │  request-id     │  MDC: traceId   │  request-id    │  Span: requestId  │
└──────────┘                   └────────────────┘                 └──────────────────┘
                                        │
                                        ▼
                                 ┌──────────────┐
                                 │ OTel Collector│
                                 │  (4317 gRPC)  │
                                 └──────┬───────┘
                                        │
                                        ▼
                                 ┌──────────────┐
                                 │   Jaeger UI   │
                                 │  (16686 HTTP) │
                                 └──────────────┘
```

### Components

| Component | Role |
|-----------|------|
| `quarkus-opentelemetry` | Auto-instruments HTTP, gRPC, Vert.x; creates spans; manages trace context |
| `McpTracingInstrumenter` | Built into `quarkus-mcp-server` 1.13.0; auto-creates MCP spans with `requestId`, `toolName`, `session_id` attributes |
| `RequestIdContext` | Helper class that manages MDC keys (`requestId`, `connectionId`) and OTel span attributes (`wanaku.mcp.request_id`, etc.) |
| `RequestIdClientInterceptor` | gRPC client interceptor that injects `x-wanaku-request-id` header from MDC |
| `TracingServerInterceptor` | gRPC server interceptor that extracts `x-wanaku-request-id` header and sets MDC + span attribute |
| `McpHeadersSupplier` | Propagates W3C `traceparent` and `x-wanaku-request-id` from MDC to downstream MCP calls |
| `quarkus-micrometer-registry-prometheus` | Added to router backend to replace quarkiverse v1 (incompatible with `quarkus-opentelemetry`); provides Prometheus metrics export |

## Configuration

### Router Backend

The router backend's `application.properties` configures OTel:

```properties
# OpenTelemetry
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
quarkus.otel.exporter.otlp.traces.protocol=grpc
quarkus.otel.resource.attributes=service.name=wanaku-router,service.version=${quarkus.application.version}
quarkus.otel.traces.sampler=parentbased_always_on
quarkus.otel.propagators=tracecontext,baggage
```

### Capability Services

Capability services use a shared base configuration in `wanaku-capabilities-base`:

```properties
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
quarkus.otel.exporter.otlp.traces.protocol=grpc
quarkus.otel.resource.attributes=service.name=${wanaku.service.name:wanaku-capability},service.version=${quarkus.application.version}
quarkus.otel.traces.sampler=parentbased_always_on
quarkus.otel.propagators=tracecontext,baggage
```

Each capability service must set `wanaku.service.name` to identify itself (e.g., `wanaku-file-resource`, `wanaku-http-tool`).

### Log Format

All log profiles include trace and request context:

```text
%d{yyyy-MM-dd HH:mm:ss} %-5p [%c] (%t) [traceId=%X{traceId}, requestId=%X{requestId}] %s%e%n
```

MDC keys available in log output:

| MDC Key | Source | Description |
|---------|--------|-------------|
| `traceId` | OTel (automatic) | W3C trace ID for distributed tracing |
| `requestId` | MCP request | MCP protocol request ID for correlation |
| `connectionId` | MCP connection | MCP connection/session identifier |

## Proto Changes

Three proto messages were extended with a `request_id` field:

### ToolInvokeRequest (`toolrequest.proto`)

```protobuf
message ToolInvokeRequest {
  // ... existing fields 1-6 ...
  string request_id = 7;
}
```

### ResourceRequest (`resourcerequest.proto`)

```protobuf
message ResourceRequest {
  // ... existing fields 1-6 ...
  string request_id = 7;
}
```

### CodeExecutionRequest (`codeexecution.proto`)

```protobuf
message CodeExecutionRequest {
  // ... existing fields 1-8 ...
  string request_id = 9;
}
```

## How Request ID Propagation Works

### 1. MCP Request → Router (HTTP)

When an MCP request arrives at the router, `quarkus-mcp-server`'s `McpTracingInstrumenter` automatically:

- Creates a span with `requestId` and `toolName` attributes
- The router bridge code extracts `requestId` from `ToolArguments.requestId().asString()` and `connectionId` from `arguments.connection().id()`

### 2. Router → Capability Service (gRPC)

The `InvokerBridge` and `ResourceAcquirerBridge`:

1. Call `RequestIdContext.setContext(requestId, connectionId)` to populate MDC
2. Call `RequestIdContext.setToolName(name)` or `setResourceName(name)` to add span attributes
3. The `RequestIdClientInterceptor` injects `x-wanaku-request-id` from MDC into gRPC metadata
4. OTel automatically injects `traceparent` via `quarkus.otel.instrument.grpc=true` (default)
5. On completion, `RequestIdContext.clear()` is called via `onItemOrFailure()`

### 3. Capability Service Processing

The `TracingServerInterceptor` on the capability service side:

1. Extracts `x-wanaku-request-id` from gRPC metadata
2. Sets `requestId` in MDC for logging
3. Sets `wanaku.mcp.request_id` attribute on the current OTel span
4. Wraps the `ServerCall.Listener` to ensure MDC lifecycle is properly managed

The `AbstractToolDelegate` and `AbstractResourceDelegate` also extract `requestId` from the proto message itself (as a fallback/supplement):

```java
String requestId = request.getRequestId();
if (requestId != null && !requestId.isEmpty()) {
    MDC.put("requestId", requestId);
}
```

### 4. Code Execution

The `CodeExecutionBridge` uses the current OTel trace ID as the request ID (since it doesn't have direct access to MCP `ToolArguments.requestId`):

```java
Span currentSpan = Span.current();
String requestId = currentSpan.getSpanContext().getTraceId();
RequestIdContext.setContext(requestId, connectionId);
```

### 5. Downstream MCP Calls

When the router makes outbound MCP calls (via langchain4j), the `ClientUtil.createClient()` method uses `McpHeadersSupplier` to propagate:

- W3C `traceparent` (from OTel context propagation)
- `x-wanaku-request-id` (from MDC)

## OTel Span Attributes

The following custom span attributes are set:

| Attribute | Set By | Description |
|-----------|--------|-------------|
| `wanaku.mcp.request_id` | Router bridges, TracingServerInterceptor | MCP request ID |
| `wanaku.mcp.connection_id` | Router bridges | MCP connection/session ID |
| `wanaku.mcp.tool_name` | InvokerBridge | Name of the tool being invoked |
| `wanaku.mcp.resource_name` | ResourceAcquirerBridge | Name of the resource being acquired |

These attributes are searchable in Jaeger/Grafana for quick request correlation.

## Deployment

### Docker Compose

The `docker-compose.yml` and `docker-compose-noauth.yml` include:

- **OTel Collector** (`otel-collector:4317`): Receives OTLP data from router and capability services
- **Jaeger** (`jaeger:16686`): UI for trace visualization; receives data from OTel Collector via OTLP

Environment variables set on services:

```yaml
QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
```

### OTel Collector Pipeline

The `otel-collector-config.yaml` configures:

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

exporters:
  otlp/jaeger:
    endpoint: jaeger:4317
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      exporters: [otlp/jaeger]
```

### Accessing Jaeger

After starting the stack with Docker Compose, Jaeger UI is available at:

```text
http://localhost:16686
```

Search by:

- **Service**: `wanaku-router` or the capability service name
- **Span attribute**: `wanaku.mcp.request_id=<request-id>`
- **Operation**: tool invocation or resource acquisition
