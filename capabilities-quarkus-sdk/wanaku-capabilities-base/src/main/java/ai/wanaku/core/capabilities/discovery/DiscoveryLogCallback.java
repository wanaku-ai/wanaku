package ai.wanaku.core.capabilities.discovery;

import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.discovery.DiscoveryCallback;
import ai.wanaku.capabilities.sdk.api.discovery.RegistrationManager;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

class DiscoveryLogCallback implements DiscoveryCallback {
    private static final Logger LOG = Logger.getLogger(DiscoveryLogCallback.class);

    @Override
    public void onPing(RegistrationManager manager, ServiceTarget target, int status) {
        if (status != 200) {
            LOG.warnf("Pinging router failed with status %d", status);
        } else {
            LOG.tracef("Pinging router completed successfully");
        }
    }

    @Override
    public void onRegistration(RegistrationManager manager, ServiceTarget target) {
        LOG.infof("The service %s successfully registered with ID %s.", target.getServiceName(), target.getId());
    }

    @Override
    public void onDeregistration(RegistrationManager manager, ServiceTarget target, int status) {
        if (status != 200) {
            LOG.warnf("De-registering service %s failed with status %d", target.getServiceType(), status);
        }
    }
}
