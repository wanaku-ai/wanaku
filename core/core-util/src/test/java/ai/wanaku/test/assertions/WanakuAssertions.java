package ai.wanaku.test.assertions;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.testcontainers.containers.GenericContainer;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.restassured.response.Response;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

/**
 * Custom assertions for Wanaku testing.
 * Provides domain-specific assertions with clear failure messages.
 */
public final class WanakuAssertions {

    private WanakuAssertions() {}

    // ========== Response Assertions ==========

    public static void assertSuccessResponse(WanakuResponse<?> response) {
        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.error())
                .withFailMessage("Expected success response but got error: %s", response.error())
                .isNull();
    }

    public static void assertErrorResponse(WanakuResponse<?> response) {
        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.error())
                .withFailMessage("Expected error response but got success")
                .isNotNull();
    }

    public static void assertErrorResponse(WanakuResponse<?> response, String expectedMessage) {
        assertErrorResponse(response);
        Assertions.assertThat(String.valueOf(response.error()))
                .withFailMessage(
                        "Expected error message to contain '%s' but got: %s", expectedMessage, response.error())
                .contains(expectedMessage);
    }

    // ========== HTTP Response Assertions ==========

    public static void assertHttpStatus(Response response, int expectedStatus) {
        int actualStatus = response.getStatusCode();
        Assertions.assertThat(actualStatus)
                .withFailMessage(
                        "Expected HTTP status %d but got %d. Response body: %s",
                        expectedStatus, actualStatus, getResponseBody(response))
                .isEqualTo(expectedStatus);
    }

    public static void assertHttpSuccess(Response response) {
        int status = response.getStatusCode();
        Assertions.assertThat(status)
                .withFailMessage(
                        "Expected successful HTTP status (2xx) but got %d. Response: %s",
                        status, getResponseBody(response))
                .isBetween(200, 299);
    }

    public static void assertHttpError(Response response) {
        int status = response.getStatusCode();
        Assertions.assertThat(status)
                .withFailMessage(
                        "Expected error HTTP status (4xx or 5xx) but got %d. Response: %s",
                        status, getResponseBody(response))
                .isGreaterThanOrEqualTo(400);
    }

    // ========== Tool Assertions ==========

    public static void assertToolRegistered(String toolName, List<ToolReference> tools) {
        Assertions.assertThat(tools)
                .withFailMessage(
                        "Expected tool '%s' to be registered but it was not found in: %s",
                        toolName, getToolNames(tools))
                .extracting(ToolReference::getName)
                .contains(toolName);
    }

    public static void assertToolNotRegistered(String toolName, List<ToolReference> tools) {
        Assertions.assertThat(tools)
                .withFailMessage("Expected tool '%s' to NOT be registered but it was found", toolName)
                .extracting(ToolReference::getName)
                .doesNotContain(toolName);
    }

    public static void assertToolEquals(ToolReference expected, ToolReference actual) {
        Assertions.assertThat(actual)
                .withFailMessage("Expected tool to match but it didn't")
                .isNotNull();
        Assertions.assertThat(actual.getName()).isEqualTo(expected.getName());
        Assertions.assertThat(actual.getNamespace()).isEqualTo(expected.getNamespace());
        Assertions.assertThat(actual.getType()).isEqualTo(expected.getType());
        Assertions.assertThat(actual.getUri()).isEqualTo(expected.getUri());
    }

    // ========== Resource Assertions ==========

    public static void assertResourceRegistered(String resourceName, List<ResourceReference> resources) {
        Assertions.assertThat(resources)
                .withFailMessage(
                        "Expected resource '%s' to be registered but it was not found in: %s",
                        resourceName, getResourceNames(resources))
                .extracting(ResourceReference::getName)
                .contains(resourceName);
    }

    public static void assertResourceNotRegistered(String resourceName, List<ResourceReference> resources) {
        Assertions.assertThat(resources)
                .withFailMessage("Expected resource '%s' to NOT be registered but it was found", resourceName)
                .extracting(ResourceReference::getName)
                .doesNotContain(resourceName);
    }

    public static void assertResourceEquals(ResourceReference expected, ResourceReference actual) {
        Assertions.assertThat(actual).isNotNull();
        Assertions.assertThat(actual.getName()).isEqualTo(expected.getName());
        Assertions.assertThat(actual.getNamespace()).isEqualTo(expected.getNamespace());
        Assertions.assertThat(actual.getLocation()).isEqualTo(expected.getLocation());
        Assertions.assertThat(actual.getType()).isEqualTo(expected.getType());
        Assertions.assertThat(actual.getMimeType()).isEqualTo(expected.getMimeType());
    }

    // ========== Forward Assertions ==========

    public static void assertForwardRegistered(String forwardName, List<ForwardReference> forwards) {
        Assertions.assertThat(forwards)
                .withFailMessage("Expected forward '%s' to be registered but it was not found", forwardName)
                .extracting(ForwardReference::getName)
                .contains(forwardName);
    }

    public static void assertForwardEquals(ForwardReference expected, ForwardReference actual) {
        Assertions.assertThat(actual).isNotNull();
        Assertions.assertThat(actual.getName()).isEqualTo(expected.getName());
        Assertions.assertThat(actual.getAddress()).isEqualTo(expected.getAddress());
    }

    // ========== Container Assertions ==========

    public static void assertContainerRunning(GenericContainer<?> container) {
        Assertions.assertThat(container.isRunning())
                .withFailMessage("Container %s is not running", container.getDockerImageName())
                .isTrue();
    }

    public static void assertContainerHealthy(GenericContainer<?> container) {
        assertContainerRunning(container);
        // Additional health checks if needed
    }

    // ========== Namespace Assertions ==========

    public static void assertNamespaceExists(String namespaceName, List<Namespace> namespaces) {
        Assertions.assertThat(namespaces)
                .withFailMessage("Expected namespace '%s' to exist", namespaceName)
                .extracting(Namespace::getName)
                .contains(namespaceName);
    }

    public static void assertCondition(
            Condition condition, String expectedType, String expectedStatus, Long expectedObservedGeneration) {
        Assertions.assertThat(condition).isNotNull();
        Assertions.assertThat(condition.getType()).isEqualTo(expectedType);
        Assertions.assertThat(condition.getStatus()).isEqualTo(expectedStatus);
        Assertions.assertThat(condition.getObservedGeneration()).isEqualTo(expectedObservedGeneration);
    }

    public static void assertServiceLabel(Service service, String labelKey, String expectedValue) {
        Assertions.assertThat(service).isNotNull();
        Assertions.assertThat(service.getMetadata().getLabels()).containsEntry(labelKey, expectedValue);
    }

    public static void assertMetadataLabel(HasMetadata resource, String labelKey, String expectedValue) {
        Assertions.assertThat(resource).isNotNull();
        Assertions.assertThat(resource.getMetadata().getLabels()).containsEntry(labelKey, expectedValue);
    }

    public static void assertServicePort(Service service, int expectedPort) {
        Assertions.assertThat(service).isNotNull();
        Assertions.assertThat(service.getSpec().getPorts()).isNotEmpty();
        Assertions.assertThat(service.getSpec().getPorts().getFirst().getPort()).isEqualTo(expectedPort);
    }

    public static void assertEndpointTarget(Endpoints endpoints, String expectedHost, int expectedPort) {
        Assertions.assertThat(endpoints).isNotNull();
        Assertions.assertThat(endpoints.getSubsets()).isNotEmpty();
        Assertions.assertThat(endpoints.getSubsets().getFirst().getAddresses()).isNotEmpty();
        Assertions.assertThat(endpoints.getSubsets().getFirst().getPorts()).isNotEmpty();
        Assertions.assertThat(endpoints
                        .getSubsets()
                        .getFirst()
                        .getAddresses()
                        .getFirst()
                        .getHostname())
                .isEqualTo(expectedHost);
        Assertions.assertThat(
                        endpoints.getSubsets().getFirst().getPorts().getFirst().getPort())
                .isEqualTo(expectedPort);
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
