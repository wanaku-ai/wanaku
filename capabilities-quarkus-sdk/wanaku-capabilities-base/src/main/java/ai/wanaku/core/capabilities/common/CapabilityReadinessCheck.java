package ai.wanaku.core.capabilities.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import io.quarkus.grpc.runtime.GrpcServer;

@Readiness
@ApplicationScoped
public class CapabilityReadinessCheck implements HealthCheck {

    @Inject
    GrpcServer grpcServer;

    @Override
    public HealthCheckResponse call() {
        return CapabilityChecks.grpcPortCheck(grpcServer, "capability-grpc-readiness");
    }
}
