package ai.wanaku.server.quarkus.api.v1.resources;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.util.IndexHelper;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkus.runtime.StartupEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ResourcesBean {
    private static final Logger LOG = Logger.getLogger(ResourcesBean.class);

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceResolver resourceResolver;

    public void expose(ResourceReference mcpResource) {
        doExposeResource(mcpResource);

        loadIndexAndThen(resourceReferences -> resourceReferences.add(mcpResource));
    }

    private synchronized void loadIndexAndThen(Consumer<List<ResourceReference>> handler) {
        File indexFile = resourceResolver.indexLocation();
        try {
            List<ResourceReference> resourceReferences = IndexHelper.loadResourcesIndex(indexFile);
            handler.accept(resourceReferences);

            IndexHelper.saveResourcesIndex(indexFile, resourceReferences);
        } catch (Exception e) {
            throw new WanakuException(String.format("I/O error reading/writing resources file: %s", indexFile));
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

        loadIndexAndThen(this::exposeAllResources);
    }

    private void exposeAllResources(List<ResourceReference> resourceReferences) {
        for (ResourceReference resourceReference : resourceReferences) {
            doExposeResource(resourceReference);
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
        loadIndexAndThen(resourceReferences -> removeResource(name, resourceReferences));
    }

    private void removeResource(String name, List<ResourceReference> resourceReferences) {
        ResourceReference resourceReference = null;
        for (var ref : resourceReferences) {
            if (ref.getName().equals(name)) {
                resourceReference = ref;
                break;
            }
        }

        if (resourceReference != null) {
            resourceReferences.remove(resourceReference);
            resourceManager.removeResource(resourceReference.getLocation());
        }
    }
}
