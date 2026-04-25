package ai.wanaku.core.capabilities.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import io.quarkus.grpc.runtime.GrpcServer;
import ai.wanaku.core.exchange.HealthProbeDelegate;
import ai.wanaku.core.exchange.v1.RuntimeStatus;

@ApplicationScoped
public class DefaultHealthProbeDelegate implements HealthProbeDelegate {

    private static final Logger LOG = Logger.getLogger(DefaultHealthProbeDelegate.class);

    @Inject
    GrpcServer grpcServer;

    @Override
    public RuntimeStatus getStatus(String id) {
        try {
            int port = grpcServer.getPort();
            if (port > 0) {
                return RuntimeStatus.RUNTIME_STATUS_STARTED;
            }
            return RuntimeStatus.RUNTIME_STATUS_STARTING;
        } catch (Exception e) {
            LOG.debugf(e, "Health probe check failed for %s: %s", id, e.getMessage());
            return RuntimeStatus.RUNTIME_STATUS_STARTING;
        }
    }
}
