package ai.wanaku.backend.health;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.infinispan.health.CacheHealth;
import org.infinispan.health.ClusterHealth;
import org.infinispan.health.Health;
import org.infinispan.health.HealthStatus;
import org.infinispan.manager.EmbeddedCacheManager;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InfinispanReadinessCheckTest {

    @Test
    void call_returnsUp_whenAllCachesAreHealthy() {
        CacheHealth healthyCache = mockCacheHealth("myCache", HealthStatus.HEALTHY);
        ClusterHealth clusterHealth = mockClusterHealth(HealthStatus.HEALTHY);
        Health health = mockHealth(clusterHealth, List.of(healthyCache));
        EmbeddedCacheManager cacheManager = mockCacheManager(health, Set.of("myCache"));

        InfinispanReadinessCheck check = new InfinispanReadinessCheck();
        check.cacheManager = cacheManager;

        HealthCheckResponse response = check.call();

        assertEquals("infinispan", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.UP);
        assertTrue(response.getData().isPresent());
        assertEquals("myCache", getDataValue(response, "cacheNames"));
        assertEquals("HEALTHY", getDataValue(response, "clusterHealth"));
    }

    @Test
    void call_returnsUp_whenCachesAreHealthyRebalancing() {
        CacheHealth rebalancingCache = mockCacheHealth("rebalancingCache", HealthStatus.HEALTHY_REBALANCING);
        ClusterHealth clusterHealth = mockClusterHealth(HealthStatus.HEALTHY_REBALANCING);
        Health health = mockHealth(clusterHealth, List.of(rebalancingCache));
        EmbeddedCacheManager cacheManager = mockCacheManager(health, Set.of("rebalancingCache"));

        InfinispanReadinessCheck check = new InfinispanReadinessCheck();
        check.cacheManager = cacheManager;

        HealthCheckResponse response = check.call();

        assertEquals("infinispan", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.UP);
    }

    @Test
    void call_returnsUp_whenCacheIsDegraded() {
        CacheHealth degradedCache = mockCacheHealth("degradedCache", HealthStatus.DEGRADED);
        ClusterHealth clusterHealth = mockClusterHealth(HealthStatus.DEGRADED);
        Health health = mockHealth(clusterHealth, List.of(degradedCache));
        EmbeddedCacheManager cacheManager = mockCacheManager(health, Set.of("degradedCache"));

        InfinispanReadinessCheck check = new InfinispanReadinessCheck();
        check.cacheManager = cacheManager;

        HealthCheckResponse response = check.call();

        assertEquals("infinispan", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.UP);
        assertEquals("degradedCache", getDataValue(response, "degradedCaches"));
    }

    @Test
    void call_returnsDown_whenCacheIsFailed() {
        CacheHealth failedCache = mockCacheHealth("failedCache", HealthStatus.FAILED);
        ClusterHealth clusterHealth = mockClusterHealth(HealthStatus.FAILED);
        Health health = mockHealth(clusterHealth, List.of(failedCache));
        EmbeddedCacheManager cacheManager = mockCacheManager(health, Set.of("failedCache"));

        InfinispanReadinessCheck check = new InfinispanReadinessCheck();
        check.cacheManager = cacheManager;

        HealthCheckResponse response = check.call();

        assertEquals("infinispan", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.DOWN);
        assertEquals("failedCache", getDataValue(response, "failedCaches"));
    }

    @Test
    void call_returnsDown_withMultipleDegradedCachesAndAFailedOne() {
        CacheHealth degradedCache = mockCacheHealth("cacheA", HealthStatus.DEGRADED);
        CacheHealth failedCache = mockCacheHealth("cacheB", HealthStatus.FAILED);
        ClusterHealth clusterHealth = mockClusterHealth(HealthStatus.DEGRADED);
        Health health = mockHealth(clusterHealth, List.of(degradedCache, failedCache));
        EmbeddedCacheManager cacheManager = mockCacheManager(health, Set.of("cacheA", "cacheB"));

        InfinispanReadinessCheck check = new InfinispanReadinessCheck();
        check.cacheManager = cacheManager;

        HealthCheckResponse response = check.call();

        assertTrue(response.getStatus() == HealthCheckResponse.Status.DOWN);
        String degradedCaches = getDataValue(response, "failedCaches");
        assertNotNull(degradedCaches);
        // Cache A is degraded and should not appear as failed
        assertFalse(degradedCaches.contains("cacheA"));
        assertTrue(degradedCaches.contains("cacheB"));
    }

    @Test
    void call_returnsDown_whenExceptionThrown() {
        EmbeddedCacheManager cacheManager = mock(EmbeddedCacheManager.class);
        when(cacheManager.getHealth()).thenThrow(new RuntimeException("Infinispan not available"));

        InfinispanReadinessCheck check = new InfinispanReadinessCheck();
        check.cacheManager = cacheManager;

        HealthCheckResponse response = check.call();

        assertEquals("infinispan", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.DOWN);
        assertEquals("Infinispan not available", getDataValue(response, "error"));
    }

    @Test
    void call_returnsUp_whenNoCachesExist() {
        ClusterHealth clusterHealth = mockClusterHealth(HealthStatus.HEALTHY);
        Health health = mockHealth(clusterHealth, Collections.emptyList());
        EmbeddedCacheManager cacheManager = mockCacheManager(health, Collections.emptySet());

        InfinispanReadinessCheck check = new InfinispanReadinessCheck();
        check.cacheManager = cacheManager;

        HealthCheckResponse response = check.call();

        assertEquals("infinispan", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.UP);
        assertEquals("", getDataValue(response, "cacheNames"));
    }

    @Test
    void call_returnsUp_withMixedHealthyAndInitializingCaches() {
        CacheHealth healthyCache = mockCacheHealth("readyCache", HealthStatus.HEALTHY);
        CacheHealth initializingCache = mockCacheHealth("initCache", HealthStatus.INITIALIZING);
        ClusterHealth clusterHealth = mockClusterHealth(HealthStatus.HEALTHY);
        Health health = mockHealth(clusterHealth, List.of(healthyCache, initializingCache));
        EmbeddedCacheManager cacheManager = mockCacheManager(health, Set.of("readyCache", "initCache"));

        InfinispanReadinessCheck check = new InfinispanReadinessCheck();
        check.cacheManager = cacheManager;

        HealthCheckResponse response = check.call();

        assertTrue(response.getStatus() == HealthCheckResponse.Status.UP);
    }

    @Test
    void call_returnsUp_withMixedHealthyAndDegradedCaches() {
        CacheHealth healthyCache = mockCacheHealth("healthyCache", HealthStatus.HEALTHY);
        CacheHealth degradedCache = mockCacheHealth("degradedCache", HealthStatus.DEGRADED);
        ClusterHealth clusterHealth = mockClusterHealth(HealthStatus.DEGRADED);
        Health health = mockHealth(clusterHealth, List.of(healthyCache, degradedCache));
        EmbeddedCacheManager cacheManager = mockCacheManager(health, Set.of("healthyCache", "degradedCache"));

        InfinispanReadinessCheck check = new InfinispanReadinessCheck();
        check.cacheManager = cacheManager;

        HealthCheckResponse response = check.call();

        assertTrue(response.getStatus() == HealthCheckResponse.Status.UP);
        assertFalse(response.getData().get().containsKey("cacheNames"));
        assertEquals("degradedCache", getDataValue(response, "degradedCaches"));
    }

    @Test
    void call_returnsUp_withMultipleCacheNames() {
        CacheHealth cache1 = mockCacheHealth("cache1", HealthStatus.HEALTHY);
        CacheHealth cache2 = mockCacheHealth("cache2", HealthStatus.HEALTHY);
        ClusterHealth clusterHealth = mockClusterHealth(HealthStatus.HEALTHY);
        Health health = mockHealth(clusterHealth, List.of(cache1, cache2));
        EmbeddedCacheManager cacheManager = mockCacheManager(health, Set.of("cache1", "cache2"));

        InfinispanReadinessCheck check = new InfinispanReadinessCheck();
        check.cacheManager = cacheManager;

        HealthCheckResponse response = check.call();

        assertTrue(response.getStatus() == HealthCheckResponse.Status.UP);
        String cacheNames = getDataValue(response, "cacheNames");
        assertTrue(cacheNames.contains("cache1"));
        assertTrue(cacheNames.contains("cache2"));
    }

    private CacheHealth mockCacheHealth(String name, HealthStatus status) {
        CacheHealth ch = mock(CacheHealth.class);
        when(ch.getCacheName()).thenReturn(name);
        when(ch.getStatus()).thenReturn(status);
        return ch;
    }

    private ClusterHealth mockClusterHealth(HealthStatus status) {
        ClusterHealth ch = mock(ClusterHealth.class);
        when(ch.getHealthStatus()).thenReturn(status);
        return ch;
    }

    private Health mockHealth(ClusterHealth clusterHealth, List<CacheHealth> cacheHealths) {
        Health health = mock(Health.class);
        when(health.getClusterHealth()).thenReturn(clusterHealth);
        when(health.getCacheHealth()).thenReturn(cacheHealths);
        return health;
    }

    private EmbeddedCacheManager mockCacheManager(Health health, Set<String> cacheNames) {
        EmbeddedCacheManager cm = mock(EmbeddedCacheManager.class);
        when(cm.getHealth()).thenReturn(health);
        when(cm.getCacheNames()).thenReturn(cacheNames);
        return cm;
    }

    private String getDataValue(HealthCheckResponse response, String key) {
        return response.getData()
                .map(data -> data.get(key))
                .map(Object::toString)
                .orElse(null);
    }
}
