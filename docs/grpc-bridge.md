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
├──────────┼────────────────┼────────────────────┼────────────────┤
│          │                │                    │                │
│   ┌──────▼──────┐  ┌──────▼───────────┐  ┌─────▼──────────┐    │
│   │InvokerBridge│  │ResourceAcquirer- │  │CodeExecution-  │    │
│   │             │  │Bridge            │  │Bridge          │    │
│   └──────┬──────┘  └──────┬───────────┘  └─────┬──────────┘    │
├──────────┼────────────────┼────────────────────┼────────────────┤
│                    Transport Abstraction                        │
│                  WanakuBridgeTransport                          │
├─────────────────────────────────────────────────────────────────┤
│                    gRPC Implementation                          │
│   ┌─────────────────┐      ┌────────────────────┐              │
│   │  GrpcTransport  │──────│ GrpcChannelManager │              │
│   └────────┬────────┘      └────────────────────┘              │
│            │                                                    │
│   ┌────────▼────────┐                                          │
│   │ProvisioningService│                                         │
│   └─────────────────┘                                          │
└─────────────────────────────────────────────────────────────────┘
```

Key characteristics:
- Protocol independence via transport abstraction
- Service discovery integration for dynamic endpoint resolution
- Event emission for observability
- Streaming support for code execution
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

    ToolInvokeReply invokeTool(
        ToolInvokeRequest request,
        ServiceTarget service
    );

    ResourceReply acquireResource(
        ResourceRequest request,
        ServiceTarget service
    );

    Iterator<CodeExecutionReply> executeCode(
        CodeExecutionRequest request,
        ServiceTarget service
    );
}
```

This abstraction enables:
- Protocol independence (supports gRPC, HTTP, WebSocket)
- Testability (easy mocking)
- Separation of concerns

---

## Bridge Interfaces

### ToolsBridge

Contract for tool proxy implementations.

```java
public interface ToolsBridge extends Bridge {
    ProvisioningReference provision(ToolPayload payload);
    ToolExecutor getExecutor();
}
```

### ResourceBridge

Contract for resource proxy implementations.

```java
public interface ResourceBridge extends Bridge {
    ProvisioningReference provision(ResourcePayload payload);
    List<ResourceContents> eval(
        ResourceManager.ResourceArguments arguments,
        ResourceReference mcpResource
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

    public ManagedChannel createChannel(ServiceTarget service) {
        return ManagedChannelBuilder
            .forTarget(service.toAddress())
            .usePlaintext()
            .build();
    }

    public void closeChannel(ManagedChannel channel) {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
}
```

**Characteristics:**
- Creates plaintext gRPC channels
- Robust error handling during shutdown
- Design allows for future SSL/TLS support

### GrpcTransport

**Package:** `ai.wanaku.backend.bridge.transports.grpc`

Central coordinator for all gRPC transport operations.

```java
public class GrpcTransport implements WanakuBridgeTransport {
    private final GrpcChannelManager channelManager;
    private final ProvisioningService provisioningService;

    public GrpcTransport() {
        this.channelManager = new GrpcChannelManager();
        this.provisioningService = new ProvisioningService();
    }

    @Override
    public ProvisioningReference provision(
            String name, String configData, String secretsData,
            ServiceTarget service) {
        // Build Configuration and Secret protobuf objects
        // Delegate to ProvisioningService
    }

    @Override
    public ToolInvokeReply invokeTool(
            ToolInvokeRequest request, ServiceTarget service) {
        ManagedChannel channel = channelManager.createChannel(service);
        try {
            ToolInvokerGrpc.ToolInvokerBlockingStub stub =
                ToolInvokerGrpc.newBlockingStub(channel);
            return stub.invokeTool(request);
        } catch (Exception e) {
            throw new ServiceUnavailableException(e);
        }
    }

    @Override
    public ResourceReply acquireResource(
            ResourceRequest request, ServiceTarget service) {
        ManagedChannel channel = channelManager.createChannel(service);
        try {
            ResourceAcquirerGrpc.ResourceAcquirerBlockingStub stub =
                ResourceAcquirerGrpc.newBlockingStub(channel);
            return stub.resourceAcquire(request);
        } catch (Exception e) {
            throw new ServiceUnavailableException(e);
        }
    }

    @Override
    public Iterator<CodeExecutionReply> executeCode(
            CodeExecutionRequest request, ServiceTarget service) {
        ManagedChannel channel = channelManager.createChannel(service);
        try {
            CodeExecutorGrpc.CodeExecutorBlockingStub stub =
                CodeExecutorGrpc.newBlockingStub(channel);
            return stub.executeCode(request);
        } catch (Exception e) {
            throw new ServiceUnavailableException(e);
        }
    }
}
```

### ProvisioningService

**Package:** `ai.wanaku.backend.bridge`

Encapsulates provisioning logic for all bridge types.

```java
public class ProvisioningService {

    public ProvisioningReference provision(
            Configuration cfg,
            Secret secret,
            ManagedChannel channel,
            ServiceTarget service) {

        ProvisionRequest request = ProvisionRequest.newBuilder()
            .setUri(service.toAddress())
            .setConfiguration(cfg)
            .setSecret(secret)
            .build();

        ProvisionerGrpc.ProvisionerBlockingStub stub =
            ProvisionerGrpc.newBlockingStub(channel);

        ProvisionReply reply = stub.provision(request);

        return new ProvisioningReference(
            reply.getConfigurationUri(),
            reply.getSecretUri(),
            reply.getPropertiesMap()
        );
    }
}
```

---

## Bridge Implementations

### InvokerBridge

**Package:** `ai.wanaku.backend.bridge`

Proxy for tool invocation services.

```java
public class InvokerBridge implements ToolsBridge {
    private static final String SERVICE_TYPE_TOOL_INVOKER = "tool-invoker";

    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;
    private final ToolExecutor executor;

    public InvokerBridge(
            ServiceResolver serviceResolver,
            WanakuBridgeTransport transport,
            MutinyEmitter<ToolCallEvent> toolCallEventEmitter) {
        this.serviceResolver = serviceResolver;
        this.transport = transport;
        this.executor = new InvokerToolExecutor(
            serviceResolver, transport, toolCallEventEmitter);
    }

    @Override
    public ToolExecutor getExecutor() {
        return executor;
    }

    @Override
    public ProvisioningReference provision(ToolPayload toolPayload) {
        ToolReference ref = toolPayload.getToolReference();
        ServiceTarget service = resolveService(
            ref.getType(), SERVICE_TYPE_TOOL_INVOKER);
        return transport.provision(
            ref.getName(),
            toolPayload.getConfigData(),
            toolPayload.getSecretsData(),
            service
        );
    }

    private ServiceTarget resolveService(String type, String serviceType) {
        ServiceTarget service = serviceResolver.resolve(type, serviceType);
        if (service == null) {
            throw new ServiceNotFoundException(type, serviceType);
        }
        return service;
    }
}
```

### InvokerToolExecutor

**Package:** `ai.wanaku.backend.bridge`

Handles actual tool execution by delegating to remote services.

```java
public class InvokerToolExecutor implements ToolExecutor {
    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;
    private final MutinyEmitter<ToolCallEvent> toolCallEventEmitter;

    @Override
    public ToolResponse execute(
            ToolManager.ToolArguments toolArguments,
            CallableReference toolReference) {

        if (!(toolReference instanceof ToolReference)) {
            throw new UnsupportedOperationException();
        }

        return executeToolReference(toolArguments, (ToolReference) toolReference);
    }

    private ToolResponse executeToolReference(
            ToolManager.ToolArguments toolArguments,
            ToolReference toolReference) {

        // 1. Resolve service (try TOOL_INVOKER, fallback to CODE_EXECUTION_ENGINE)
        ServiceTarget service = resolveServiceWithFallback(toolReference);

        // 2. Build request
        ToolInvokeRequest request = buildToolInvokeRequest(
            toolReference, toolArguments);

        // 3. Emit STARTED event
        ToolCallEvent startedEvent = emitStartedEvent(toolReference, toolArguments);
        long startTime = System.currentTimeMillis();

        try {
            // 4. Invoke tool
            ToolInvokeReply reply = transport.invokeTool(request, service);

            // 5. Emit completion event
            long duration = System.currentTimeMillis() - startTime;
            if (reply.getIsError()) {
                emitFailedEvent(startedEvent.getId(),
                    categorizeToolError(reply.getContent(0)),
                    reply.getContent(0), duration);
            } else {
                emitCompletedEvent(startedEvent.getId(),
                    String.join("\n", reply.getContentList()), duration);
            }

            // 6. Process reply
            return processToolInvokeReply(reply);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            emitFailedEvent(startedEvent.getId(),
                categorizeException(e), e.getMessage(), duration);
            throw e;
        }
    }

    private ToolInvokeRequest buildToolInvokeRequest(
            ToolReference toolReference,
            ToolManager.ToolArguments toolArguments) {

        return ToolInvokeRequest.newBuilder()
            .setUri(toolReference.getUri())
            .setBody(extractBody(toolReference, toolArguments))
            .putAllArguments(toolArguments.asMap())
            .putAllHeaders(extractHeaders(toolReference, toolArguments))
            .setConfigurationURI(
                Objects.requireNonNullElse(
                    toolReference.getConfigurationURI(), ""))
            .setSecretsURI(
                Objects.requireNonNullElse(
                    toolReference.getSecretsURI(), ""))
            .build();
    }

    static Map<String, String> extractHeaders(
            ToolReference toolReference,
            ToolManager.ToolArguments toolArguments) {
        // Filter properties where target == "header" and scope == "service"
        return toolReference.getProperties().stream()
            .filter(p -> "header".equals(p.getTarget())
                      && "service".equals(p.getScope()))
            .collect(Collectors.toMap(
                PropertySchema::getName,
                p -> toolArguments.get(p.getName())
            ));
    }
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

Proxy for resource acquisition services.

```java
public class ResourceAcquirerBridge implements ResourceBridge {
    private static final String SERVICE_TYPE_RESOURCE_PROVIDER = "resource-provider";

    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;

    @Override
    public List<ResourceContents> eval(
            ResourceManager.ResourceArguments arguments,
            ResourceReference mcpResource) {

        ServiceTarget service = serviceResolver.resolve(
            mcpResource.getType(), SERVICE_TYPE_RESOURCE_PROVIDER);

        ResourceRequest request = buildResourceRequest(mcpResource);
        ResourceReply reply = transport.acquireResource(request, service);

        return processReply(reply, arguments, mcpResource);
    }

    @Override
    public ProvisioningReference provision(ResourcePayload payload) {
        ResourceReference ref = payload.getResourceReference();
        ServiceTarget service = serviceResolver.resolve(
            ref.getType(), SERVICE_TYPE_RESOURCE_PROVIDER);
        return transport.provision(
            ref.getName(),
            payload.getConfigData(),
            payload.getSecretsData(),
            service
        );
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

    private List<ResourceContents> processReply(
            ResourceReply reply,
            ResourceManager.ResourceArguments arguments,
            ResourceReference mcpResource) {

        if (reply.getIsError()) {
            return List.of(new TextResourceContents(
                mcpResource.getUri(),
                mcpResource.getMimeType(),
                reply.getContent(0)
            ));
        }

        return reply.getContentList().stream()
            .map(content -> new TextResourceContents(
                mcpResource.getUri(),
                mcpResource.getMimeType(),
                content
            ))
            .toList();
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

### Tool Invocation Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Client Request                                               │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. InvokerBridge.getExecutor()                                  │
│    → Returns InvokerToolExecutor                                │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. InvokerToolExecutor.execute(ToolArguments, ToolReference)    │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. ServiceResolver.resolve(type, "tool-invoker")                │
│    └─ Fallback: resolve(type, "code-execution-engine")          │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. buildToolInvokeRequest()                                     │
│    ├─ extractHeaders(): target="header", scope="service"        │
│    ├─ extractBody(): BODY argument if defined                   │
│    └─ Convert arguments to Map<String, String>                  │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. Emit STARTED event (redacted sensitive data)                 │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 7. GrpcTransport.invokeTool(request, service)                   │
│    ├─ GrpcChannelManager.createChannel(ServiceTarget)           │
│    ├─ ToolInvokerGrpc.newBlockingStub(channel)                  │
│    └─ stub.invokeTool(request)                                  │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 8. Process ToolInvokeReply                                      │
│    ├─ isError=true  → Emit FAILED event                         │
│    └─ isError=false → Emit COMPLETED event                      │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 9. Return ToolResponse                                          │
└─────────────────────────────────────────────────────────────────┘
```

### Resource Acquisition Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. ResourceAcquirerBridge.eval(ResourceArguments, ResourceRef)  │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. ServiceResolver.resolve(type, "resource-provider")           │
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
│ 4. GrpcTransport.acquireResource(request, service)              │
│    ├─ GrpcChannelManager.createChannel(ServiceTarget)           │
│    ├─ ResourceAcquirerGrpc.newBlockingStub(channel)             │
│    └─ stub.resourceAcquire(request)                             │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. processReply()                                               │
│    ├─ isError=true  → TextResourceContents with error message   │
│    └─ isError=false → TextResourceContents for each content     │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. Return List<ResourceContents>                                │
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
│ 1. InvokerBridge.provision(ToolPayload) or                      │
│    ResourceAcquirerBridge.provision(ResourcePayload)            │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. ServiceResolver.resolve(type, serviceType)                   │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. GrpcTransport.provision(name, configData, secretsData, svc)  │
│    ├─ Build Configuration protobuf                              │
│    └─ Build Secret protobuf                                     │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. ProvisioningService.provision(cfg, secret, channel, service) │
│    ├─ Build ProvisionRequest                                    │
│    ├─ ProvisionerGrpc.ProvisionerBlockingStub                   │
│    └─ stub.provision(request) → ProvisionReply                  │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. Return ProvisioningReference(configUri, secretUri, props)    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Design Patterns

### Transport Abstraction Pattern

```java
// Interface defines protocol-independent contract
interface WanakuBridgeTransport {
    ToolInvokeReply invokeTool(ToolInvokeRequest request, ServiceTarget service);
}

// gRPC implementation
class GrpcTransport implements WanakuBridgeTransport {
    public ToolInvokeReply invokeTool(ToolInvokeRequest request, ServiceTarget service) {
        // gRPC-specific logic
    }
}

// HTTP implementation (future)
class HttpTransport implements WanakuBridgeTransport {
    public ToolInvokeReply invokeTool(ToolInvokeRequest request, ServiceTarget service) {
        // HTTP-specific logic
    }
}
```

### Composition Over Inheritance

```java
// InvokerBridge delegates to executor and transport
class InvokerBridge implements ToolsBridge {
    private final ServiceResolver serviceResolver;      // Service discovery
    private final WanakuBridgeTransport transport;     // Protocol handling
    private final ToolExecutor executor;               // Execution logic
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
| WanakuBridgeTransport.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| GrpcTransport.java | `wanaku-router/wanaku-router-backend/.../bridge/transports/grpc/` |
| GrpcChannelManager.java | `wanaku-router/wanaku-router-backend/.../bridge/transports/grpc/` |
| InvokerBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| InvokerToolExecutor.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ResourceAcquirerBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| CodeExecutionBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ProvisioningService.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ToolsBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| ResourceBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| CodeExecutorBridge.java | `wanaku-router/wanaku-router-backend/.../bridge/` |
| provision.proto | `core/core-exchange/src/main/proto/` |
| toolrequest.proto | `core/core-exchange/src/main/proto/` |
| resourcerequest.proto | `core/core-exchange/src/main/proto/` |
| codeexecution.proto | `core/core-exchange/src/main/proto/` |
