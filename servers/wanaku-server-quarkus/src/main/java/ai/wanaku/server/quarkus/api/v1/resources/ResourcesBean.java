package ai.wanaku.server.quarkus.api.v1.resources;

import java.io.File;
import java.util.List;

import ai.wanaku.api.exceptions.WanakuException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.util.IndexHelper;

@ApplicationScoped
public class ResourcesBean {
    private static final Logger LOG = Logger.getLogger(ResourcesBean.class);

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceResolver resourceResolver;

    public void expose(ResourceReference mcpResource) {
        doExposeResource(mcpResource);

        File indexFile = resourceResolver.indexLocation();
        try {
            List<ResourceReference> resourceReferences = IndexHelper.loadResourcesIndex(indexFile);
            resourceReferences.add(mcpResource);
            IndexHelper.saveResourcesIndex(indexFile, resourceReferences);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<ResourceReference> list() {
        File indexFile = resourceResolver.indexLocation();
        try {
            return IndexHelper.loadResourcesIndex(indexFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void loadResources(@Observes StartupEvent ev) {
        File indexFile = resourceResolver.indexLocation();
        if (!indexFile.exists()) {
            LOG.warnf("Index file not found: %s", indexFile);
            return;
        }

        try {
            List<ResourceReference> resourceReferences = IndexHelper.loadResourcesIndex(indexFile);

            for (ResourceReference resourceReference : resourceReferences) {
                doExposeResource(resourceReference);
            }

            IndexHelper.saveResourcesIndex(indexFile, resourceReferences);
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

        File indexFile = resourceResolver.indexLocation();
        try {
            List<ResourceReference> resourceReferences = IndexHelper.loadResourcesIndex(indexFile);
            resourceReferences.removeIf(resourceReference -> resourceReference.getName().equals(name));
            IndexHelper.saveResourcesIndex(indexFile, resourceReferences);
        } catch (Exception e) {
            throw new WanakuException(String.format("Failed to remove resource from file: %s", indexFile));
        }
    }
}
