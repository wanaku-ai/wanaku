package ai.wanaku.core.capabilities.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import io.quarkus.grpc.runtime.GrpcServer;

@Readiness
@ApplicationScoped
public class CapabilityReadinessCheck implements HealthCheck {

    @Inject
    GrpcServer grpcServer;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder().name("capability-grpc-readiness");

        try {
            int port = grpcServer.getPort();
            if (port > 0) {
                builder.up().withData("grpcPort", port);
            } else {
                builder.down().withData("grpcPort", "not available");
            }
        } catch (Exception e) {
            builder.down().withData("grpcPort", "not available");
        }

        return builder.build();
    }
}
