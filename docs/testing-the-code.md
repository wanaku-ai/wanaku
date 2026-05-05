# Wanaku Assertions Library

Wanaku provides domain-specific assertion helpers designed to keep tests concise and to surface clear,
actionable failure messages.

## Core Assertions

**Location**
`core/core-util/src/test/java/ai/wanaku/test/assertions/WanakuAssertions.java`

This class is shared across modules via the `core-util` test-jar (`classifier` `tests`).

It provides assertions for:
- HTTP responses (status, success/error)
- Wanaku responses (success/error)
- Tools, Resources, Forwards (registration, equality)
- Containers (running, healthy)
- Namespaces

## Operator Assertions (Kubernetes-specific)

**Location**
`apps/wanaku-operator/src/test/java/ai/wanaku/operator/assertions/OperatorAssertions.java`

This class provides Kubernetes-specific assertions for testing operator functionality:

- `assertCondition(Condition, String, String, Long)` - Verify Kubernetes condition state
- `assertServiceLabel(Service, String, String)` - Verify service has expected label
- `assertServicePort(Service, int)` - Verify service exposes expected port
- `assertMetadataLabel(HasMetadata, String, String)` - Verify resource has expected label
- `assertEndpointTarget(Endpoints, String, int)` - Verify endpoint points to expected host/port

These assertions are specifically for the operator module and use fabric8 Kubernetes client types.

## Usage

### Core Assertions

Add a static import in your test:

```java
import static ai.wanaku.test.assertions.WanakuAssertions.assertHttpStatus;
import static ai.wanaku.test.assertions.WanakuAssertions.assertNamespaceExists;
import static ai.wanaku.test.assertions.WanakuAssertions.assertSuccessResponse;
```

### Operator Assertions (Kubernetes-specific)

For operator tests, use the operator-specific assertions:

```java
import static ai.wanaku.operator.assertions.OperatorAssertions.assertCondition;
import static ai.wanaku.operator.assertions.OperatorAssertions.assertEndpointTarget;
import static ai.wanaku.operator.assertions.OperatorAssertions.assertServiceLabel;
import static ai.wanaku.operator.assertions.OperatorAssertions.assertServicePort;
```

### Available helpers

**Core Assertions (`WanakuAssertions`):**

- `assertSuccessResponse(WanakuResponse<?>)`
- `assertErrorResponse(WanakuResponse<?>)`
- `assertErrorResponse(WanakuResponse<?>, String)`
- `assertHttpStatus(Response, int)`
- `assertHttpSuccess(Response)`
- `assertHttpError(Response)`
- `assertToolRegistered(String, List<ToolReference>)`
- `assertToolNotRegistered(String, List<ToolReference>)`
- `assertToolEquals(ToolReference, ToolReference)`
- `assertResourceRegistered(String, List<ResourceReference>)`
- `assertResourceNotRegistered(String, List<ResourceReference>)`
- `assertResourceEquals(ResourceReference, ResourceReference)`
- `assertForwardRegistered(String, List<ForwardReference>)`
- `assertForwardEquals(ForwardReference, ForwardReference)`
- `assertContainerRunning(GenericContainer<?>)`
- `assertContainerHealthy(GenericContainer<?>)`
- `assertNamespaceExists(String, List<Namespace>)`

**Operator Assertions (`OperatorAssertions`):**

- `assertCondition(Condition, String, String, Long)` - Verify Kubernetes condition
- `assertServiceLabel(Service, String, String)` - Verify service label
- `assertServicePort(Service, int)` - Verify service port
- `assertMetadataLabel(HasMetadata, String, String)` - Verify resource label
- `assertEndpointTarget(Endpoints, String, int)` - Verify endpoint target

### Common patterns

```java
WanakuResponse<ToolReference> response = toolsService.getByName("my-tool");
assertSuccessResponse(response);
```

```java
assertNamespaceExists("default", namespaces);
```

```java
assertHttpStatus(restAssuredResponse, 200);
```

## Examples in the Codebase

These tests demonstrate the custom assertions:

- `apps/wanaku-router-backend/src/test/java/ai/wanaku/backend/api/v1/namespaces/NamespacesBeanTest.java`
- `apps/wanaku-router-backend/src/test/java/ai/wanaku/backend/api/v1/tools/ToolsResourceTest.java`
- `apps/wanaku-router-backend/src/test/java/ai/wanaku/backend/api/v1/resources/ResourcesResourceTest.java`
- `apps/wanaku-router-backend/src/test/java/ai/wanaku/backend/api/v1/prompts/PromptsResourceTest.java`

## Migration Guide

1. Identify repeated assertion patterns (success response, tool/resource presence, namespace existence, HTTP status).
2. Replace AssertJ/JUnit assertions with the closest `WanakuAssertions` method.
3. Keep any test-specific assertions (e.g., label map updates) alongside the custom assertions.
4. When asserting `WanakuResponse`, use `assertSuccessResponse` or `assertErrorResponse` since the response is a record
   with the `error()` accessor (no `isSuccess()`).

### Quick replacements

* `assertThat(response.error()).isNull()`  
  -> `assertSuccessResponse(response)`
* `assertThat(namespaces).extracting(Namespace::getName).contains("default")`  
  -> `assertNamespaceExists("default", namespaces)`
* `response.then().statusCode(200)`  
  -> `assertHttpStatus(response, 200)`
