package ai.wanaku.server.quarkus.api.v1.management.targets;

import ai.wanaku.api.exceptions.ResourceNotFoundException;
import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.ServiceEntity;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.service.discovery.ServiceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.Map;

@Transactional
@ApplicationScoped
public class TargetsBean {
    private static final Logger LOG = Logger.getLogger(TargetsBean.class);

    @Inject
    ResourceResolver resourceResolver;

    @Inject
    ToolsResolver toolsResolver;

    @Inject
    ServiceRegistry serviceRegistry;

    @Inject
    ServiceRepository serviceRepository;

    public void configureTools(String service, String option, String value) {
        // TODO why is it needed?
        Map<String, String> configurations = toolsConfigurations(service);

        ServiceEntity serviceEntity = serviceRepository.findByIdAndServiceType(service, ServiceType.TOOL_INVOKER);
        if (serviceEntity == null) {
            throw new ToolNotFoundException("Tool not found: " + service);
        }
        if (!serviceEntity.getConfigurations().containsKey(option)) {
            throw new WanakuException("The option '" + option + "' cannot be configured on the Service: " + service);
        }
        serviceEntity.getConfigurations().get(option).setValue(value);

        serviceRepository.update(serviceEntity);
    }

    public void configureResources(String service, String option, String value) {
        // TODO why is it needed?
//        Map<String, String> configurations = resourcesConfigurations(service);

        ServiceEntity serviceEntity = serviceRepository.findByIdAndServiceType(service, ServiceType.RESOURCE_PROVIDER);
        if (serviceEntity == null) {
            throw new ResourceNotFoundException("Resource not found: " + service);
        }
        if (!serviceEntity.getConfigurations().containsKey(option)) {
            throw new WanakuException("The option '" + option + "' cannot be configured on the Service: " + service);
        }
        serviceEntity.getConfigurations().get(option).setValue(value);

        serviceRepository.update(serviceEntity);
    }

    public Map<String,Service> toolList() {
        return serviceRegistry.getEntries(ServiceType.TOOL_INVOKER);
    }

    public Map<String, Service> resourcesList() {
        return serviceRegistry.getEntries(ServiceType.RESOURCE_PROVIDER);
    }

    public Map<String, String> toolsConfigurations(String target) {
        Map<String, String> configurations = toolsResolver.getServiceConfigurations(target);
        for (var entry : configurations.entrySet()) {
            LOG.infof("Received tool configuration %s from %s: %s", entry.getKey(), target, entry.getValue());
        }
        return configurations;
    }

    public Map<String, String> resourcesConfigurations(String target) {
        Map<String, String> configurations = resourceResolver.getServiceConfigurations(target);
        for (var entry : configurations.entrySet()) {
            LOG.infof("Received resource configuration %s from %s: %s", entry.getKey(), target, entry.getValue());
        }
        return configurations;
    }

}
