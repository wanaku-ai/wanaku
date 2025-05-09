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

    public void expose(ResourceReference mcpResource) {
        doExposeResource(mcpResource);
        resourceReferenceRepository.persist(mcpResource);
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

    public void remove(String name) {
        ResourceReference resourceReference = resourceReferenceRepository.findById(name);

        try {
            resourceManager.removeResource(resourceReference.getLocation());
        } finally {
            resourceReferenceRepository.deleteById(resourceReference.getName());
        }

    }

    public void update(ResourceReference resource) {
        resourceReferenceRepository.update(resource.getName(), resource);
    }
}
