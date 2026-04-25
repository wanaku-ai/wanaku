package ai.wanaku.backend.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import ai.wanaku.backend.core.mcp.providers.ServiceRegistry;

@Readiness
@ApplicationScoped
public class ServiceRegistryReadinessCheck implements HealthCheck {

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder().name("service-registry");

        try {
            if (serviceRegistryInstance.isUnsatisfied()) {
                builder.down().withData("reason", "ServiceRegistry CDI bean not available");
                return builder.build();
            }

            ServiceRegistry registry = serviceRegistryInstance.get();
            int totalCapabilities = registry.getEntries().size();

            builder.up().withData("totalCapabilities", totalCapabilities);
        } catch (Exception e) {
            builder.down().withData("error", e.getMessage());
        }

        return builder.build();
    }
}
