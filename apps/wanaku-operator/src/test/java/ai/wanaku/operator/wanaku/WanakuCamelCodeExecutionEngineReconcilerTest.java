package ai.wanaku.operator.wanaku;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.operator.util.OperatorUtil;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WanakuCamelCodeExecutionEngineReconcilerTest {

    private final WanakuCamelCodeExecutionEngineReconciler reconciler = new WanakuCamelCodeExecutionEngineReconciler();

    private WanakuCamelCodeExecutionEngine baseResource() {
        WanakuCamelCodeExecutionEngine resource = new WanakuCamelCodeExecutionEngine();
        WanakuCamelCodeExecutionEngineSpec spec = new WanakuCamelCodeExecutionEngineSpec();
        spec.setRouterRef("my-router");
        spec.setEngineType("camel");
        spec.setLanguageName("yaml");
        spec.setImage("quay.io/wanaku/camel-code-execution-engine:latest");
        resource.setSpec(spec);
        resource.getMetadata().setName("test-engine");
        resource.getMetadata().setNamespace("wanaku");
        resource.getMetadata().setUid("test-uid");
        return resource;
    }

    @Test
    void validateSpecReturnsInvalidWhenSpecIsNull() {
        WanakuCamelCodeExecutionEngine resource = new WanakuCamelCodeExecutionEngine();
        resource.setSpec(null);
        WanakuCamelCodeExecutionEngineReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertFalse(result.valid);
        assertEquals("spec must be provided", result.errorMessage);
    }

    @Test
    void validateSpecReturnsInvalidWhenRouterRefIsMissing() {
        WanakuCamelCodeExecutionEngine resource = baseResource();
        resource.getSpec().setRouterRef(null);
        WanakuCamelCodeExecutionEngineReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertFalse(result.valid);
        assertTrue(result.errorMessage.contains("routerRef"));
    }

    @Test
    void validateSpecReturnsInvalidWhenLanguageNameIsMissing() {
        WanakuCamelCodeExecutionEngine resource = baseResource();
        resource.getSpec().setLanguageName(null);
        WanakuCamelCodeExecutionEngineReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertFalse(result.valid);
        assertTrue(result.errorMessage.contains("languageName"));
    }

    @Test
    void validateSpecReturnsInvalidWhenEngineTypeIsMissing() {
        WanakuCamelCodeExecutionEngine resource = baseResource();
        resource.getSpec().setEngineType(null);
        WanakuCamelCodeExecutionEngineReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertFalse(result.valid);
        assertTrue(result.errorMessage.contains("engineType"));
    }

    @Test
    void validateSpecReturnsInvalidWhenImageMissingInClusterMode() {
        WanakuCamelCodeExecutionEngine resource = baseResource();
        resource.getSpec().setImage(null);
        WanakuCamelCodeExecutionEngineReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertFalse(result.valid);
        assertTrue(result.errorMessage.contains("image"));
    }

    @Test
    void validateSpecReturnsInvalidWhenRemoteHostMissing() {
        WanakuCamelCodeExecutionEngine resource = baseResource();
        resource.getSpec().setDeploymentMode("remote");
        WanakuCamelCodeExecutionEngineReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertFalse(result.valid);
        assertTrue(result.errorMessage.contains("remote.host"));
    }

    @Test
    void validateSpecReturnsValidForInCluster() {
        WanakuCamelCodeExecutionEngine resource = baseResource();
        WanakuCamelCodeExecutionEngineReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertTrue(result.valid);
        assertNull(result.errorMessage);
    }

    @Test
    void validateSpecReturnsValidForRemote() {
        WanakuCamelCodeExecutionEngine resource = baseResource();
        resource.getSpec().setDeploymentMode("remote");
        WanakuCamelCodeExecutionEngineSpec.RemoteSpec remote = new WanakuCamelCodeExecutionEngineSpec.RemoteSpec();
        remote.setHost("engine.example.com");
        resource.getSpec().setRemote(remote);
        WanakuCamelCodeExecutionEngineReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertTrue(result.valid);
        assertNull(result.errorMessage);
    }

    @Test
    void validateSpecReturnsInvalidForBadDeploymentMode() {
        WanakuCamelCodeExecutionEngine resource = baseResource();
        resource.getSpec().setDeploymentMode("invalid");
        WanakuCamelCodeExecutionEngineReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertFalse(result.valid);
        assertTrue(result.errorMessage.contains("deploymentMode"));
    }

    @Test
    void validateSpecReturnsInvalidForSecurityOverlap() {
        WanakuCamelCodeExecutionEngine resource = baseResource();
        WanakuCamelCodeExecutionEngineSpec.SecuritySpec securitySpec =
                new WanakuCamelCodeExecutionEngineSpec.SecuritySpec();
        securitySpec.setComponentAllowlist(List.of("component-a", "component-b"));
        securitySpec.setComponentBlocklist(List.of("component-b"));
        resource.getSpec().setSecurity(securitySpec);
        WanakuCamelCodeExecutionEngineReconciler.ValidateSpecResult result = reconciler.validateSpec(resource);
        assertFalse(result.valid);
        assertTrue(result.errorMessage.contains("component"));
    }

    @Test
    void validateDeploymentModeAcceptsInCluster() {
        assertDoesNotThrow(() -> OperatorUtil.validateDeploymentMode("in-cluster"));
    }

    @Test
    void validateDeploymentModeAcceptsRemote() {
        assertDoesNotThrow(() -> OperatorUtil.validateDeploymentMode("remote"));
    }

    @Test
    void validateDeploymentModeAcceptsNull() {
        assertDoesNotThrow(() -> OperatorUtil.validateDeploymentMode(null));
    }

    @Test
    void validateDeploymentModeAcceptsBlank() {
        assertDoesNotThrow(() -> OperatorUtil.validateDeploymentMode(" "));
    }

    @Test
    void validateDeploymentModeThrowsForInvalidValue() {
        WanakuException ex = assertThrows(WanakuException.class, () -> OperatorUtil.validateDeploymentMode("invalid"));
        assertTrue(ex.getMessage().contains("deploymentMode"));
    }

    @Test
    void validateCacheStrategyAcceptsValidStrategies() {
        for (String strategy : WanakuTypes.VALID_CACHE_STRATEGIES) {
            WanakuCamelCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec =
                    new WanakuCamelCodeExecutionEngineSpec.DependencyCacheSpec();
            cacheSpec.setStrategy(strategy);
            assertDoesNotThrow(() -> OperatorUtil.validateCacheStrategy(cacheSpec));
        }
    }

    @Test
    void validateCacheStrategyThrowsForInvalidStrategy() {
        WanakuCamelCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec =
                new WanakuCamelCodeExecutionEngineSpec.DependencyCacheSpec();
        cacheSpec.setStrategy("invalid-strategy");
        WanakuException ex = assertThrows(WanakuException.class, () -> OperatorUtil.validateCacheStrategy(cacheSpec));
        assertTrue(ex.getMessage().contains("dependencyCache.strategy"));
    }

    @Test
    void validateCacheStrategyAcceptsNullSpec() {
        assertDoesNotThrow(() -> OperatorUtil.validateCacheStrategy(null));
    }

    @Test
    void validateCacheStrategyAcceptsNullStrategy() {
        WanakuCamelCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec =
                new WanakuCamelCodeExecutionEngineSpec.DependencyCacheSpec();
        cacheSpec.setStrategy(null);
        assertDoesNotThrow(() -> OperatorUtil.validateCacheStrategy(cacheSpec));
    }

    @Test
    void validateCacheStrategyAcceptsBlankStrategy() {
        WanakuCamelCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec =
                new WanakuCamelCodeExecutionEngineSpec.DependencyCacheSpec();
        cacheSpec.setStrategy(" ");
        assertDoesNotThrow(() -> OperatorUtil.validateCacheStrategy(cacheSpec));
    }

    @Test
    void validateSecurityListsDetectsOverlap() {
        WanakuCamelCodeExecutionEngineSpec.SecuritySpec securitySpec =
                new WanakuCamelCodeExecutionEngineSpec.SecuritySpec();
        securitySpec.setComponentAllowlist(List.of("component-a", "component-b"));
        securitySpec.setComponentBlocklist(List.of("component-b", "component-c"));
        WanakuException ex = assertThrows(WanakuException.class, () -> reconciler.validateSecurityLists(securitySpec));
        assertTrue(ex.getMessage().contains("component"));
    }

    @Test
    void validateSecurityListsPassesWhenNoOverlap() {
        WanakuCamelCodeExecutionEngineSpec.SecuritySpec securitySpec =
                new WanakuCamelCodeExecutionEngineSpec.SecuritySpec();
        securitySpec.setComponentAllowlist(List.of("component-a"));
        securitySpec.setComponentBlocklist(List.of("component-b"));
        assertDoesNotThrow(() -> reconciler.validateSecurityLists(securitySpec));
    }

    @Test
    void validateSecurityListsAcceptsNullSpec() {
        assertDoesNotThrow(() -> reconciler.validateSecurityLists(null));
    }

    @Test
    void validateSecurityListsPassesWithEmptyLists() {
        WanakuCamelCodeExecutionEngineSpec.SecuritySpec securitySpec =
                new WanakuCamelCodeExecutionEngineSpec.SecuritySpec();
        securitySpec.setComponentAllowlist(List.of());
        securitySpec.setComponentBlocklist(null);
        assertDoesNotThrow(() -> reconciler.validateSecurityLists(securitySpec));
    }
}
