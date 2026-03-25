package ai.wanaku.backend.bridge;

import org.jboss.logging.Logger;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

/**
 * Implementation of {@link ProvisionBridge} that consolidates provisioning logic
 * shared between {@link ResourceAcquirerBridge} and {@link InvokerBridge}.
 * <p>
 * This class encapsulates service resolution and configuration/secrets provisioning,
 * eliminating code duplication across bridge implementations.
 */
public class ProvisionerBridge implements ProvisionBridge {
    private static final Logger LOG = Logger.getLogger(ProvisionerBridge.class);

    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;

    public ProvisionerBridge(ServiceResolver serviceResolver, WanakuBridgeTransport transport) {
        this.serviceResolver = serviceResolver;
        this.transport = transport;
    }

    @Override
    public ProvisioningReference provision(String name, String configData, String secretsData, ServiceTarget service) {
        return transport.provision(name, configData, secretsData, service);
    }

    /**
     * Resolves a service target for the specified type and service type.
     *
     * @param type the service type identifier
     * @param serviceType the category of service (e.g., "tool-invoker", "resource-provider")
     * @return the resolved service target
     * @throws ServiceNotFoundException if no service is registered for the given type
     */
    public ServiceTarget resolveService(String type, String serviceType) {
        LOG.debugf("Resolving service for type '%s' and service type '%s'", type, serviceType);
        ServiceTarget service = serviceResolver.resolve(type, serviceType);
        if (service == null) {
            throw new ServiceNotFoundException("There is no host registered for service " + type);
        }
        LOG.debugf("Resolved service: %s", service.toAddress());
        return service;
    }
}
