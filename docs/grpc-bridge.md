# gRPC Bridge Architecture

This document describes the gRPC bridge component that mediates communication between the Wanaku router and remote capability services.

## Architecture Overview

The gRPC bridge implements a transport-agnostic architecture using composition:

```
┌─────────────────────────────────────────────────────────────────┐
│                      Bridge Interfaces                          │
│   ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐     │
│   │ ToolsBridge │  │ResourceBridge│  │CodeExecutorBridge │     │
│   └──────┬──────┘  └──────┬───────┘  └─────────┬─────────┘     │
│   ┌──────┴──────┐  ┌──────┴──────┐  ┌──────────┴──────────┐    │
│   │ McpBridge  │  │ProvisionBridge│ │                      │    │
│   └──────┬──────┘  └──────┬───────┘  │                      │    │
├──────────┼────────────────┼──────────┼──────────────────────┤    │
│          │                │          │                      │    │
│   ┌──────▼──────┐  ┌──────▼───────────┐  ┌─────▼──────────┐    │
│   │InvokerBridge│  │ResourceAcquirer- │  │CodeExecution-  │    │
│   │             │  │Bridge            │  │Bridge          │    │
│   └──────┬──────┘  └──────┬───────────┘  └─────┬──────────┘    │
│          │        ┌───────┤                     │               │
│          │  ┌─────▼───────▼──┐                  │               │
│          │  │ProvisionerBridge│                  │               │
│          │  └────────┬────────┘                  │               │
├──────────┼───────────┼──────────────────────────┼───────────────┤
│                    Transport Abstraction                        │
│                  WanakuBridgeTransport                          │
├─────────────────────────────────────────────────────────────────┤
│                    gRPC Implementation                          │
│   ┌─────────────────┐      ┌────────────────────┐              │
│   │  GrpcTransport  │──────│ GrpcChannelManager │              │
│   └────────┬────────┘      └────────────────────┘              │
│            │                                                    │
│   ┌────────┴────────────────────────────────┐                   │
│   │ GrpcToolResponseTransformer             │                   │
│   │ GrpcResourceResponseTransformer         │                   │
│   └─────────────────────────────────────────┘                   │
│                                                                 │
│                    MCP Implementation                           │
│   ┌──────────────────┐    ┌────────────────┐                    │
│   │ DefaultMcpBridge │────│ ForwardRegistry│                    │
│   └──────────────────┘    └────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
```

Key characteristics:
- Protocol independence via transport abstraction
- Async-first operations using Mutiny `Uni` types
- Response transformation at the transport layer (bridges never see transport-specific types)
- Service discovery integration for dynamic endpoint resolution
- Event emission for observability
- Streaming support for code execution
- MCP-to-MCP forwarding via `McpBridge`
- Consolidated provisioning via `ProvisionerBridge`
- Composition over inheritance

---

## Core Abstractions

### Bridge Interface

**Package:** `ai.wanaku.backend.bridge`

Base marker interface for all bridge implementations.

```java
public interface Bridge {
    default String name() {
        return this.getClass().getSimpleName();
    }
}
```

### WanakuBridgeTransport Interface

**Package:** `ai.wanaku.backend.bridge`

Core transport abstraction defining the contract for all transport implementations.

```java
public interface WanakuBridgeTransport {

    ProvisioningReference provision(
        String name,
        String configData,
        String secretsData,
        ServiceTarget service
    );

    Uni<ToolResponse> invokeTool(
        ToolInvokeRequest request,
        ServiceTarget service
    );

    Uni<List<ResourceContents>> acquireResource(
        ResourceRequest request,
        ServiceTarget service,
        ResourceManager.ResourceArguments arguments,
        ResourceReference mcpResource
    );

    Iterator<CodeExecutionReply> executeCode(
        CodeExecutionRequest request,
        ServiceTarget service
    );

    HealthProbeReply probeHealth(
        HealthProbeRequest request,
        ServiceTarget service
    );
}
```

The async methods (`invokeTool`, `acquireResource`) return already-transformed domain types (e.g., `ToolResponse`, `List<ResourceContents>`) rather than transport-specific protobuf types. This keeps bridges protocol-agnostic.

This abstraction enables:
- Protocol independence (supports gRPC, HTTP, WebSocket)
- Testability (easy mocking)
- Separation of concerns
- Async-first design with Mutiny `Uni` types

---

## Bridge Interfaces

### ToolsBridge

Contract for tool proxy implementations.

```java
public interface ToolsBridge extends Bridge {
    Uni<ToolResponse> execute(ToolManager.ToolArguments toolArguments, CallableReference toolReference);
}
```

### ResourceBridge

Contract for resource proxy implementations.

```java
public interface ResourceBridge extends Bridge {
    Uni<ResourceResponse> read(
        ResourceManager.ResourceArguments arguments,
        ResourceReference mcpResource
    );
}
```

### ProvisionBridge

Contract for provisioning configuration and secrets.

```java
public interface ProvisionBridge {
    ProvisioningReference provision(
        String name, String configData, String secretsData, ServiceTarget service
    );
}
```

### CodeExecutorBridge

Contract for code execution bridge implementations.

```java
public interface CodeExecutorBridge extends Bridge {
    Iterator<CodeExecutionReply> executeCode(
        String engineType,
        String language,
        CodeExecutionRequest request
    );
}
```

---

## gRPC Transport Layer

### GrpcChannelManager

**Package:** `ai.wanaku.backend.bridge.transports.grpc`

Manages gRPC channel lifecycle.

```java
class GrpcChannelManager {
    private static final Map<ServiceTarget, ManagedChannel> CHANNEL_MAP =
        new ConcurrentHashMap<>();

    public ManagedChannel createChannel(ServiceTarget service) {
        return CHANNEL_MAP.computeIfAbsent(service, s ->
            ManagedChannelBuilder
                .forTarget(s.toAddress())
                .usePlaintext()
                .build()
        );
    }

    public void closeChannel() {
        // No-op: channels are cached and reused
    }

    public void shutdown() {
        // Closes all cached channels on application shutdown
        CHANNEL_MAP.values().forEach(ch -> ch.shutdown());
        CHANNEL_MAP.clear();
    }
}
```

**Characteristics:**
- Caches and reuses gRPC channels per service target (reduces connection overhead)
- Creates plaintext gRPC channels
- No-op `closeChannel()` since channels are reused
- `shutdown()` closes all cached channels on application termination
- Design allows for future SSL/TLS support

### GrpcTransport

**Package:** `ai.wanaku.backend.bridge.transports.grpc`

Central coordinator for all gRPC transport operations.

```java
public class GrpcTransport implements WanakuBridgeTransport {
    private final GrpcChannelManager channelManager;
    private final ToolResponseTransformer<ToolInvokeReply> toolTransformer =
        new GrpcToolResponseTransformer();
    private final ResourceResponseTransformer<ResourceReply> resourceTransformer =
        new GrpcResourceResponseTransformer();

    public GrpcTransport() {
        this.channelManager = new GrpcChannelManager();
    }

    @Override
    public ProvisioningReference provision(
            String name, String configData, String secretsData,
            ServiceTarget service) {
        // Build Configuration and Secret protobuf objects
        // Call ProvisionerGrpc stub directly
    }

    @Override
    public Uni<ToolResponse> invokeTool(
            ToolInvokeRequest request, ServiceTarget service) {
        ManagedChannel channel = channelManager.createChannel(service);
        ToolInvokerGrpc.ToolInvokerFutureStub stub =
            ToolInvokerGrpc.newFutureStub(channel);
        return Uni.createFrom().future(stub.invokeTool(request))
            .map(toolTransformer::transformReply);
    }

    @Override
    public Uni<List<ResourceContents>> acquireResource(
            ResourceRequest request, ServiceTarget service,
            ResourceManager.ResourceArguments arguments,
            ResourceReference mcpResource) {
        ManagedChannel channel = channelManager.createChannel(service);
        ResourceAcquirerGrpc.ResourceAcquirerFutureStub stub =
            ResourceAcquirerGrpc.newFutureStub(channel);
        return Uni.createFrom().future(stub.resourceAcquire(request))
            .map(reply -> resourceTransformer.transformReply(reply, arguments, mcpResource));
    }

    @Override
    public Iterator<CodeExecutionReply> executeCode(
            CodeExecutionRequest request, ServiceTarget service) {
        ManagedChannel channel = channelManager.createChannel(service);
        CodeExecutorGrpc.CodeExecutorBlockingStub stub =
            CodeExecutorGrpc.newBlockingStub(channel);
        return stub.executeCode(request);
    }

    @Override
    public HealthProbeReply probeHealth(
            HealthProbeRequest request, ServiceTarget service) {
        ManagedChannel channel = channelManager.createChannel(service);
        HealthProbeGrpc.HealthProbeBlockingStub stub =
            HealthProbeGrpc.newBlockingStub(channel);
        return stub.probe(request);
    }
}
```

### Response Transformers

**Package:** `ai.wanaku.backend.bridge` and `ai.wanaku.backend.bridge.transports.grpc`

Response transformers convert transport-specific types into MCP domain types within the transport layer, ensuring bridges never handle protocol-specific types.

```java
// Generic interface for tool response transformation
public interface ToolResponseTransformer<T> {
    ToolResponse transformReply(T reply);
}

// Generic interface for resource response transformation
public interface ResourceResponseTransformer<T> {
    List<ResourceContents> transformReply(
        T reply,
        ResourceManager.ResourceArguments arguments,
        ResourceReference mcpResource
    );
}

// gRPC-specific implementations
class GrpcToolResponseTransformer implements ToolResponseTransformer<ToolInvokeReply> {
    @Override
    public ToolResponse transformReply(ToolInvokeReply reply) {
        // Converts protobuf ProtocolStringList to List<TextContent>
    }
}

class GrpcResourceResponseTransformer implements ResourceResponseTransformer<ResourceReply> {
    @Override
    public List<ResourceContents> transformReply(
            ResourceReply reply,
            ResourceManager.ResourceArguments arguments,
            ResourceReference mcpResource) {
        // Builds TextResourceContents with proper MIME type and URI
    }
}
```

---

## Bridge Implementations

### InvokerBridge

**Package:** `ai.wanaku.backend.bridge`

Proxy for tool invocation services. Uses an async-first design with `Uni<ToolResponse>`.

```java
public class InvokerBridge implements ToolsBridge {
    private static final String SERVICE_TYPE_TOOL_INVOKER = "tool-invoker";

    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;
    private final EventNotifier eventNotifier;

    public InvokerBridge(
            ServiceResolver serviceResolver,
            WanakuBridgeTransport transport,
            EventNotifier eventNotifier) {
        this.serviceResolver = serviceResolver;
        this.transport = transport;
        this.eventNotifier = eventNotifier;
    }

    @Override
    public Uni<ToolResponse> execute(
            ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
        // 1. Resolve service (try TOOL_INVOKER, fallback to CODE_EXECUTION_ENGINE)
        // 2. Build ToolInvokeRequest via InvokerToolExecutor.buildToolInvokeRequest()
        // 3. Emit started event via EventNotifier
        // 4. Delegate to transport.invokeTool(request, service)
        // 5. Emit completed/failed event on response/failure
    }
}
```

### InvokerToolExecutor

**Package:** `ai.wanaku.backend.bridge`

Static utility class providing helper methods for building tool invocation requests. Used by `InvokerBridge` to construct `ToolInvokeRequest` objects from tool references and arguments.

```java
public final class InvokerToolExecutor {
    private InvokerToolExecutor() {}

    static ToolInvokeRequest buildToolInvokeRequest(
            ToolReference toolReference,
            ToolManager.ToolArguments toolArguments) {

        // Filter out metadata args before converting to string map
        Map<String, Object> filteredArgs = filterOutMetadataArgs(toolArguments.args());
        Map<String, String> argumentsMap = CollectionsHelper.toStringStringMap(filteredArgs);

        // Extract metadata headers from args (with prefix stripped)
        Map<String, String> metadataHeaders = extractMetadataHeaders(toolArguments);

        // Extract tool-defined headers from schema
        Map<String, String> toolDefinedHeaders = extractHeaders(toolReference, toolArguments);

        // Merge headers: metadata first, then tool-defined (tool-defined wins on conflict)
        Map<String, String> headers = new HashMap<>(metadataHeaders);
        headers.putAll(toolDefinedHeaders);

        String body = extractBody(toolReference, toolArguments);

        return ToolInvokeRequest.newBuilder()
            .setBody(body)
            .setUri(toolReference.getUri())
            .setConfigurationUri(
                Objects.requireNonNullElse(
                    toolReference.getConfigurationURI(), ""))
            .setSecretsUri(
                Objects.requireNonNullElse(
                    toolReference.getSecretsURI(), ""))
            .putAllHeaders(headers)
            .putAllArguments(argumentsMap)
            .build();
    }

    static Map<String, String> extractHeaders(
            ToolReference toolReference,
            ToolManager.ToolArguments toolArguments) {
        // Filter properties where target == "header" and scope == "service"
    }

    static Map<String, String> extractMetadataHeaders(
            ToolManager.ToolArguments toolArguments) {
        // Extract args with "wanaku_meta_" prefix, strip prefix for header name
    }

    static Map<String, Object> filterOutMetadataArgs(Map<String, Object> args) {
        // Exclude args with "wanaku_meta_" prefix from the arguments map
    }
}
```

### EventNotifier

**Package:** `ai.wanaku.backend.bridge`

Wraps a `MutinyEmitter<ToolCallEvent>` to emit tool call lifecycle events for observability.

```java
public class EventNotifier {
    public EventNotifier(MutinyEmitter<ToolCallEvent> emitter);

    public ToolCallEvent emitStartedEvent(
        ToolManager.ToolArguments toolArguments,
        ToolReference toolReference,
        ServiceTarget service,
        ToolInvokeRequest request);

    public void emitCompletedEvent(String eventId, String content, long duration);

    public void emitFailedEvent(
        String eventId, ToolCallEvent.ErrorCategory category,
        String errorMessage, long duration);

    public ToolCallEvent.ErrorCategory categorizeException(Exception e);
}
```

**Exception Categorization:**

| Exception Type | Category |
|----------------|----------|
| `StatusRuntimeException` | SERVICE_UNAVAILABLE |
| `ServiceUnavailableException` | SERVICE_UNAVAILABLE |
| `NullPointerException` | TOOL_DEFINITION_ERROR |
| `IllegalArgumentException` | INVALID_ARGUMENTS |
| Other | UNKNOWN |

### ResourceAcquirerBridge

**Package:** `ai.wanaku.backend.bridge`

Proxy for resource acquisition services. Uses `ProvisionerBridge` for service resolution and the async transport for resource reading.

```java
public class ResourceAcquirerBridge implements ResourceBridge {
    private static final String SERVICE_TYPE_RESOURCE_PROVIDER = "resource-provider";

    private final ProvisionerBridge provisionerBridge;
    private final WanakuBridgeTransport transport;

    public ResourceAcquirerBridge(
            ProvisionerBridge provisionerBridge,
            WanakuBridgeTransport transport) {
        this.provisionerBridge = provisionerBridge;
        this.transport = transport;
    }

    @Override
    public Uni<ResourceResponse> read(
            ResourceManager.ResourceArguments arguments,
            ResourceReference mcpResource) {

        ServiceTarget service = provisionerBridge.resolveService(
            mcpResource.getType(), SERVICE_TYPE_RESOURCE_PROVIDER);

        ResourceRequest request = buildResourceRequest(mcpResource);
        return transport.acquireResource(request, service, arguments, mcpResource)
            .map(ResourceResponse::new);
    }

    private ResourceRequest buildResourceRequest(ResourceReference mcpResource) {
        return ResourceRequest.newBuilder()
            .setLocation(mcpResource.getLocation())
            .setType(mcpResource.getType())
            .setName(mcpResource.getName())
            .setConfigurationURI(
                Objects.requireNonNullElse(
                    mcpResource.getConfigurationURI(), ""))
            .setSecretsURI(
                Objects.requireNonNullElse(
                    mcpResource.getSecretsURI(), ""))
            .build();
    }
}
```

### ProvisionerBridge

**Package:** `ai.wanaku.backend.bridge`

Consolidates provisioning logic and service resolution shared between `InvokerBridge` and `ResourceAcquirerBridge`.

```java
public class ProvisionerBridge implements ProvisionBridge {
    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;

    public ProvisionerBridge(ServiceResolver serviceResolver,
                             WanakuBridgeTransport transport) {
        this.serviceResolver = serviceResolver;
        this.transport = transport;
    }

    @Override
    public ProvisioningReference provision(
            String name, String configData, String secretsData,
            ServiceTarget service) {
        return transport.provision(name, configData, secretsData, service);
    }

    public ServiceTarget resolveService(String type, String serviceType) {
        ServiceTarget service = serviceResolver.resolve(type, serviceType);
        if (service == null) {
            throw new ServiceNotFoundException(
                "There is no host registered for service " + type);
        }
        return service;
    }
}
```

### CodeExecutionBridge

**Package:** `ai.wanaku.backend.bridge`

Proxy for code execution services with streaming support.

```java
public class CodeExecutionBridge implements CodeExecutorBridge {
    private static final String SERVICE_TYPE_CODE_EXECUTION = "code-execution-engine";

    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;
    private final MutinyEmitter<ToolCallEvent> toolCallEventEmitter;

    @Override
    public Iterator<CodeExecutionReply> executeCode(
            String engineType,
            String language,
            CodeExecutionRequest request) {

        ServiceTarget service = resolveService(engineType, language);

        ai.wanaku.core.exchange.CodeExecutionRequest grpcRequest =
            buildGrpcRequest(engineType, language, request);

        ToolCallEvent startedEvent = emitStartedEvent(engineType, language, request);
        Instant startTime = Instant.now();

        try {
            Iterator<CodeExecutionReply> iterator =
                transport.executeCode(grpcRequest, service);

            return new CloseableCodeExecutionIterator(
                iterator, startedEvent, startTime, this);

        } catch (Exception e) {
            emitCompletionEvent(startedEvent, startTime, true, e.getMessage());
            throw e;
        }
    }

    private ServiceTarget resolveService(String engineType, String language) {
        ServiceTarget service = serviceResolver.resolveCodeExecution(
            SERVICE_TYPE_CODE_EXECUTION, engineType, language);

        if (service == null) {
            throw new ServiceNotFoundException(
                SERVICE_TYPE_CODE_EXECUTION, engineType + "/" + language);
        }
        return service;
    }

    private ai.wanaku.core.exchange.CodeExecutionRequest buildGrpcRequest(
            String engineType,
            String language,
            CodeExecutionRequest request) {

        String uri = String.format("code-execution-engine://%s/%s",
            engineType, language);

        String decodedCode = decodeBase64Code(request.getCode());

        // Convert List<String> arguments to Map with indexed keys
        Map<String, String> argMap = new HashMap<>();
        List<String> args = request.getArguments();
        for (int i = 0; i < args.size(); i++) {
            argMap.put("arg" + i, args.get(i));
        }

        return ai.wanaku.core.exchange.CodeExecutionRequest.newBuilder()
            .setUri(uri)
            .setCode(decodedCode)
            .putAllArguments(argMap)
            .putAllEnvironment(request.getEnvironment())
            .setTimeout(request.getTimeout())
            .build();
    }

    private String decodeBase64Code(String base64Code) {
        if (base64Code == null || base64Code.isEmpty()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(base64Code));
        } catch (IllegalArgumentException e) {
            return base64Code;  // Return raw if not valid base64
        }
    }
}
```

### CloseableCodeExecutionIterator

Wraps streaming iterator to ensure terminal events are emitted.

```java
public static class CloseableCodeExecutionIterator
        implements Iterator<CodeExecutionReply>, Closeable {

    private final Iterator<CodeExecutionReply> delegate;
    private final ToolCallEvent startedEvent;
    private final Instant startTime;
    private final CodeExecutionBridge bridge;
    private boolean completed = false;
    private boolean hasError = false;
    private String lastErrorMessage = null;

    @Override
    public boolean hasNext() {
        try {
            boolean hasMore = delegate.hasNext();
            if (!hasMore && !completed) {
                bridge.emitCompletionEvent(
                    startedEvent, startTime, hasError, lastErrorMessage);
                completed = true;
            }
            return hasMore;
        } catch (Exception e) {
            hasError = true;
            lastErrorMessage = e.getMessage();
            if (!completed) {
                bridge.emitCompletionEvent(
                    startedEvent, startTime, true, e.getMessage());
                completed = true;
            }
            throw e;
        }
    }

    @Override
    public CodeExecutionReply next() {
        try {
            CodeExecutionReply reply = delegate.next();
            if (reply.getIsError()) {
                hasError = true;
                lastErrorMessage = reply.getContent(0);
            }
            return reply;
        } catch (Exception e) {
            hasError = true;
            lastErrorMessage = e.getMessage();
            if (!completed) {
                bridge.emitCompletionEvent(
                    startedEvent, startTime, true, e.getMessage());
                completed = true;
            }
            throw e;
        }
    }

    @Override
    public void close() {
        if (!completed) {
            bridge.emitCompletionEvent(
                startedEvent, startTime, hasError, lastErrorMessage);
            completed = true;
        }
    }
}
```

---

## gRPC Protocol Definitions

### Provisioner Service

**File:** `provision.proto`

```protobuf
service Provisioner {
  rpc Provision (ProvisionRequest) returns (ProvisionReply) {}
}

message Configuration {
  PayloadType type = 1;   // REFERENCE or BUILTIN
  string name = 2;
  string payload = 3;
}

message Secret {
  PayloadType type = 1;
  string name = 2;
  string payload = 3;
}

message ProvisionRequest {
  string uri = 1;
  Configuration configuration = 2;
  Secret secret = 3;
}

message ProvisionReply {
  string configurationUri = 1;
  string secretUri = 2;
  map<string, PropertySchema> properties = 3;
}
```

### ToolInvoker Service

**File:** `toolrequest.proto`

```protobuf
service ToolInvoker {
  rpc InvokeTool (ToolInvokeRequest) returns (ToolInvokeReply) {}
}

message ToolInvokeRequest {
  string uri = 1;
  string body = 2;
  map<string, string> arguments = 3;
  string configurationURI = 4;
  string secretsURI = 5;
  map<string, string> headers = 6;
}

message ToolInvokeReply {
  bool isError = 1;
  repeated string content = 2;
}
```

### ResourceAcquirer Service

**File:** `resourcerequest.proto`

```protobuf
service ResourceAcquirer {
  rpc ResourceAcquire (ResourceRequest) returns (ResourceReply) {}
}

message ResourceRequest {
  string location = 1;
  string type = 2;
  string name = 3;
  map<string, string> params = 4;
  string configurationURI = 5;
  string secretsURI = 6;
}

message ResourceReply {
  bool isError = 1;
  repeated string content = 2;
}
```

### CodeExecutor Service

**File:** `codeexecution.proto`

```protobuf
service CodeExecutor {
  rpc ExecuteCode (CodeExecutionRequest) returns (stream CodeExecutionReply) {}
}

message CodeExecutionRequest {
  string uri = 1;
  string body = 2;
  string code = 3;
  map<string, string> arguments = 4;
  string configurationURI = 5;
  string secretsURI = 6;
  int64 timeout = 7;
  map<string, string> environment = 8;
}

message CodeExecutionReply {
  bool isError = 1;
  repeated string content = 2;
  OutputType outputType = 3;
  ExecutionStatus status = 4;
  int32 exitCode = 5;
  int64 timestamp = 6;
}

enum OutputType {
  STDOUT = 0;
  STDERR = 1;
  STATUS = 2;
  COMPLETION = 3;
}

enum ExecutionStatus {
  PENDING = 0;
  RUNNING = 1;
  COMPLETED = 2;
  FAILED = 3;
  CANCELLED = 4;
  TIMEOUT = 5;
}
```

---

## Request/Response Flows

### Tool Invocation Flow (Async)

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Client Request                                               │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. InvokerBridge.execute(ToolArguments, CallableReference)      │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. ServiceResolver.resolve(type, "tool-invoker")                │
│    └─ Fallback: resolve(type, "code-execution-engine")          │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. buildToolInvokeRequest()                                     │
│    ├─ extractHeaders(): target="header", scope="service"        │
│    ├─ extractBody(): BODY argument if defined                   │
│    └─ Convert arguments to Map<String, String>                  │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. GrpcTransport.invokeTool(request, service)                   │
│    ├─ GrpcChannelManager.createChannel(ServiceTarget) [cached]  │
│    ├─ ToolInvokerGrpc.newFutureStub(channel)                    │
│    ├─ stub.invokeTool(request)                                  │
│    └─ GrpcToolResponseTransformer.transformReply()              │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. Return Uni<ToolResponse>                                     │
└─────────────────────────────────────────────────────────────────┘
```

### Resource Acquisition Flow (Async)

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. ResourceAcquirerBridge.read(ResourceArguments, Ref)          │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. ProvisionerBridge.resolveService(type, "resource-provider")  │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. buildResourceRequest()                                       │
│    ├─ location: from ResourceReference                          │
│    ├─ type: from ResourceReference                              │
│    ├─ configurationURI: from ResourceReference (default "")     │
│    └─ secretsURI: from ResourceReference (default "")           │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. GrpcTransport.acquireResource(request, service, ...)         │
│    ├─ GrpcChannelManager.createChannel(ServiceTarget) [cached]  │
│    ├─ ResourceAcquirerGrpc.newFutureStub(channel)               │
│    ├─ stub.resourceAcquire(request)                             │
│    └─ GrpcResourceResponseTransformer.transformReply()          │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. Return Uni<ResourceResponse>                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Code Execution Flow (Streaming)

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. CodeExecutionBridge.executeCode(engineType, language, req)   │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. ServiceResolver.resolveCodeExecution(                        │
│       "code-execution-engine", engineType, language)            │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. buildGrpcRequest()                                           │
│    ├─ uri: "code-execution-engine://{engineType}/{language}"    │
│    ├─ code: Base64 decode from request                          │
│    ├─ arguments: List → Map (arg0, arg1, ...)                   │
│    └─ environment: from request                                 │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. Emit STARTED event                                           │
│    ├─ Redact code: "[CODE]"                                     │
│    └─ Redact arguments: emit count only                         │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. GrpcTransport.executeCode(request, service)                  │
│    ├─ GrpcChannelManager.createChannel(ServiceTarget)           │
│    ├─ CodeExecutorGrpc.newBlockingStub(channel)                 │
│    └─ stub.executeCode(request) → Iterator                      │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. Wrap in CloseableCodeExecutionIterator                       │
│    ├─ Tracks completion state                                   │
│    └─ Ensures terminal event on close/completion                │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 7. Stream responses to client                                   │
│    └─ Emit COMPLETED/FAILED when iterator exhausted or closed   │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 8. Return Iterator<CodeExecutionReply>                          │
└─────────────────────────────────────────────────────────────────┘
```

### Provisioning Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. ProvisionerBridge.provision(name, configData, secretsData,   │
│                                service)                         │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. GrpcTransport.provision(name, configData, secretsData, svc)  │
│    ├─ Build Configuration protobuf                              │
│    ├─ Build Secret protobuf                                     │
│    ├─ Build ProvisionRequest                                    │
│    ├─ ProvisionerGrpc.ProvisionerBlockingStub                   │
│    └─ stub.provision(request) → ProvisionReply                  │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. Return ProvisioningReference(configUri, secretUri, props)    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Design Patterns

### Transport Abstraction Pattern

```java
// Interface defines protocol-independent contract with async-first design
interface WanakuBridgeTransport {
    Uni<ToolResponse> invokeTool(ToolInvokeRequest request, ServiceTarget service);
}

// gRPC implementation with response transformation
class GrpcTransport implements WanakuBridgeTransport {
    private final ToolResponseTransformer<ToolInvokeReply> transformer = ...;

    public Uni<ToolResponse> invokeTool(ToolInvokeRequest request, ServiceTarget service) {
        // gRPC FutureStub → Uni with transformer applied
    }
}

// HTTP implementation (future)
class HttpTransport implements WanakuBridgeTransport {
    public Uni<ToolResponse> invokeTool(ToolInvokeRequest request, ServiceTarget service) {
        // HTTP-specific logic with its own transformer
    }
}
```

### Composition Over Inheritance

```java
// InvokerBridge delegates to transport for async execution
class InvokerBridge implements ToolsBridge {
    private final ServiceResolver serviceResolver;      // Service discovery
    private final WanakuBridgeTransport transport;     // Protocol handling
}

// ResourceAcquirerBridge delegates to ProvisionerBridge and transport
class ResourceAcquirerBridge implements ResourceBridge {
    private final ProvisionerBridge provisionerBridge;  // Shared provisioning + resolution
    private final WanakuBridgeTransport transport;     // Protocol handling
}

// Each component has single responsibility
```

### Service Locator Pattern

```java
// ServiceResolver discovers services dynamically
ServiceTarget service = serviceResolver.resolve("my-tool", "tool-invoker");

// Bridges don't need hardcoded addresses
```

### Iterator Wrapper Pattern

```java
// Ensure cleanup even if iteration abandoned
try (CloseableCodeExecutionIterator iter = bridge.executeCode(...)) {
    while (iter.hasNext()) {
        CodeExecutionReply reply = iter.next();
        // Process reply
    }
}  // Terminal event emitted on close
```

---

## Security Features

| Feature | Implementation |
|---------|----------------|
| Argument Redaction | Events omit actual argument values |
| Code Redaction | Code replaced with "[CODE]" in events |
| Error Sanitization | Safe error messages in responses |
| Plaintext (dev) | Default configuration for development |
| TLS Ready | Channel manager designed for future SSL/TLS |

---

## File Locations

| Component | Path |
|-----------|------|
| Bridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ToolsBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ResourceBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| CodeExecutorBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ProvisionBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| McpBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| WanakuBridgeTransport.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| InvokerBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| InvokerToolExecutor.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| EventNotifier.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ResourceAcquirerBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| CodeExecutionBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ProvisionerBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| DefaultMcpBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ForwardClient.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ForwardRegistry.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ToolResponseTransformer.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ResourceResponseTransformer.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| GrpcTransport.java | `wanaku-router/wanaku-router-backend/.../bridge/transports/grpc/` |
| GrpcChannelManager.java | `wanaku-router/wanaku-router-backend/.../bridge/transports/grpc/` |
| GrpcToolResponseTransformer.java | `wanaku-router/wanaku-router-backend/.../bridge/transports/grpc/` |
| GrpcResourceResponseTransformer.java | `wanaku-router/wanaku-router-backend/.../bridge/transports/grpc/` |
| provision.proto | `core/core-exchange/src/main/proto/` |
| toolrequest.proto | `core/core-exchange/src/main/proto/` |
| resourcerequest.proto | `core/core-exchange/src/main/proto/` |
| codeexecution.proto | `core/core-exchange/src/main/proto/` |
