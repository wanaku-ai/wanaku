package ai.wanaku.core.capabilities.discovery;

import ai.wanaku.api.discovery.DiscoveryCallback;
import ai.wanaku.api.discovery.RegistrationManager;
import ai.wanaku.api.types.providers.ServiceTarget;
import org.jboss.logging.Logger;

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
        LOG.infof("The service %s successfully registered with ID %s.", target.getService(), target.getId());
    }

    @Override
    public void onDeregistration(RegistrationManager manager, ServiceTarget target, int status) {
        if (status != 200) {
            LOG.warnf(
                    "De-registering service %s failed with status %d",
                    target.getServiceType().asValue(), status);
        }
    }
}
