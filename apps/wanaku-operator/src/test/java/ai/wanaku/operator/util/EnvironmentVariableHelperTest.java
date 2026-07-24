package ai.wanaku.operator.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentVariableHelperTest {

    // ── extractAnnotationEnvVars ──────────────────────────────────────────────

    @Test
    void nullAnnotationMapReturnsEmptyList() {
        List<EnvVar> result = EnvironmentVariableHelper.extractAnnotationEnvVars(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void emptyAnnotationMapReturnsEmptyList() {
        List<EnvVar> result = EnvironmentVariableHelper.extractAnnotationEnvVars(Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void annotationsWithoutPrefixReturnEmptyList() {
        Map<String, String> annotations = Map.of(
                "kubectl.kubernetes.io/last-applied-configuration", "{}",
                "app.kubernetes.io/managed-by", "operator");

        List<EnvVar> result = EnvironmentVariableHelper.extractAnnotationEnvVars(annotations);
        assertTrue(result.isEmpty());
    }

    @Test
    void singleMatchingAnnotationProducesCorrectEnvVar() {
        Map<String, String> annotations = Map.of("env.wanaku.ai/MY_VAR", "my_value");

        List<EnvVar> result = EnvironmentVariableHelper.extractAnnotationEnvVars(annotations);

        assertEquals(1, result.size());
        assertEquals("MY_VAR", result.getFirst().getName());
        assertEquals("my_value", result.getFirst().getValue());
    }

    @Test
    void mixedAnnotationsReturnsOnlyPrefixedOnes() {
        Map<String, String> annotations = Map.of(
                "env.wanaku.ai/KEEP_ME", "yes",
                "app.kubernetes.io/managed-by", "operator",
                "some.other/annotation", "ignored");

        List<EnvVar> result = EnvironmentVariableHelper.extractAnnotationEnvVars(annotations);

        assertEquals(1, result.size());
        assertEquals("KEEP_ME", result.getFirst().getName());
        assertEquals("yes", result.getFirst().getValue());
    }

    @Test
    void multipleMatchingAnnotationsAllReturned() {
        Map<String, String> annotations = Map.of(
                "env.wanaku.ai/VAR_A", "alpha",
                "env.wanaku.ai/VAR_B", "beta",
                "unrelated", "ignored");

        List<EnvVar> result = EnvironmentVariableHelper.extractAnnotationEnvVars(annotations);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> "VAR_A".equals(e.getName()) && "alpha".equals(e.getValue())));
        assertTrue(result.stream().anyMatch(e -> "VAR_B".equals(e.getName()) && "beta".equals(e.getValue())));
    }

    @Test
    void emptySuffixAnnotationIsSkipped() {
        // Key is exactly the prefix with nothing after the slash
        Map<String, String> annotations = Map.of("env.wanaku.ai/", "should_be_ignored");

        List<EnvVar> result = EnvironmentVariableHelper.extractAnnotationEnvVars(annotations);

        assertTrue(result.isEmpty());
    }

    @Test
    void hyphenInNameIsSkipped() {
        Map<String, String> annotations = Map.of("env.wanaku.ai/MY-VAR", "value");

        List<EnvVar> result = EnvironmentVariableHelper.extractAnnotationEnvVars(annotations);

        assertTrue(result.isEmpty());
    }

    @Test
    void digitLeadingNameIsSkipped() {
        Map<String, String> annotations = Map.of("env.wanaku.ai/1VAR", "value");

        List<EnvVar> result = EnvironmentVariableHelper.extractAnnotationEnvVars(annotations);

        assertTrue(result.isEmpty());
    }

    @Test
    void mixOfValidAndInvalidAnnotationsReturnsOnlyValid() {
        Map<String, String> annotations = Map.of(
                "env.wanaku.ai/VALID_VAR", "keep",
                "env.wanaku.ai/INVALID-VAR", "drop",
                "env.wanaku.ai/", "drop_empty");

        List<EnvVar> result = EnvironmentVariableHelper.extractAnnotationEnvVars(annotations);

        assertEquals(1, result.size());
        assertEquals("VALID_VAR", result.getFirst().getName());
        assertEquals("keep", result.getFirst().getValue());
    }

    // ── applyAnnotationEnvVars ────────────────────────────────────────────────

    @Test
    void applyWithNullAnnotationsLeavesListUnchanged() {
        List<EnvVar> envVars = new ArrayList<>(List.of(
                new EnvVarBuilder().withName("EXISTING").withValue("val").build()));

        EnvironmentVariableHelper.applyAnnotationEnvVars(envVars, null);

        assertEquals(1, envVars.size());
        assertEquals("EXISTING", envVars.getFirst().getName());
    }

    @Test
    void applyAddsNewAnnotationVar() {
        List<EnvVar> envVars = new ArrayList<>(List.of(
                new EnvVarBuilder().withName("EXISTING").withValue("val").build()));

        EnvironmentVariableHelper.applyAnnotationEnvVars(envVars, Map.of("env.wanaku.ai/NEW_VAR", "new_value"));

        assertEquals(2, envVars.size());
        assertTrue(envVars.stream().anyMatch(e -> "NEW_VAR".equals(e.getName()) && "new_value".equals(e.getValue())));
    }

    @Test
    void applyAnnotationVarOverridesExistingVarWithSameName() {
        List<EnvVar> envVars = new ArrayList<>(List.of(
                new EnvVarBuilder()
                        .withName("QUARKUS_TLS_TRUST_ALL")
                        .withValue("true")
                        .build(),
                new EnvVarBuilder().withName("OTHER").withValue("keep").build()));

        EnvironmentVariableHelper.applyAnnotationEnvVars(
                envVars, Map.of("env.wanaku.ai/QUARKUS_TLS_TRUST_ALL", "false"));

        assertEquals(2, envVars.size());
        assertEquals(
                "false",
                envVars.stream()
                        .filter(e -> "QUARKUS_TLS_TRUST_ALL".equals(e.getName()))
                        .findFirst()
                        .map(EnvVar::getValue)
                        .orElseThrow());
        // unrelated var must be preserved
        assertTrue(envVars.stream().anyMatch(e -> "OTHER".equals(e.getName())));
    }
}
