package ai.wanaku.server.quarkus.api.v1.management.targets;

import ai.wanaku.api.types.management.State;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.util.IndexHelper;
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

    public void configureTools(String service, String option, String value)
            throws IOException {
        Map<String, String> configurations = toolsConfigurations(service);
        configurations.put(option, value);

        IndexHelper.saveTargetsIndex(toolsResolver.targetsIndexFile(), configurations);
    }

    public void configureResources(String service, String option, String value)
            throws IOException {
        Map<String, String> configurations = resourcesConfigurations(service);
        configurations.put(option, value);

        IndexHelper.saveTargetsIndex(toolsResolver.targetsIndexFile(), configurations);
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
