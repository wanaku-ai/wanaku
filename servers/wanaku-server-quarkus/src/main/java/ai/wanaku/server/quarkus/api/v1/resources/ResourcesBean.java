package ai.wanaku.server.quarkus.api.v1.resources;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.mcp.providers.Repository;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class ResourcesBean {
    private static final Logger LOG = Logger.getLogger(ResourcesBean.class);

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceResolver resourceResolver;

    @Inject
    Repository<ResourceReference> repository;

    public void expose(ResourceReference mcpResource) {
        doExposeResource(mcpResource);

        String index = resourceResolver.index();
        repository.save(index, mcpResource.getLocation(), mcpResource);
    }

    public List<ResourceReference> list() {
        String index = resourceResolver.index();
        return repository.getAll(index);
    }

    void loadResources(@Observes StartupEvent ev) {
        List<ResourceReference> resources = list();

        if (resources.isEmpty()) {
            LOG.warnf("Index %s not found", resourceResolver.index());
            return;
        }

        try {
            for (ResourceReference resourceReference : resources) {
                doExposeResource(resourceReference);
            }

            // TODO is it an update?
//            IndexHelper.saveResourcesIndex(indexFile, resourceReferences);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void doExposeResource(ResourceReference resourceReference) {
        LOG.debugf("Exposing resource: %s", resourceReference.getName());
        resourceManager.newResource(resourceReference.getName())
                .setUri(resourceReference.getLocation())
                .setMimeType(resourceReference.getMimeType())
                .setDescription(resourceReference.getDescription())
                .setHandler(
                        args -> new ResourceResponse(
                                resourceResolver.read(args, resourceReference)))
                .register();
    }

    public void remove(String name) {
        resourceManager.removeResource(name);

        String index = resourceResolver.index();
        repository.delete(index, name);
    }
}
