package ai.wanaku.server.quarkus.api.v1.management.targets;

import ai.wanaku.api.exceptions.ConfigurationNotFoundException;
import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.providers.ResourceRegistry;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
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

    public void configureTools(String service, String option, String value)
            throws IOException {
        Service entry = ServiceRegistry.getInstance().getEntryForService(service);
        Configuration configuration = configurationForService(service, option, entry);

        configuration.setValue(value);
        ServiceRegistry.getInstance().link(service, entry);
        IndexHelper.saveTargetsIndex(toolsResolver.targetsIndexFile(), ServiceRegistry.getInstance().getEntries());
    }

    public void configureResources(String service, String option, String value)
            throws IOException {
        Service entry = ResourceRegistry.getInstance().getEntryForService(service);
        Configuration configuration = configurationForService(service, option, entry);

        configuration.setValue(value);
        ResourceRegistry.getInstance().link(service, entry);
        IndexHelper.saveTargetsIndex(toolsResolver.targetsIndexFile(), ResourceRegistry.getInstance().getEntries());
    }

    private static Configuration configurationForService(String service, String option, Service entry) {
        if (entry == null) {
            throw new ServiceNotFoundException(String.format("There is no service named %s", service));
        }

        Configuration configuration = entry.getConfigurations().getConfigurations().get(option);
        if (configuration == null) {
            throw new ConfigurationNotFoundException(String.format("There is no configuration named %s for %s", option, service));
        }
        return configuration;
    }

    public void toolsLink(String service, String target) throws IOException {
        Map<String, String> configurations = toolsConfigurations(target);

        Service srv = newService(target, configurations);

        ServiceRegistry.getInstance().link(service, srv);
        IndexHelper.saveTargetsIndex(toolsResolver.targetsIndexFile(), ServiceRegistry.getInstance().getEntries());
        toolsConfigurations(target);
    }

    public void toolsUnlink(String service) throws IOException {
        ServiceRegistry.getInstance().unlink(service);
        IndexHelper.saveTargetsIndex(toolsResolver.targetsIndexFile(), ServiceRegistry.getInstance().getEntries());
    }

    public Map<String,Service> toolList() {
        return ServiceRegistry.getInstance().getEntries();
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

    public void resourcesLink(String service, String target) throws IOException {
        Map<String, String> configurations = resourcesConfigurations(target);

        Service srv = newService(target, configurations);

        ResourceRegistry.getInstance().link(service, srv);
        IndexHelper.saveTargetsIndex(resourceResolver.targetsIndexFile(), ResourceRegistry.getInstance().getEntries());
    }

    public void resourcesUnlink(String service) throws IOException {
        ResourceRegistry.getInstance().unlink(service);
        IndexHelper.saveTargetsIndex(resourceResolver.targetsIndexFile(), ResourceRegistry.getInstance().getEntries());
    }

    public Map<String, Service> resourcesList() {
        return ResourceRegistry.getInstance().getEntries();
    }

    void loadResources(@Observes StartupEvent ev) {
        var resourcesMap = doLoad("Resources", resourceResolver.targetsIndexFile());

        for (var entry : resourcesMap.entrySet()) {
            ResourceRegistry.getInstance().link(entry.getKey(), entry.getValue());
        }

        var toolsMap = doLoad("Tools", toolsResolver.targetsIndexFile());
        for (var entry : toolsMap.entrySet()) {
            ServiceRegistry.getInstance().link(entry.getKey(), entry.getValue());
        }
    }

    private Map<String, Service> doLoad(String name, File indexFile) {
        if (!indexFile.exists()) {
            LOG.warnf("%s targets index file not found: %s", name, indexFile);
            return Map.of();
        }

        try {
            return IndexHelper.loadTargetsIndex(indexFile, Service.class);
        } catch (MismatchedInputException e) {
            // OLD format, we can delete it
            LOG.errorf(e, "Deleting target file in an older format %s.", indexFile);
            indexFile.delete();
            return Map.of();
        } catch (Exception e) {
            throw new WanakuException(String.format("Failed to load targets index from %s.", indexFile));
        }
    }
}
