package ai.wanaku.server.quarkus.api.v1.management.targets;

import ai.wanaku.api.types.management.State;
import ai.wanaku.api.exceptions.ResourceNotFoundException;
import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.persistence.api.ServiceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public void configureTools(String service, String option, String value)
            throws IOException {
        // TODO why is it needed?
//        Map<String, String> configurations = toolsConfigurations(service);
//        configurations.put(option, value);

        Service storedService = serviceRepository.findByIdAndServiceType(service, ServiceType.TOOL_INVOKER);
        if (storedService == null) {
            throw new ToolNotFoundException("Tool not found: " + service);
        }
        if (!storedService.getConfigurations().getConfigurations().containsKey(option)) {
            throw new WanakuException("The option '" + option + "' cannot be configured on the Service: " + service);
        }
        storedService.getConfigurations().getConfigurations().get(option).setValue(value);

        serviceRepository.update(storedService);
    }

    public void configureResources(String service, String option, String value)
            throws IOException {
        // TODO why is it needed?
//        Map<String, String> configurations = resourcesConfigurations(service);
//        configurations.put(option, value);

        Service serviceEntity = serviceRepository.findByIdAndServiceType(service, ServiceType.RESOURCE_PROVIDER);
        if (serviceEntity == null) {
            throw new ResourceNotFoundException("Resource not found: " + service);
        }
        if (!serviceEntity.getConfigurations().getConfigurations().containsKey(option)) {
            throw new WanakuException("The option '" + option + "' cannot be configured on the Service: " + service);
        }
        serviceEntity.getConfigurations().getConfigurations().get(option).setValue(value);

        serviceRepository.update(serviceEntity);
    }

    public Map<String, Service> toolList() {
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

    public Map<String, List<State>> toolsState() {
        Map<String, List<State>> states = new HashMap<>();
        Map<String, Service> toolsServices = toolList();
        buildState(toolsServices, states);

        return states;
    }

    public Map<String, List<State>> resourcesState() {
        Map<String, List<State>> states = new HashMap<>();

        Map<String, Service> resourcesServices = resourcesList();
        buildState(resourcesServices, states);

        return states;
    }

    private void buildState(Map<String, Service> stringServiceMap, Map<String, List<State>> states) {
        for (var entry : stringServiceMap.entrySet()) {
            List<State> state = serviceRegistry.getState(entry.getKey(), 10);

            states.put(entry.getKey(), state);
        }
    }


}
