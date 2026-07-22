package ai.wanaku.operator.util;

import java.util.Base64;
import java.util.Map;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import ai.wanaku.operator.wanaku.WanakuTypes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperatorSecurityConfigTest {

    @Test
    void testClientIdIsFixed() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertEquals("wanaku-service", config.getClientId());
    }

    @Test
    void testTokenEndpointWithDefaultRealm() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertEquals("http://keycloak:8080/realms/wanaku/protocol/openid-connect/token", config.getTokenEndpoint());
    }

    @Test
    void testTokenEndpointWithCustomRealm() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");
        authSpec.setAuthRealm("custom-realm");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertEquals(
                "http://keycloak:8080/realms/custom-realm/protocol/openid-connect/token", config.getTokenEndpoint());
    }

    @Test
    void testTokenEndpointStripsTrailingSlash() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080/");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertEquals("http://keycloak:8080/realms/wanaku/protocol/openid-connect/token", config.getTokenEndpoint());
    }

    @Test
    void testSecretFallsBackToDefault() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertEquals(OperatorSecurityConfig.DEFAULT_CLIENT_SECRET, config.getSecret());
    }

    @Test
    void testIsAuthEnabledWithValidSpec() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        assertTrue(OperatorSecurityConfig.isAuthEnabled(authSpec));
    }

    @Test
    void testIsAuthEnabledWithNullSpec() {
        assertFalse(OperatorSecurityConfig.isAuthEnabled(null));
    }

    @Test
    void testIsAuthEnabledWithNullAuthServer() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();

        assertFalse(OperatorSecurityConfig.isAuthEnabled(authSpec));
    }

    @Test
    void testIsAuthEnabledWithBlankAuthServer() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("  ");

        assertFalse(OperatorSecurityConfig.isAuthEnabled(authSpec));
    }

    @Test
    void testIsAuthEnabledReportsCorrectly() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        assertTrue(config.isAuthEnabled());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSecretReadFromKubernetesApi() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        String expectedSecret = "my-rotated-secret";
        Secret k8sSecret = new SecretBuilder()
                .withData(Map.of(
                        OperatorSecurityConfig.SECRET_KEY,
                        Base64.getEncoder().encodeToString(expectedSecret.getBytes())))
                .build();

        KubernetesClient client = mock(KubernetesClient.class);
        MixedOperation secretsOp = mock(MixedOperation.class);
        NonNamespaceOperation nsOp = mock(NonNamespaceOperation.class);
        Resource secretResource = mock(Resource.class);

        when(client.secrets()).thenReturn(secretsOp);
        when(secretsOp.inNamespace("test-ns")).thenReturn(nsOp);
        when(nsOp.withName(OperatorSecurityConfig.DEFAULT_SECRET_NAME)).thenReturn(secretResource);
        when(secretResource.get()).thenReturn(k8sSecret);

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec, client, "test-ns");

        assertEquals(expectedSecret, config.getSecret());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSecretFallsBackWhenKubernetesApiFails() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        KubernetesClient client = mock(KubernetesClient.class);
        when(client.secrets()).thenThrow(new RuntimeException("API unreachable"));

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec, client, "test-ns");

        // Should fall back to env var / default without throwing
        assertEquals(OperatorSecurityConfig.DEFAULT_CLIENT_SECRET, config.getSecret());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSecretFallsBackWhenK8sSecretMissing() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        KubernetesClient client = mock(KubernetesClient.class);
        MixedOperation secretsOp = mock(MixedOperation.class);
        NonNamespaceOperation nsOp = mock(NonNamespaceOperation.class);
        Resource secretResource = mock(Resource.class);

        when(client.secrets()).thenReturn(secretsOp);
        when(secretsOp.inNamespace("test-ns")).thenReturn(nsOp);
        when(nsOp.withName(OperatorSecurityConfig.DEFAULT_SECRET_NAME)).thenReturn(secretResource);
        when(secretResource.get()).thenReturn(null);

        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec, client, "test-ns");

        assertEquals(OperatorSecurityConfig.DEFAULT_CLIENT_SECRET, config.getSecret());
    }

    @Test
    void testResolveSecretNameDefault() {
        // When env var is not set, should return the default name
        assertEquals(OperatorSecurityConfig.DEFAULT_SECRET_NAME, OperatorSecurityConfig.resolveSecretName());
    }

    @Test
    void testLegacyConstructorFallsBackToEnvVar() {
        WanakuTypes.AuthSpec authSpec = new WanakuTypes.AuthSpec();
        authSpec.setAuthServer("http://keycloak:8080");

        // Legacy constructor (no K8s client) should still work
        OperatorSecurityConfig config = new OperatorSecurityConfig(authSpec);

        // Should return default since no env var and no K8s client
        assertEquals(OperatorSecurityConfig.DEFAULT_CLIENT_SECRET, config.getSecret());
    }
}
