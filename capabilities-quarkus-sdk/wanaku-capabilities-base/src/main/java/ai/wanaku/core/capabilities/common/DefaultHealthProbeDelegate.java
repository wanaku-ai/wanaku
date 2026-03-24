package ai.wanaku.core.capabilities.common;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.exchange.HealthProbeDelegate;
import ai.wanaku.core.exchange.v1.RuntimeStatus;

/**
 * Default health probe delegate implementation.
 * <p>
 * Returns {@link RuntimeStatus#RUNTIME_STATUS_STARTED} when the capability is responding.
 * If a capability can respond to the probe, it is considered started.
 */
@ApplicationScoped
public class DefaultHealthProbeDelegate implements HealthProbeDelegate {

    @Override
    public RuntimeStatus getStatus(String id) {
        return RuntimeStatus.RUNTIME_STATUS_STARTED;
    }
}
