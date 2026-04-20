# Wanaku Assertions Library

Wanaku provides a domain-specific assertions helper designed to keep tests concise and to surface clear,
actionable failure messages.

**Location**
`core/core-util/src/test/java/ai/wanaku/test/assertions/WanakuAssertions.java`

This class is shared across modules via the `core-util` test-jar (`classifier` `tests`).

## Usage

Add a static import in your test:

```java
import static ai.wanaku.test.assertions.WanakuAssertions.assertHttpStatus;
import static ai.wanaku.test.assertions.WanakuAssertions.assertNamespaceExists;
import static ai.wanaku.test.assertions.WanakuAssertions.assertSuccessResponse;
```

### Available helpers

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
