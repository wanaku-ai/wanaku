package ai.wanaku.server.quarkus.api.v1.resources;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.ResourceReferenceEntity;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.service.discovery.ResourceReferenceRepository;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

@Transactional
@ApplicationScoped
public class ResourcesBean {
    private static final Logger LOG = Logger.getLogger(ResourcesBean.class);

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceResolver resourceResolver;

    @Inject
    ResourceReferenceRepository resourceReferenceRepository;

    public void expose(ResourceReference mcpResource) {
        resourceReferenceRepository.persist(new ResourceReferenceEntity(mcpResource));
        doExposeResource(mcpResource);
    }

    public List<ResourceReference> list() {
        List<ResourceReferenceEntity> resourceReferenceEntities = resourceReferenceRepository.listAll();

        return resourceReferenceEntities.stream()
                .map(resourceReferenceEntity -> resourceReferenceEntity.asResourceReference())
                .collect(Collectors.toList());
    }

    void loadResources(@Observes StartupEvent ev) {
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
        if (!resourceReferenceRepository.deleteById(name)) {
            throw new ToolNotFoundException("Resource not found: " + name);
        }
        resourceManager.removeResource(name);
    }
}
