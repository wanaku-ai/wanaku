package ai.wanaku.backend.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.infinispan.health.CacheHealth;
import org.infinispan.health.Health;
import org.infinispan.health.HealthStatus;
import org.infinispan.manager.EmbeddedCacheManager;

@Readiness
@ApplicationScoped
public class InfinispanReadinessCheck implements HealthCheck {

    private static final Set<HealthStatus> UNHEALTHY_STATUSES = Set.of(HealthStatus.DEGRADED, HealthStatus.FAILED);

    @Inject
    EmbeddedCacheManager cacheManager;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder().name("infinispan");

        try {
            Health health = cacheManager.getHealth();
            if (isDegraded(health)) {
                builder.down();
                appendDegradedCaches(builder, health);
            } else {
                builder.up();
                appendHealthyData(builder, health);
            }
        } catch (Exception e) {
            builder.down().withData("error", e.getMessage());
        }

        return builder.build();
    }

    private boolean isDegraded(Health health) {
        return health.getCacheHealth().stream().anyMatch(ch -> UNHEALTHY_STATUSES.contains(ch.getStatus()));
    }

    private void appendDegradedCaches(HealthCheckResponseBuilder builder, Health health) {
        String degradedCaches = health.getCacheHealth().stream()
                .filter(ch -> UNHEALTHY_STATUSES.contains(ch.getStatus()))
                .map(CacheHealth::getCacheName)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        builder.withData("degradedCaches", degradedCaches);
    }

    private void appendHealthyData(HealthCheckResponseBuilder builder, Health health) {
        builder.withData("cacheNames", String.join(",", cacheManager.getCacheNames()));
        builder.withData(
                "clusterHealth", health.getClusterHealth().getHealthStatus().toString());
    }
}
