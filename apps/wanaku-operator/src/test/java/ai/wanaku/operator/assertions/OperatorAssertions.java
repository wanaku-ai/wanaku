package ai.wanaku.operator.assertions;

import org.assertj.core.api.Assertions;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;

/**
 * Custom assertions for Wanaku operator testing.
 * Provides Kubernetes-specific assertions with clear failure messages.
 */
public final class OperatorAssertions {

    private OperatorAssertions() {}

    // ========== Condition Assertions ==========

    public static void assertCondition(
            Condition condition, String expectedType, String expectedStatus, Long expectedObservedGeneration) {
        Assertions.assertThat(condition).isNotNull();
        Assertions.assertThat(condition.getType()).isEqualTo(expectedType);
        Assertions.assertThat(condition.getStatus()).isEqualTo(expectedStatus);
        Assertions.assertThat(condition.getObservedGeneration()).isEqualTo(expectedObservedGeneration);
    }

    // ========== Service Assertions ==========

    public static void assertServiceLabel(Service service, String labelKey, String expectedValue) {
        Assertions.assertThat(service).isNotNull();
        Assertions.assertThat(service.getMetadata().getLabels()).containsEntry(labelKey, expectedValue);
    }

    public static void assertServicePort(Service service, int expectedPort) {
        Assertions.assertThat(service).isNotNull();
        Assertions.assertThat(service.getSpec().getPorts()).isNotEmpty();
        Assertions.assertThat(service.getSpec().getPorts().getFirst().getPort()).isEqualTo(expectedPort);
    }

    // ========== Metadata Assertions ==========

    public static void assertMetadataLabel(HasMetadata resource, String labelKey, String expectedValue) {
        Assertions.assertThat(resource).isNotNull();
        Assertions.assertThat(resource.getMetadata().getLabels()).containsEntry(labelKey, expectedValue);
    }

    // ========== Endpoint Assertions ==========

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
}
