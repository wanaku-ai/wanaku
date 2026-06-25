package ai.wanaku.core.capabilities.common;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import io.quarkus.grpc.runtime.GrpcServer;

class CapabilityChecks {

    private CapabilityChecks() {}

    /**
     * Creates a new response build for checking the grpc port
     * @param grpcServer The gRPC server instance
     * @param name the check name
     * @return a new HealtCheckResponse
     */
    static HealthCheckResponse grpcPortCheck(GrpcServer grpcServer, String name) {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder().name(name);

        try {
            int port = getServerPort(grpcServer);
            if (port > ServicesHelper.PORT_UNAVAILABLE) {
                builder.up().withData("grpcPort", port);
            } else {
                builder.down().withData("grpcPort", "not available");
            }
        } catch (Exception e) {
            builder.down().withData("grpcPort", "not available");
        }

        return builder.build();
    }

    private static int getServerPort(GrpcServer grpcServer) {
        return ServicesHelper.resolveEffectiveServerPort(grpcServer);
    }
}
