package ai.wanaku.backend.api.v1.resources;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.server.McpSyncServer;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.backend.api.v1.common.ProvisioningHelper;
import ai.wanaku.backend.api.v1.namespaces.NamespacesBean;
import ai.wanaku.backend.bridge.ProvisionerBridge;
import ai.wanaku.backend.bridge.ResourceBridge;
import ai.wanaku.backend.common.AbstractBean;
import ai.wanaku.backend.common.ResourceHelper;
import ai.wanaku.backend.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.backend.core.persistence.api.WanakuRepository;
import ai.wanaku.backend.mcp.McpServerRegistry;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.io.ResourcePayload;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.util.StringHelper;

@ApplicationScoped
public class ResourcesBean extends AbstractBean<ResourceReference> {
    private static final Logger LOG = Logger.getLogger(ResourcesBean.class);

    @Inject
    McpServerRegistry registry;

    @Inject
    ResourceBridge resourceBridge;

    @Inject
    ProvisionerBridge provisionerBridge;

    @Inject
    NamespacesBean namespacesBean;

    @Inject
    Instance<ResourceReferenceRepository> resourceReferenceRepositoryInstance;

    private ResourceReferenceRepository resourceReferenceRepository;

    @PostConstruct
    public void init() {
        resourceReferenceRepository = resourceReferenceRepositoryInstance.get();
    }

    public ResourceReference expose(ResourceReference mcpResource) {
        List<ResourceReference> existing = resourceReferenceRepository.findByName(mcpResource.getName());
        if (!existing.isEmpty()) {
            throw EntityAlreadyExistsException.forName(mcpResource.getName());
        }
        doExposeResource(mcpResource);
        return resourceReferenceRepository.persist(mcpResource);
    }

    public ResourceReference expose(ResourcePayload resourcePayload) {
        ResourceReference resourceReference = resourcePayload.getPayload();

        ProvisioningHelper.provision(
                provisionerBridge,
                resourcePayload,
                resourceReference.getName(),
                resourceReference.getType(),
                ServiceType.RESOURCE_PROVIDER.asValue(),
                (configURI, secretsURI) -> {
                    resourceReference.setConfigurationURI(configURI);
                    resourceReference.setSecretsURI(secretsURI);
                });

        return expose(resourcePayload.getPayload());
    }

    public List<ResourceReference> list(String labelFilter) {
        if (StringHelper.isBlank(labelFilter)) {
            return resourceReferenceRepository.listAll();
        }
        return resourceReferenceRepository.findAllFilterByLabelExpression(labelFilter);
    }

    public List<ResourceReference> list() {
        return list(null);
    }

    void loadResources(@Observes StartupEvent ev) {
        namespacesBean.preload();

        for (ResourceReference resourceReference : list()) {
            try {
                doExposeResource(resourceReference);
            } catch (EntityAlreadyExistsException e) {
                LOG.errorf(
                        e,
                        "Error registering a resource named %s during startup, but it already exists",
                        resourceReference.getName());
            }
        }
    }

    private void doExposeResource(ResourceReference resourceReference) {
        if (!StringHelper.isEmpty(resourceReference.getNamespace())) {
            final Namespace namespace = namespacesBean.alocateNamespace(resourceReference.getNamespace());
            McpSyncServer server = registry.getServerForPath(namespace.getPath());
            ResourceHelper.expose(resourceReference, server, namespace, resourceBridge::read);
        } else {
            ResourceHelper.expose(resourceReference, registry.getPublicServer(), resourceBridge::read);
        }
    }

    private int removeReference(String name, ResourceReference resourceReference) {
        int removed = 0;
        try {
            removed = resourceReferenceRepository.removeByField("name", name);
        } finally {
            if (removed > 0) {
                try {
                    registry.getPublicServer().removeResource(resourceReference.getLocation());
                } catch (Exception e) {
                    LOG.debugf(e, "Resource %s not found on public server for removal", name);
                }
            }
        }
        return removed;
    }

    public int remove(String name) {
        ResourceReference ref = getByName(name);
        if (ref == null) {
            return 0;
        }
        return removeReference(name, ref);
    }

    public ResourceReference getByName(String name) {
        List<ResourceReference> resources = resourceReferenceRepository.findByName(name);
        return resources.isEmpty() ? null : resources.getFirst();
    }

    public void update(ResourceReference resource) {
        resourceReferenceRepository.update(resource.getId(), resource);
    }

    @Override
    protected WanakuRepository<ResourceReference, String> getRepository() {
        return resourceReferenceRepository;
    }
}
