# Wanaku Assertions Library

This module provides a domain-specific assertions helper designed to keep tests concise and to surface clear,
actionable failure messages.

**Location**
`core/core-util/src/test/java/ai/wanaku/test/assertions/WanakuAssertions.java`

This class is shared across modules via the `core-util` test-jar (`classifier` `tests`).

## Usage

Add a static import in your test:

```java
import static ai.wanaku.test.assertions.WanakuAssertions.assertSuccessResponse;
```

### Common patterns

```java
WanakuResponse<ToolReference> response = toolsService.getByName("my-tool");
assertSuccessResponse(response);
```

```java
assertNamespaceExists("default", namespaces);
```

```java
assertHttpSuccess(restAssuredResponse);
```

## Examples in the Codebase

These tests demonstrate the custom assertions:

* `cli/src/test/java/ai/wanaku/cli/main/commands/tools/ToolsLabelAddTest.java`
* `cli/src/test/java/ai/wanaku/cli/main/commands/tools/ToolsLabelRemoveTest.java`
* `wanaku-router/wanaku-router-backend/src/test/java/ai/wanaku/backend/api/v1/namespaces/NamespacesBeanTest.java`
* `wanaku-router/wanaku-router-backend/src/test/java/ai/wanaku/backend/api/v1/tools/ToolsResourceTest.java`
* `wanaku-router/wanaku-router-backend/src/test/java/ai/wanaku/backend/api/v1/resources/ResourcesResourceTest.java`

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
