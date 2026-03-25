package ai.wanaku.backend.health;

import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.capabilities.sdk.api.types.discovery.HealthStatus;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.HealthProbeReply;
import ai.wanaku.core.exchange.v1.HealthProbeRequest;
import ai.wanaku.core.exchange.v1.RuntimeStatus;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthProbeClientTest {

    private static final ServiceTarget TARGET =
            new ServiceTarget("test-id", "test-service", "localhost", 9090, "tool-invoker", "mcp", null, null, null);

    @Test
    void probe_returnsHealthy_whenServiceReportsStarted() {
        WanakuBridgeTransport transport = mock(WanakuBridgeTransport.class);
        HealthProbeReply reply = HealthProbeReply.newBuilder()
                .setStatus(RuntimeStatus.RUNTIME_STATUS_STARTED)
                .build();
        when(transport.probeHealth(any(HealthProbeRequest.class), eq(TARGET))).thenReturn(reply);

        HealthProbeClient client = new HealthProbeClient(transport);
        HealthStatus status = client.probe(TARGET);

        assertEquals(HealthStatus.HEALTHY, status);
        verify(transport).probeHealth(any(HealthProbeRequest.class), eq(TARGET));
    }

    @Test
    void probe_returnsUnhealthy_whenServiceReportsNonStartedStatus() {
        WanakuBridgeTransport transport = mock(WanakuBridgeTransport.class);
        HealthProbeReply reply = HealthProbeReply.newBuilder()
                .setStatus(RuntimeStatus.RUNTIME_STATUS_STOPPED)
                .build();
        when(transport.probeHealth(any(HealthProbeRequest.class), eq(TARGET))).thenReturn(reply);

        HealthProbeClient client = new HealthProbeClient(transport);
        HealthStatus status = client.probe(TARGET);

        assertEquals(HealthStatus.UNHEALTHY, status);
    }

    @Test
    void probe_returnsDown_whenTransportThrowsException() {
        WanakuBridgeTransport transport = mock(WanakuBridgeTransport.class);
        when(transport.probeHealth(any(HealthProbeRequest.class), eq(TARGET)))
                .thenThrow(new RuntimeException("connection refused"));

        HealthProbeClient client = new HealthProbeClient(transport);
        HealthStatus status = client.probe(TARGET);

        assertEquals(HealthStatus.DOWN, status);
    }
}
