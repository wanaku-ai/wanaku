package ai.wanaku.operator.wanaku;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.operator.util.OperatorUtil;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WanakuCodeExecutionEngineReconcilerTest {

    private final WanakuCodeExecutionEngineReconciler reconciler = new WanakuCodeExecutionEngineReconciler();

    private WanakuCodeExecutionEngine baseResource() {
        WanakuCodeExecutionEngine resource = new WanakuCodeExecutionEngine();
        WanakuCodeExecutionEngineSpec spec = new WanakuCodeExecutionEngineSpec();
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
    void validateSpecThrowsWhenSpecIsNull() {
        WanakuCodeExecutionEngine resource = new WanakuCodeExecutionEngine();
        resource.setSpec(null);
        assertThrows(WanakuException.class, () -> reconciler.validateSpec(resource));
    }

    @Test
    void validateSpecThrowsWhenRouterRefIsMissing() {
        WanakuCodeExecutionEngine resource = baseResource();
        resource.getSpec().setRouterRef(null);
        WanakuException ex = assertThrows(WanakuException.class, () -> reconciler.validateSpec(resource));
        assertTrue(ex.getMessage().contains("routerRef"));
    }

    @Test
    void validateSpecThrowsWhenLanguageNameIsMissing() {
        WanakuCodeExecutionEngine resource = baseResource();
        resource.getSpec().setLanguageName(null);
        assertThrows(WanakuException.class, () -> reconciler.validateSpec(resource));
    }

    @Test
    void validateSpecThrowsWhenEngineTypeIsMissing() {
        WanakuCodeExecutionEngine resource = baseResource();
        resource.getSpec().setEngineType(null);
        assertThrows(WanakuException.class, () -> reconciler.validateSpec(resource));
    }

    @Test
    void validateSpecThrowsWhenImageMissingInClusterMode() {
        WanakuCodeExecutionEngine resource = baseResource();
        resource.getSpec().setImage(null);
        assertThrows(WanakuException.class, () -> reconciler.validateSpec(resource));
    }

    @Test
    void validateSpecThrowsWhenRemoteHostMissing() {
        WanakuCodeExecutionEngine resource = baseResource();
        resource.getSpec().setDeploymentMode("remote");
        assertThrows(WanakuException.class, () -> reconciler.validateSpec(resource));
    }

    @Test
    void validateSpecSucceedsForValidInCluster() {
        WanakuCodeExecutionEngine resource = baseResource();
        assertDoesNotThrow(() -> reconciler.validateSpec(resource));
    }

    @Test
    void validateSpecSucceedsForValidRemote() {
        WanakuCodeExecutionEngine resource = baseResource();
        resource.getSpec().setDeploymentMode("remote");
        WanakuCodeExecutionEngineSpec.RemoteSpec remote = new WanakuCodeExecutionEngineSpec.RemoteSpec();
        remote.setHost("engine.example.com");
        resource.getSpec().setRemote(remote);
        assertDoesNotThrow(() -> reconciler.validateSpec(resource));
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
            WanakuCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec =
                    new WanakuCodeExecutionEngineSpec.DependencyCacheSpec();
            cacheSpec.setStrategy(strategy);
            assertDoesNotThrow(() -> OperatorUtil.validateCacheStrategy(cacheSpec));
        }
    }

    @Test
    void validateCacheStrategyThrowsForInvalidStrategy() {
        WanakuCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec =
                new WanakuCodeExecutionEngineSpec.DependencyCacheSpec();
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
        WanakuCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec =
                new WanakuCodeExecutionEngineSpec.DependencyCacheSpec();
        cacheSpec.setStrategy(null);
        assertDoesNotThrow(() -> OperatorUtil.validateCacheStrategy(cacheSpec));
    }

    @Test
    void validateCacheStrategyAcceptsBlankStrategy() {
        WanakuCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec =
                new WanakuCodeExecutionEngineSpec.DependencyCacheSpec();
        cacheSpec.setStrategy(" ");
        assertDoesNotThrow(() -> OperatorUtil.validateCacheStrategy(cacheSpec));
    }

    @Test
    void validateSecurityListsDetectsOverlap() {
        WanakuCodeExecutionEngineSpec.SecuritySpec securitySpec = new WanakuCodeExecutionEngineSpec.SecuritySpec();
        securitySpec.setComponentAllowlist(List.of("component-a", "component-b"));
        securitySpec.setComponentBlocklist(List.of("component-b", "component-c"));
        WanakuException ex = assertThrows(WanakuException.class, () -> reconciler.validateSecurityLists(securitySpec));
        assertTrue(ex.getMessage().contains("component"));
    }

    @Test
    void validateSecurityListsPassesWhenNoOverlap() {
        WanakuCodeExecutionEngineSpec.SecuritySpec securitySpec = new WanakuCodeExecutionEngineSpec.SecuritySpec();
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
        WanakuCodeExecutionEngineSpec.SecuritySpec securitySpec = new WanakuCodeExecutionEngineSpec.SecuritySpec();
        securitySpec.setComponentAllowlist(List.of());
        securitySpec.setComponentBlocklist(null);
        assertDoesNotThrow(() -> reconciler.validateSecurityLists(securitySpec));
    }
}
