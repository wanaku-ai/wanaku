package ai.wanaku.core.capabilities.common;

import org.eclipse.microprofile.health.HealthCheckResponse;
import io.quarkus.grpc.runtime.GrpcServer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityLivenessCheckTest {

    @Test
    void call_returnsUp_whenGrpcServerIsRunning() {
        GrpcServer grpcServer = mock(GrpcServer.class);
        when(grpcServer.getPort()).thenReturn(9190);

        CapabilityLivenessCheck check = new CapabilityLivenessCheck();
        check.grpcServer = grpcServer;

        HealthCheckResponse response = check.call();

        assertEquals("capability-grpc-liveness", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.UP);
        assertEquals(9190, ((Long) response.getData().get().get("grpcPort")).intValue());
    }

    @Test
    void call_returnsDown_whenGrpcServerPortIsZero() {
        GrpcServer grpcServer = mock(GrpcServer.class);
        when(grpcServer.getPort()).thenReturn(0);

        CapabilityLivenessCheck check = new CapabilityLivenessCheck();
        check.grpcServer = grpcServer;

        HealthCheckResponse response = check.call();

        assertEquals("capability-grpc-liveness", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.DOWN);
        assertEquals("not available", response.getData().get().get("grpcPort"));
    }

    @Test
    void call_returnsDown_whenGrpcServerThrowsException() {
        GrpcServer grpcServer = mock(GrpcServer.class);
        when(grpcServer.getPort()).thenThrow(new RuntimeException("Server not started"));

        CapabilityLivenessCheck check = new CapabilityLivenessCheck();
        check.grpcServer = grpcServer;

        HealthCheckResponse response = check.call();

        assertEquals("capability-grpc-liveness", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.DOWN);
        assertEquals("not available", response.getData().get().get("grpcPort"));
    }

    @Test
    void call_returnsDown_whenGrpcServerPortIsNegative() {
        GrpcServer grpcServer = mock(GrpcServer.class);
        when(grpcServer.getPort()).thenReturn(-1);

        CapabilityLivenessCheck check = new CapabilityLivenessCheck();
        check.grpcServer = grpcServer;

        HealthCheckResponse response = check.call();

        assertEquals("capability-grpc-liveness", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.DOWN);
        assertEquals("not available", response.getData().get().get("grpcPort"));
    }
}
