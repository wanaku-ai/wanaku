# Service Registration Architecture

This document describes the capability registration workflow for the Wanaku service discovery system. It is intended as a reference for implementing similar service discovery patterns.

## Architecture Overview

The service registration system uses a layered design:

```
┌─────────────────────────────────────────────────────────┐
│                    REST API Layer                       │
│              (DiscoveryResource.java)                   │
├─────────────────────────────────────────────────────────┤
│                    Bean Layer                           │
│               (DiscoveryBean.java)                      │
├─────────────────────────────────────────────────────────┤
│                  Registry Interface                     │
│             (ServiceRegistry.java)                      │
├─────────────────────────────────────────────────────────┤
│               Persistence Layer                         │
│         (InfinispanServiceRegistry.java)                │
│    ┌─────────────────────┬─────────────────────────┐    │
│    │CapabilitiesRepository│ ServiceRecordRepository│    │
│    └─────────────────────┴─────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

Key characteristics:
- Service targets represent downstream capability endpoints
- Reactive messaging distributes events to subscribers
- gRPC transport for service-to-service communication
- Dual-repository pattern separates service metadata from activity history

---

## Core Data Types

### ServiceTarget

Service endpoint descriptor stored in the registry.

**Package:** `ai.wanaku.capabilities.sdk.api.types.providers`

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Unique identifier (assigned by registry) |
| `serviceName` | `String` | Logical name of the service |
| `host` | `String` | Host address |
| `port` | `int` | Port number |
| `serviceType` | `String` | Type category (see ServiceType) |
| `serviceSubType` | `String` | Engine type: jvm, interpreted, camel |
| `languageName` | `String` | Language: java, python, yaml, xml |
| `languageType` | `String` | compiled, interpreted |
| `languageSubType` | `String` | jvm, cpython, etc. |

**Key Methods:**
```java
String toAddress()                    // Returns "host:port"
static ServiceTarget newEmptyTarget(String serviceName, String host,
    int port, String serviceType, String serviceSubType)
```

### ServiceType

Enumeration of service categories.

**Package:** `ai.wanaku.capabilities.sdk.api.types.providers`

| Value | String | Int |
|-------|--------|-----|
| `RESOURCE_PROVIDER` | "resource-provider" | 1 |
| `TOOL_INVOKER` | "tool-invoker" | 2 |
| `MULTI_CAPABILITY` | "multi-capability" | 3 |
| `CODE_EXECUTION_ENGINE` | "code-execution-engine" | 4 |

### ServiceState

Point-in-time health snapshot.

**Package:** `ai.wanaku.capabilities.sdk.api.types.discovery`

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | `Instant` | When state was recorded |
| `healthy` | `boolean` | Health status |
| `reason` | `String` | Message (used when unhealthy) |

**Factory Methods:**
```java
static ServiceState newHealthy()                    // Healthy with "HEALTHY" message
static ServiceState newUnhealthy(String reason)     // Unhealthy with reason
static ServiceState newMissingInAction()            // "MISSING_IN_ACTION"
static ServiceState newInactive()                   // "AUTO_DEREGISTRATION"
```

### ActivityRecord

Service lifecycle tracking with state history.

**Package:** `ai.wanaku.capabilities.sdk.api.types.discovery`

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Service identifier |
| `lastSeen` | `Instant` | Last activity timestamp |
| `active` | `boolean` | Current active status |
| `states` | `List<ServiceState>` | State history (circular buffer) |

---

## Discovery API Endpoints

**Base Path:** `/api/v1/management/discovery`

### POST /register

Register a service with the router. Returns the service target with assigned ID.

**Request:** `ServiceTarget` (JSON)
**Response:** `WanakuResponse<ServiceTarget>`

```java
RestResponse<WanakuResponse<ServiceTarget>> register(ServiceTarget serviceTarget);
```

### POST /deregister

Remove a service from the registry.

**Request:** `ServiceTarget` (JSON)
**Response:** 200 OK

```java
Response deregister(ServiceTarget serviceTarget);
```

### POST /update/{id}

Update service state (report health status).

**Path Parameter:** `id` - Service identifier
**Request:** `ServiceState` (JSON)
**Response:** 200 OK

```java
Response updateState(@PathParam("id") String id, ServiceState serviceState);
```

### POST /ping

Send heartbeat to indicate service is still active.

**Request:** Service ID as plain text
**Response:** 200 OK

```java
Response ping(String id);
```

---

## Registry Layer

### ServiceRegistry Interface

**Package:** `ai.wanaku.core.mcp.providers`

Defines the contract for service registration and discovery operations.

```java
public interface ServiceRegistry {
    // Registration
    ServiceTarget register(ServiceTarget serviceTarget);
    void deregister(ServiceTarget serviceTarget);
    void update(ServiceTarget serviceTarget);

    // Query
    List<ServiceTarget> getServiceByName(String serviceName, String serviceType);
    List<ServiceTarget> getCodeExecutionService(String serviceType,
        String serviceSubType, String serviceName);
    List<ServiceTarget> getEntries();
    List<ServiceTarget> getEntries(String serviceType);

    // State Management
    ActivityRecord getStates(String id);
    void updateLastState(String id, ServiceState state);
    void ping(String id);
}
```

### InfinispanServiceRegistry

**Package:** `ai.wanaku.core.persistence.infinispan.discovery`

Implementation using dual-repository pattern:
- `InfinispanCapabilitiesRepository` - stores `ServiceTarget` entities
- `InfinispanServiceRecordRepository` - stores `ActivityRecord` entities

**Key Behaviors:**

1. **Registration:** Delegates to capabilities repository, generates UUID for ID
2. **Multi-Capability Matching:** Queries include services with type "multi-capability"
3. **Circular Buffer:** States list capped at configurable max (default 10), removes oldest 50% when exceeded

```java
// Configuration
@ConfigProperty(name = "wanaku.persistence.infinispan.max-state-count",
    defaultValue = "10")
int maxStateCount;
```

### Repository Queries

**Capabilities Repository:**

```java
// Find by name and type (includes multi-capability)
findByService(serviceName, serviceType)
// WHERE serviceName = :serviceName
//   AND (serviceType = :serviceType OR serviceType = 'multi-capability')

// List by type (includes multi-capability)
listCapable(serviceType)
// WHERE serviceType = :serviceType OR serviceType = 'multi-capability'

// Find code execution engine (exact match)
findCodeExecutionService(serviceType, serviceSubType, languageName)
// WHERE serviceType = :serviceType
//   AND serviceSubType = :serviceSubType
//   AND languageName = :languageName
```

---

## Registration Workflow (Service Side)

### RegistrationManager Interface

**Package:** `ai.wanaku.capabilities.sdk.api.discovery`

Defines the contract for managing service lifecycle registration.

```java
public interface RegistrationManager {
    void register();
    void deregister();
    void ping();
    void lastAsFail(String reason);
    void lastAsSuccessful();
    void addCallBack(DiscoveryCallback callback);
}
```

### DefaultRegistrationManager

**Package:** `ai.wanaku.core.capabilities.discovery`

Quarkus CDI implementation with retry logic and persistent identity.

**Key Features:**
- Restores service ID from persistent storage on startup
- Uses `ReentrantLock` for thread-safe registration
- Retry logic with configurable wait time
- Callback pattern for lifecycle notifications

**Registration Flow:**

```
┌──────────────────┐
│ register() called│
└────────┬─────────┘
         │
         ▼
┌──────────────────┐     Yes    ┌─────────────┐
│ Already registered?├─────────►│ Call ping() │
└────────┬─────────┘            └─────────────┘
         │ No
         ▼
┌──────────────────┐
│ Acquire lock     │
│ (1s timeout)     │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ tryRegistering() │◄─────────┐
└────────┬─────────┘          │
         │                    │ Retry
         ▼                    │
┌──────────────────┐   Fail   │
│ Call REST API    ├──────────┘
└────────┬─────────┘
         │ Success
         ▼
┌──────────────────┐
│ Save ID to disk  │
│ Set registered   │
│ Invoke callbacks │
└──────────────────┘
```

### DiscoveryCallback Interface

**Package:** `ai.wanaku.capabilities.sdk.api.discovery`

Callback interface for receiving registration lifecycle events.

```java
public interface DiscoveryCallback {
    void onPing(RegistrationManager manager, ServiceTarget target, int status);
    void onRegistration(RegistrationManager manager, ServiceTarget target);
    void onDeregistration(RegistrationManager manager, ServiceTarget target, int status);
}
```

**Usage:**
```java
registrationManager.addCallBack(new DiscoveryCallback() {
    @Override
    public void onRegistration(RegistrationManager manager, ServiceTarget target) {
        LOG.info("Registered with ID: " + target.getId());
    }

    @Override
    public void onPing(RegistrationManager manager, ServiceTarget target, int status) {
        if (status != 200) {
            LOG.warn("Ping failed with status: " + status);
        }
    }

    @Override
    public void onDeregistration(RegistrationManager manager, ServiceTarget target, int status) {
        LOG.info("Deregistered");
    }
});
```

---

## Service Resolution (Router Side)

### ServiceResolver Interface

**Package:** `ai.wanaku.backend.service.support`

Defines contract for resolving services from the registry.

```java
public interface ServiceResolver {
    ServiceTarget resolve(String serviceName, String serviceType);
    ServiceTarget resolveCodeExecution(String serviceType, String serviceSubType,
        String languageName);
}
```

### FirstAvailable Strategy

**Package:** `ai.wanaku.backend.service.support`

Simple strategy that returns the first matching service from the registry.

```java
public class FirstAvailable implements ServiceResolver {
    private final ServiceRegistry serviceRegistry;

    public FirstAvailable(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public ServiceTarget resolve(String serviceName, String serviceType) {
        List<ServiceTarget> services = serviceRegistry.getServiceByName(
            serviceName, serviceType);
        if (services != null && !services.isEmpty()) {
            return services.getFirst();
        }
        return null;
    }

    @Override
    public ServiceTarget resolveCodeExecution(String serviceType,
            String serviceSubType, String languageName) {
        List<ServiceTarget> services = serviceRegistry.getCodeExecutionService(
            serviceType, serviceSubType, languageName);
        if (services != null && !services.isEmpty()) {
            return services.getFirst();
        }
        return null;
    }
}
```

### Provider Pattern

Resolvers are exposed via CDI producers:

```java
@ApplicationScoped
public class ToolsProvider {
    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    @Produces
    @DefaultBean
    ToolsResolver getResolver() {
        ServiceResolver resolver = new FirstAvailable(serviceRegistry);
        WanakuBridgeTransport transport = new GrpcTransport();
        return new WanakuToolsResolver(
            new InvokerBridge(resolver, transport, toolCallEventEmitter));
    }
}
```

---

## Event System

### ServiceTargetEvent

**Package:** `ai.wanaku.backend.common`

Event object emitted when service registration state changes.

| Field | Type | Description |
|-------|------|-------------|
| `eventType` | `EventType` | Type of event |
| `id` | `String` | Service identifier |
| `serviceTarget` | `ServiceTarget` | Full service descriptor (register/deregister) |
| `serviceState` | `ServiceState` | Health state (update events) |

**Factory Methods:**
```java
static ServiceTargetEvent register(ServiceTarget serviceTarget)
static ServiceTargetEvent deregister(ServiceTarget serviceTarget)
static ServiceTargetEvent update(String id, ServiceState serviceState)
static ServiceTargetEvent ping(String id)
```

### EventType Enum

```java
public enum EventType {
    REGISTER("register"),
    DEREGISTER("deregister"),
    UPDATE("update"),
    PING("ping");
}
```

### Reactive Messaging

Events are published to the `service-target-event` channel:

```java
@Inject
@Channel("service-target-event")
@OnOverflow(OnOverflow.Strategy.DROP)
MutinyEmitter<ServiceTargetEvent> serviceTargetEventEmitter;

private void emitEvent(ServiceTargetEvent event) {
    if (serviceTargetEventEmitter.hasRequests()) {
        serviceTargetEventEmitter.send(event);
    }
}
```

### SSE Notifications

Events are exposed via Server-Sent Events at `/api/v1/capabilities/notifications`:

```java
@Path("/notifications")
@Produces(MediaType.SERVER_SENT_EVENTS)
@GET
@RestStreamElementType(MediaType.APPLICATION_JSON)
public Multi<OutboundSseEvent> targetsEventStream(@Context Sse sse) {
    return serviceTargetEvents.map(event -> sse.newEventBuilder()
            .name(event.getEventType().name())
            .id(event.getId())
            .data(event)
            .build());
}
```

---

## Code Examples

### Creating a ServiceTarget

```java
ServiceTarget target = new ServiceTarget();
target.setServiceName("my-tool-provider");
target.setHost("localhost");
target.setPort(8080);
target.setServiceType(ServiceType.TOOL_INVOKER.asValue());

// Or using factory method
ServiceTarget target = ServiceTarget.newEmptyTarget(
    "my-tool-provider",
    "localhost",
    8080,
    ServiceType.TOOL_INVOKER.asValue(),
    null  // serviceSubType
);
```

### Registration Lifecycle

```java
// Create the discovery service client (Quarkus REST Client)
DiscoveryService discoveryService = RestClientBuilder.newBuilder()
    .baseUri(URI.create("http://router:8080"))
    .build(DiscoveryService.class);

// Register
RestResponse<WanakuResponse<ServiceTarget>> response =
    discoveryService.register(target);
String assignedId = response.getEntity().getData().getId();

// Periodic heartbeat
scheduledExecutor.scheduleAtFixedRate(() -> {
    discoveryService.ping(assignedId);
}, 30, 30, TimeUnit.SECONDS);

// Report state changes
discoveryService.updateState(assignedId, ServiceState.newHealthy());
discoveryService.updateState(assignedId,
    ServiceState.newUnhealthy("Database connection lost"));

// Deregister on shutdown
discoveryService.deregister(target);
```

### Service Resolution

```java
@Inject
ServiceRegistry serviceRegistry;

// Resolve a tool invoker
ServiceResolver resolver = new FirstAvailable(serviceRegistry);
ServiceTarget toolService = resolver.resolve(
    "my-tool",
    ServiceType.TOOL_INVOKER.asValue()
);

if (toolService != null) {
    String address = toolService.toAddress();  // "host:port"
    // Connect to service...
}

// Resolve code execution engine
ServiceTarget engine = resolver.resolveCodeExecution(
    ServiceType.CODE_EXECUTION_ENGINE.asValue(),
    "camel",      // engine type
    "yaml"        // language
);
```

### Event Handling

```java
// Subscribe to service events
@Inject
@Channel("service-target-event")
Multi<ServiceTargetEvent> events;

void subscribeToEvents() {
    events.subscribe().with(event -> {
        switch (event.getEventType()) {
            case REGISTER -> handleRegistration(event.getServiceTarget());
            case DEREGISTER -> handleDeregistration(event.getServiceTarget());
            case UPDATE -> handleStateUpdate(event.getId(), event.getServiceState());
            case PING -> handlePing(event.getId());
        }
    });
}
```

---

## File Locations

| Component | Path |
|-----------|------|
| Discovery REST API | `wanaku-router/wanaku-router-backend/.../discovery/DiscoveryResource.java` |
| Discovery Bean | `wanaku-router/wanaku-router-backend/.../discovery/DiscoveryBean.java` |
| ServiceRegistry Interface | `core/core-mcp/.../providers/ServiceRegistry.java` |
| Infinispan Implementation | `core/core-persistence/core-persistence-infinispan/.../discovery/` |
| ServiceTarget Model | `capabilities-api/.../types/providers/ServiceTarget.java` |
| State Models | `capabilities-api/.../types/discovery/` |
| Registration Manager | `core/core-capabilities-base/.../discovery/DefaultRegistrationManager.java` |
| Service Resolver | `wanaku-router/wanaku-router-backend/.../support/ServiceResolver.java` |
| Event Types | `wanaku-router/wanaku-router-backend/.../common/EventType.java` |
| SSE Endpoint | `wanaku-router/wanaku-router-backend/.../capabilities/CapabilitiesResource.java` |
