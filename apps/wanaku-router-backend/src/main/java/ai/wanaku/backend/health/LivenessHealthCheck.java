package ai.wanaku.backend.health;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class LivenessHealthCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.builder()
                .name("wanaku-router")
                .up()
                .withData("availableProcessors", Runtime.getRuntime().availableProcessors())
                .withData("maxMemory", Runtime.getRuntime().maxMemory())
                .withData("totalMemory", Runtime.getRuntime().totalMemory())
                .withData("freeMemory", Runtime.getRuntime().freeMemory())
                .build();
    }
}
