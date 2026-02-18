package ai.wanaku.backend.health;

import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import ai.wanaku.capabilities.sdk.api.types.discovery.HealthStatus;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.HealthProbeGrpc;
import ai.wanaku.core.exchange.v1.HealthProbeReply;
import ai.wanaku.core.exchange.v1.HealthProbeRequest;
import ai.wanaku.core.exchange.v1.RuntimeStatus;

/**
 * Client for probing the health of remote capabilities via gRPC.
 */
class HealthProbeClient {
    private static final Logger LOG = Logger.getLogger(HealthProbeClient.class);

    private final int timeoutSeconds;

    HealthProbeClient(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Probes the health of a remote capability.
     *
     * @param target the service target to probe
     * @return the health status based on the probe result
     */
    HealthStatus probe(ServiceTarget target) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forTarget(target.toAddress())
                    .usePlaintext()
                    .build();

            HealthProbeGrpc.HealthProbeBlockingStub stub =
                    HealthProbeGrpc.newBlockingStub(channel).withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS);

            HealthProbeReply reply = stub.getStatus(
                    HealthProbeRequest.newBuilder().setId(target.getId()).build());

            return mapRuntimeStatus(reply.getStatus());
        } catch (StatusRuntimeException e) {
            LOG.warnf("Health probe failed for %s: %s %s", target.toAddress(), e.getMessage(), e.getStatus());
            return HealthStatus.DOWN;
        } catch (Exception e) {
            LOG.debugf(e, "Health probe error for %s", target.toAddress());
            return HealthStatus.DOWN;
        } finally {
            if (channel != null && !channel.isShutdown()) {
                try {
                    channel.shutdown();
                } catch (Exception e) {
                    LOG.warnf(e, "Error closing gRPC channel for %s", target.toAddress());
                }
            }
        }
    }

    private static HealthStatus mapRuntimeStatus(RuntimeStatus status) {
        return switch (status) {
            case RUNTIME_STATUS_STARTED -> HealthStatus.HEALTHY;
            default -> HealthStatus.UNHEALTHY;
        };
    }
}
