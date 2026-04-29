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

    private static final Set<HealthStatus> UNHEALTHY_STATUSES = Set.of(HealthStatus.FAILED, HealthStatus.DEGRADED);

    @Inject
    EmbeddedCacheManager cacheManager;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder().name("infinispan");

        try {
            Health health = cacheManager.getHealth();
            if (isFailed(health)) {
                builder.down();
                appendDegradedCaches(builder, health, HealthStatus.FAILED);
                appendDegradedCaches(builder, health, HealthStatus.DEGRADED);
            } else {
                builder.up();
                if (isDegraded(health)) {
                    appendDegradedCaches(builder, health, HealthStatus.DEGRADED);
                } else {
                    appendHealthyData(builder, health);
                }
            }
        } catch (Exception e) {
            builder.down().withData("error", e.getMessage());
        }

        return builder.build();
    }

    private boolean isFailed(Health health) {
        return health.getCacheHealth().stream().anyMatch(ch -> ch.getStatus().equals(HealthStatus.FAILED));
    }

    private boolean isDegraded(Health health) {
        return health.getCacheHealth().stream().anyMatch(ch -> ch.getStatus().equals(HealthStatus.DEGRADED));
    }

    private void appendDegradedCaches(HealthCheckResponseBuilder builder, Health health, HealthStatus healthStatus) {
        String degradedCaches = health.getCacheHealth().stream()
                .filter(ch -> ch.getStatus().equals(healthStatus))
                .map(CacheHealth::getCacheName)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        builder.withData(labelForStatus(healthStatus), degradedCaches);
    }

    private static String labelForStatus(HealthStatus status) {
        if (status.equals(HealthStatus.DEGRADED)) {
            return "degradedCaches";
        }

        if (status.equals(HealthStatus.FAILED)) {
            return "failedCaches";
        }

        return "unspecifiedStatus";
    }

    private void appendHealthyData(HealthCheckResponseBuilder builder, Health health) {
        builder.withData("cacheNames", String.join(",", cacheManager.getCacheNames()));
        builder.withData(
                "clusterHealth", health.getClusterHealth().getHealthStatus().toString());
    }
}
