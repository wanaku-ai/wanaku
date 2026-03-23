package ai.wanaku.test.assertions;

import java.util.List;
import org.testcontainers.containers.GenericContainer;
import io.restassured.response.Response;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Custom assertions for Wanaku testing.
 * Provides domain-specific assertions with clear failure messages.
 */
public final class WanakuAssertions {

    private WanakuAssertions() {}

    // ========== Response Assertions ==========

    public static void assertSuccessResponse(WanakuResponse<?> response) {
        assertThat(response).isNotNull();
        assertThat(response.error())
                .withFailMessage("Expected success response but got error: %s", response.error())
                .isNull();
    }

    public static void assertErrorResponse(WanakuResponse<?> response) {
        assertThat(response).isNotNull();
        assertThat(response.error())
                .withFailMessage("Expected error response but got success")
                .isNotNull();
    }

    public static void assertErrorResponse(WanakuResponse<?> response, String expectedMessage) {
        assertErrorResponse(response);
        assertThat(String.valueOf(response.error()))
                .withFailMessage(
                        "Expected error message to contain '%s' but got: %s", expectedMessage, response.error())
                .contains(expectedMessage);
    }

    // ========== HTTP Response Assertions ==========

    public static void assertHttpStatus(Response response, int expectedStatus) {
        int actualStatus = response.getStatusCode();
        assertThat(actualStatus)
                .withFailMessage(
                        "Expected HTTP status %d but got %d. Response body: %s",
                        expectedStatus, actualStatus, getResponseBody(response))
                .isEqualTo(expectedStatus);
    }

    public static void assertHttpSuccess(Response response) {
        int status = response.getStatusCode();
        assertThat(status)
                .withFailMessage(
                        "Expected successful HTTP status (2xx) but got %d. Response: %s",
                        status, getResponseBody(response))
                .isBetween(200, 299);
    }

    public static void assertHttpError(Response response) {
        int status = response.getStatusCode();
        assertThat(status)
                .withFailMessage(
                        "Expected error HTTP status (4xx or 5xx) but got %d. Response: %s",
                        status, getResponseBody(response))
                .isGreaterThanOrEqualTo(400);
    }

    // ========== Tool Assertions ==========

    public static void assertToolRegistered(String toolName, List<ToolReference> tools) {
        assertThat(tools)
                .withFailMessage(
                        "Expected tool '%s' to be registered but it was not found in: %s",
                        toolName, getToolNames(tools))
                .extracting(ToolReference::getName)
                .contains(toolName);
    }

    public static void assertToolNotRegistered(String toolName, List<ToolReference> tools) {
        assertThat(tools)
                .withFailMessage("Expected tool '%s' to NOT be registered but it was found", toolName)
                .extracting(ToolReference::getName)
                .doesNotContain(toolName);
    }

    public static void assertToolEquals(ToolReference expected, ToolReference actual) {
        assertThat(actual)
                .withFailMessage("Expected tool to match but it didn't")
                .isNotNull();
        assertThat(actual.getName()).isEqualTo(expected.getName());
        assertThat(actual.getNamespace()).isEqualTo(expected.getNamespace());
        assertThat(actual.getType()).isEqualTo(expected.getType());
        assertThat(actual.getUri()).isEqualTo(expected.getUri());
    }

    // ========== Resource Assertions ==========

    public static void assertResourceRegistered(String resourceName, List<ResourceReference> resources) {
        assertThat(resources)
                .withFailMessage(
                        "Expected resource '%s' to be registered but it was not found in: %s",
                        resourceName, getResourceNames(resources))
                .extracting(ResourceReference::getName)
                .contains(resourceName);
    }

    public static void assertResourceNotRegistered(String resourceName, List<ResourceReference> resources) {
        assertThat(resources)
                .withFailMessage("Expected resource '%s' to NOT be registered but it was found", resourceName)
                .extracting(ResourceReference::getName)
                .doesNotContain(resourceName);
    }

    public static void assertResourceEquals(ResourceReference expected, ResourceReference actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getName()).isEqualTo(expected.getName());
        assertThat(actual.getNamespace()).isEqualTo(expected.getNamespace());
        assertThat(actual.getLocation()).isEqualTo(expected.getLocation());
        assertThat(actual.getType()).isEqualTo(expected.getType());
        assertThat(actual.getMimeType()).isEqualTo(expected.getMimeType());
    }

    // ========== Forward Assertions ==========

    public static void assertForwardRegistered(String forwardName, List<ForwardReference> forwards) {
        assertThat(forwards)
                .withFailMessage("Expected forward '%s' to be registered but it was not found", forwardName)
                .extracting(ForwardReference::getName)
                .contains(forwardName);
    }

    public static void assertForwardEquals(ForwardReference expected, ForwardReference actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getName()).isEqualTo(expected.getName());
        assertThat(actual.getAddress()).isEqualTo(expected.getAddress());
    }

    // ========== Container Assertions ==========

    public static void assertContainerRunning(GenericContainer<?> container) {
        assertThat(container.isRunning())
                .withFailMessage("Container %s is not running", container.getDockerImageName())
                .isTrue();
    }

    public static void assertContainerHealthy(GenericContainer<?> container) {
        assertContainerRunning(container);
        // Additional health checks if needed
    }

    // ========== Namespace Assertions ==========

    public static void assertNamespaceExists(String namespaceName, List<Namespace> namespaces) {
        assertThat(namespaces)
                .withFailMessage("Expected namespace '%s' to exist", namespaceName)
                .extracting(Namespace::getName)
                .contains(namespaceName);
    }

    // ========== Helper Methods ==========

    private static String getResponseBody(Response response) {
        try {
            return response.getBody().asString();
        } catch (Exception e) {
            return "<unable to read response body>";
        }
    }

    private static List<String> getToolNames(List<ToolReference> tools) {
        return tools.stream().map(ToolReference::getName).toList();
    }

    private static List<String> getResourceNames(List<ResourceReference> resources) {
        return resources.stream().map(ResourceReference::getName).toList();
    }
}
