package ai.wanaku.server.quarkus.api.v1.resources;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.File;
import java.util.List;

@ApplicationScoped
public class ResourcesBean {
    private static final Logger LOG = Logger.getLogger(ResourcesBean.class);

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceResolver resourceResolver;

    @Inject
    Instance<ResourceReferenceRepository> resourceReferenceRepositoryInstance;

    private ResourceReferenceRepository resourceReferenceRepository;

    @PostConstruct
    public void init() {
        resourceReferenceRepository = resourceReferenceRepositoryInstance.get();
    }

    public void expose(ResourceReference mcpResource) {
        doExposeResource(mcpResource);
        resourceReferenceRepository.persist(mcpResource);
    }

    public List<ResourceReference> list() {
        return resourceReferenceRepository.listAll();
    }

    void loadResources(@Observes StartupEvent ev) {
        File indexFile = resourceResolver.indexLocation();
        if (!indexFile.exists()) {
            LOG.warnf("Index file not found: %s", indexFile);
            return;
        }

        for (ResourceReference resourceReference : list()) {
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
        ResourceReference resourceReference = resourceReferenceRepository.findById(name);
        resourceManager.removeResource(resourceReference.getLocation());
        resourceReferenceRepository.deleteById(resourceReference.getName());
    }
}
