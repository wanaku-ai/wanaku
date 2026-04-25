package ai.wanaku.backend.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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

    @Inject
    EmbeddedCacheManager cacheManager;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder().name("infinispan");

        try {
            Health health = cacheManager.getHealth();
            boolean degraded = health.getCacheHealth().stream()
                    .anyMatch(ch -> ch.getStatus() == HealthStatus.DEGRADED || ch.getStatus() == HealthStatus.FAILED);

            builder.status(!degraded);
            if (!degraded) {
                builder.withData("cacheNames", String.join(",", cacheManager.getCacheNames()));
                builder.withData(
                        "clusterHealth",
                        health.getClusterHealth().getHealthStatus().toString());
            } else {
                String degradedCaches = health.getCacheHealth().stream()
                        .filter(ch -> ch.getStatus() == HealthStatus.DEGRADED || ch.getStatus() == HealthStatus.FAILED)
                        .map(CacheHealth::getCacheName)
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
                builder.withData("degradedCaches", degradedCaches);
            }
        } catch (Exception e) {
            builder.down().withData("error", e.getMessage());
        }

        return builder.build();
    }
}
