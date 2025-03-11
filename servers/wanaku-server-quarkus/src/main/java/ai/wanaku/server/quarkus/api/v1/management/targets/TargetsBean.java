package ai.wanaku.server.quarkus.api.v1.management.targets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.ConfigurationNotFoundException;
import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.util.IndexHelper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static ai.wanaku.api.types.management.Service.newService;

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

        // TODO is it really needed?
//        IndexHelper.saveTargetsIndex(toolsResolver.targetsIndexFile(), configurations);
    }

    public void configureResources(String service, String option, String value)
            throws IOException {
        Map<String, String> configurations = resourcesConfigurations(service);
        configurations.put(option, value);

        // TODO is it really needed?
//        IndexHelper.saveTargetsIndex(toolsResolver.targetsIndexFile(), configurations);
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
