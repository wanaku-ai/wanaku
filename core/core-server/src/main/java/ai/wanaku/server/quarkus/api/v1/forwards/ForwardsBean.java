package ai.wanaku.server.quarkus.api.v1.forwards;

import ai.wanaku.api.types.NameNamespacePair;
import ai.wanaku.api.types.Namespace;
import ai.wanaku.core.persistence.api.WanakuRepository;
import ai.wanaku.core.util.StringHelper;
import ai.wanaku.server.quarkus.api.v1.namespaces.NamespacesBean;
import ai.wanaku.server.quarkus.common.AbstractBean;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.api.types.RemoteToolReference;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import ai.wanaku.core.mcp.providers.ForwardRegistry;
import ai.wanaku.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.server.quarkus.common.ResourceHelper;
import ai.wanaku.server.quarkus.common.ToolsHelper;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.runtime.StartupEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ForwardsBean extends AbstractBean<ForwardReference> {
    private static final Logger LOG = Logger.getLogger(ForwardsBean.class);

    @Inject
    ResourceManager resourceManager;

    @Inject
    ToolManager toolManager;

    @Inject
    ForwardRegistry forwardRegistry;

    @Inject
    NamespacesBean namespacesBean;

    @Inject
    Instance<ForwardReferenceRepository> forwardReferenceRepositoryInstance;

    private ForwardReferenceRepository forwardReferenceRepository;

    @PostConstruct
    void init() {
        forwardReferenceRepository = forwardReferenceRepositoryInstance.get();
    }

    private boolean removeLinkedEntries(ForwardReference forwardReference) {
        final NameNamespacePair nameNamespacePair =
                new NameNamespacePair(forwardReference.getName(), forwardReference.getNamespace());

        final ForwardResolver forwardResolver = forwardRegistry.getResolver(nameNamespacePair);
        if (forwardResolver == null) {
            return false;
        }

        try {
            removeRemoteTools(forwardResolver);
            removeRemoteResources(forwardResolver);
        } finally {
            forwardRegistry.unlink(nameNamespacePair);
        }

        return true;
    }

    private void removeRemoteResources(ForwardResolver forwardResolver) {
        final List<ResourceReference> resourceReferences = forwardResolver.listResources();
        for (ResourceReference remoteResource : resourceReferences) {
            LOG.infof("Removing remote resource %s", remoteResource);
            try {
                resourceManager.removeResource(remoteResource.getLocation());
            } catch (Exception e) {
                LOG.infof("Failed to remove forward tool %s (server restart may be necessary)",
                        remoteResource.getName());
            }
        }
    }

    private void removeRemoteTools(ForwardResolver forwardResolver) {
        List<RemoteToolReference> remoteToolReferences = forwardResolver.listTools();
        for (RemoteToolReference remoteToolReference : remoteToolReferences) {
            LOG.infof("Removing remote tool %s", remoteToolReference);
            try {
                toolManager.removeTool(remoteToolReference.getName());
            } catch (Exception e) {
                LOG.infof("Failed to remove forward tool %s (server restart may be necessary)",
                        remoteToolReference.getName());
            }
        }
    }

    public int remove(ForwardReference forwardReferenceHint) {
        // The input record is incomplete (i.e.: missing the ID, so we lookup the full record on the repository).
        final List<ForwardReference> references = forwardReferenceRepository.findByName(forwardReferenceHint.getName());
        if (references.isEmpty()) {
            LOG.warnf("Forward reference does not exist", forwardReferenceHint.getName());
            return 0;
        }

        final ForwardReference forwardReference = references.getFirst();
        if (!removeLinkedEntries(forwardReference)) {
            LOG.warnf("Failed to remove tools and resources references for %s", forwardReference.getName());
        }

        // Remove from the repository
        return removeByName(forwardReference.getName());
    }

    public void forward(ForwardReference forwardReference) {
        try {
            // The input record is incomplete (i.e.: missing the ID, so we lookup the full record on the repository).
            final List<ForwardReference> references = forwardReferenceRepository.findByName(forwardReference.getName());
            if (!references.isEmpty()) {
                LOG.warnf("Forward reference %s already exists", forwardReference.getName());
                return;
            }

            registerForward(forwardReference);

            forwardReferenceRepository.persist(forwardReference);
        } catch (WanakuException e) {
            throw e;
        } catch (Exception e) {
            throw new WanakuException(e);
        }
    }

    private void registerForward(ForwardReference forwardReference) {
        Namespace ns = null;

        if (!StringHelper.isEmpty(forwardReference.getNamespace())) {
            ns = namespacesBean.alocateNamespace(forwardReference.getNamespace());
        }

        final NameNamespacePair nameNamespacePair =
                new NameNamespacePair(forwardReference.getName(), forwardReference.getNamespace());


        ForwardResolver forwardResolver = forwardRegistry.newResolverForService(nameNamespacePair, forwardReference);
        List<ResourceReference> resourceReferences = forwardResolver.listResources();
        for (ResourceReference reference : resourceReferences) {
            LOG.debugf("Exposing remote resource %s", reference.getName());
            ResourceHelper.expose(reference, resourceManager, ns, forwardResolver::read);
        }

        List<RemoteToolReference> toolReferences = forwardResolver.listTools();
        for (RemoteToolReference reference : toolReferences) {
            LOG.infof("Binding remote tool %s", reference.getName());
            Tool tool = forwardResolver.resolve(reference);
            ToolsHelper.registerTool(reference, toolManager, ns, tool::call);
        }

        forwardRegistry.link(nameNamespacePair, forwardResolver);
    }

    public List<ResourceReference> listAllResources() {
        List<ResourceReference> references = new ArrayList<>();
        for (NameNamespacePair service : forwardRegistry.services()) {
            ForwardResolver forwardResolver = forwardRegistry.getResolver(service);

            List<ResourceReference> remoteToolReferences = forwardResolver.listResources();
            references.addAll(remoteToolReferences);
        }

        return references;
    }

    /**
     * List all tools from forward services
     * @return
     */
    public List<RemoteToolReference> listAllTools() {
        List<RemoteToolReference> references = new ArrayList<>();
        for (NameNamespacePair service : forwardRegistry.services()) {
            ForwardResolver forwardResolver = forwardRegistry.getResolver(service);

            List<RemoteToolReference> remoteToolReferences = forwardResolver.listTools();
            references.addAll(remoteToolReferences);
        }

        return references;
    }

    public List<ToolReference> listAllAsTools() {
        return listAllTools().stream().map(RemoteToolReference::asToolReference)
                        .collect(Collectors.toList());
    }

    void loadForwards(@Observes StartupEvent event) {
        for (ForwardReference forwardReference : forwardReferenceRepository.listAll()) {
            try {
                registerForward(forwardReference);
            } catch (Exception e) {
                LOG.errorf("Error registering forwards from %s during startup", forwardReference.getAddress());
            }
        }
    }

    public List<ForwardReference> listForwards() {
        return forwardReferenceRepository.listAll();
    }

    public void update(ForwardReference resource) {
        forwardReferenceRepository.update(resource.getName(), resource);
    }

    @Override
    protected  WanakuRepository<ForwardReference, String> getRepository() {
        return forwardReferenceRepository;
    }
}
