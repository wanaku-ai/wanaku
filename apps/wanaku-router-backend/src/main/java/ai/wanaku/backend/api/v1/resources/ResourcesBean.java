package ai.wanaku.backend.api.v1.resources;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.backend.api.v1.namespaces.NamespacesBean;
import ai.wanaku.backend.bridge.ProvisionerBridge;
import ai.wanaku.backend.bridge.ResourceBridge;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.backend.common.AbstractBean;
import ai.wanaku.backend.common.ResourceHelper;
import ai.wanaku.backend.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.backend.core.persistence.api.WanakuRepository;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.io.ResourcePayload;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.util.StringHelper;

@ApplicationScoped
public class ResourcesBean extends AbstractBean<ResourceReference> {
    private static final Logger LOG = Logger.getLogger(ResourcesBean.class);

    @Inject
    ResourceManager resourceManager;

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
        doExposeResource(mcpResource);

        return resourceReferenceRepository.persist(mcpResource);
    }

    public ResourceReference expose(ResourcePayload resourcePayload) {
        ResourceReference resourceReference = resourcePayload.getPayload();
        ServiceTarget service =
                provisionerBridge.resolveService(resourceReference.getType(), ServiceType.RESOURCE_PROVIDER.asValue());
        final ProvisioningReference provisioningReference = provisionerBridge.provision(
                resourceReference.getName(),
                resourcePayload.getConfigurationData(),
                resourcePayload.getSecretsData(),
                service);

        resourceReference.setConfigurationURI(provisioningReference.configurationURI().toString());
        resourceReference.setSecretsURI(provisioningReference.secretsURI().toString());

        return expose(resourcePayload.getPayload());
    }

    public List<ResourceReference> list(String labelFilter) {
        if (labelFilter == null || labelFilter.trim().isEmpty()) {
            return resourceReferenceRepository.listAll();
        }
        return resourceReferenceRepository.findAllFilterByLabelExpression(labelFilter);
    }

    public List<ResourceReference> list() {
        return list(null);
    }

    void loadResources(@Observes StartupEvent ev) {
        // Preload data
        namespacesBean.preload();

        for (ResourceReference resourceReference : list()) {
            try {
                expose(resourceReference);
            } catch (EntityAlreadyExistsException e) {
                LOG.errorf(
                        "Error registering a resource named %s during startup, but it already exists",
                        resourceReference.getName());
            }
        }
    }

    private void doExposeResource(ResourceReference resourceReference) {
        if (!StringHelper.isEmpty(resourceReference.getNamespace())) {
            final Namespace namespace = namespacesBean.alocateNamespace(resourceReference.getNamespace());

            ResourceHelper.expose(resourceReference, resourceManager, namespace, resourceBridge::read);
        } else {
            ResourceHelper.expose(resourceReference, resourceManager, resourceBridge::read);
        }
    }

    private int removeReference(String name, ResourceReference resourceReference) {
        int removed = 0;

        try {
            removed = resourceReferenceRepository.removeByField("name", name);
        } finally {
            if (removed > 0) {
                resourceManager.removeResource(resourceReference.getLocation());
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
