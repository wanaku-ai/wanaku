package ai.wanaku.backend.health;

import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import org.jboss.logging.Logger;
import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.capabilities.sdk.api.types.discovery.HealthStatus;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.HealthProbeReply;
import ai.wanaku.core.exchange.v1.HealthProbeRequest;
import ai.wanaku.core.exchange.v1.RuntimeStatus;

/**
 * Client for probing the health of remote capabilities via the bridge transport.
 */
class HealthProbeClient {
    private static final Logger LOG = Logger.getLogger(HealthProbeClient.class);

    private final WanakuBridgeTransport transport;

    HealthProbeClient(WanakuBridgeTransport transport) {
        this.transport = transport;
    }

    /**
     * Probes the health of a remote capability.
     *
     * @param target the service target to probe
     * @return the health status based on the probe result
     */
    HealthStatus probe(ServiceTarget target) {
        try {
            HealthProbeRequest request =
                    HealthProbeRequest.newBuilder().setId(target.getId()).build();

            HealthProbeReply reply = transport.probeHealth(request, target);
            return mapRuntimeStatus(reply.getStatus());
        } catch (ServiceUnavailableException e) {
            LOG.warnf("Service is not available at %s: %s", target.toAddress(), e.getMessage());
            return HealthStatus.DOWN;
        } catch (Exception e) {
            LOG.warnf(e, "Health probe failed for %s: %s", target.toAddress(), e.getMessage());
            return HealthStatus.DOWN;
        }
    }

    private static HealthStatus mapRuntimeStatus(RuntimeStatus status) {
        return switch (status) {
            case RUNTIME_STATUS_STARTED -> HealthStatus.HEALTHY;
            default -> HealthStatus.UNHEALTHY;
        };
    }
}
