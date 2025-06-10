package ai.wanaku.server.quarkus.api.v1.resources;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.server.quarkus.common.ResourceHelper;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkus.runtime.StartupEvent;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

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

    public ResourceReference expose(ResourceReference mcpResource) {
        doExposeResource(mcpResource);
        return resourceReferenceRepository.persist(mcpResource);
    }

    public List<ResourceReference> list() {
        return resourceReferenceRepository.listAll();
    }

    void loadResources(@Observes StartupEvent ev) {
        for (ResourceReference resourceReference : list()) {
            doExposeResource(resourceReference);
        }
    }

    private void doExposeResource(ResourceReference resourceReference) {
        ResourceHelper.expose(resourceReference, resourceManager, resourceResolver::read);
    }

    private boolean removeReference(String name, ResourceReference resourceReference) {
        if (resourceReference.getName().equals(name)) {
            try {
                resourceManager.removeResource(resourceReference.getLocation());
            } finally {
                resourceReferenceRepository.deleteById(resourceReference.getId());
            }

            return true;
        }

        return false;
    }

    public void remove(String name) {
        if (!resourceReferenceRepository.remove(ref -> removeReference(name, ref))) {
            LOG.warnf("No references named %s where found", name);
        }
    }

    public void update(ResourceReference resource) {
        resourceReferenceRepository.update(resource.getId(), resource);
    }
}
