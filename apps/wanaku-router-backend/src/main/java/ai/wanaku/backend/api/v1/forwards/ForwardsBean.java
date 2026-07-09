package ai.wanaku.backend.api.v1.forwards;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.server.McpSyncServer;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.backend.api.v1.namespaces.NamespacesBean;
import ai.wanaku.backend.bridge.ForwardClient;
import ai.wanaku.backend.bridge.ForwardRegistry;
import ai.wanaku.backend.bridge.McpBridge;
import ai.wanaku.backend.common.AbstractBean;
import ai.wanaku.backend.common.ResourceHelper;
import ai.wanaku.backend.common.ToolsHelper;
import ai.wanaku.backend.core.mcp.util.LabelExpressionParser;
import ai.wanaku.backend.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.backend.core.persistence.api.WanakuRepository;
import ai.wanaku.backend.mcp.McpServerRegistry;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.exceptions.ResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.capabilities.sdk.api.types.LabelsAwareEntity;
import ai.wanaku.capabilities.sdk.api.types.NameNamespacePair;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.RemoteToolReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.util.StringHelper;

@ApplicationScoped
public class ForwardsBean extends AbstractBean<ForwardReference> {
    private static final Logger LOG = Logger.getLogger(ForwardsBean.class);

    private final Map<NameNamespacePair, List<RemoteToolReference>> registeredRemoteToolsByForward =
            new ConcurrentHashMap<>();

    private final Set<String> registeredToolNames = ConcurrentHashMap.newKeySet();

    @Inject
    McpServerRegistry registry;

    @Inject
    NamespacesBean namespacesBean;

    @Inject
    Instance<ForwardReferenceRepository> forwardReferenceRepositoryInstance;

    @Inject
    ForwardRegistry forwardRegistry;

    @Inject
    McpBridge mcpBridge;

    private ForwardReferenceRepository forwardReferenceRepository;

    @PostConstruct
    void init() {
        forwardReferenceRepository = forwardReferenceRepositoryInstance.get();
    }

    private boolean removeLinkedEntries(ForwardReference forwardReference) {
        final NameNamespacePair nameNamespacePair =
                new NameNamespacePair(forwardReference.getName(), forwardReference.getNamespace());

        String address = forwardRegistry.getClientAddress(nameNamespacePair);
        if (address == null) {
            return false;
        }

        try (var forwardClient = ForwardClient.newClient(address)) {
            removeRemoteTools(nameNamespacePair, forwardClient);
            removeRemoteResources(forwardClient);
        } finally {
            forwardRegistry.unlink(nameNamespacePair);
        }

        return true;
    }

    private void removeRemoteResources(ForwardClient forwardClient) {
        final List<ResourceReference> resourceReferences = mcpBridge.listResources(forwardClient);
        McpSyncServer server = registry.getPublicServer();
        for (ResourceReference remoteResource : resourceReferences) {
            LOG.infof("Removing remote resource %s", remoteResource);
            try {
                server.removeResource(remoteResource.getLocation());
            } catch (Exception e) {
                LOG.infof(
                        "Failed to remove forward resource %s (server restart may be necessary)",
                        remoteResource.getName());
            }
        }
    }

    private void removeRemoteTools(NameNamespacePair nameNamespacePair, ForwardClient forwardClient) {
        List<RemoteToolReference> remoteToolReferences = registeredRemoteToolsByForward.remove(nameNamespacePair);
        if (remoteToolReferences == null) {
            remoteToolReferences = mcpBridge.listTools(forwardClient);
        }

        McpSyncServer server = registry.getPublicServer();
        for (RemoteToolReference remoteToolReference : remoteToolReferences) {
            LOG.infof("Removing remote tool %s", remoteToolReference);
            try {
                server.removeTool(remoteToolReference.getName());
                registeredToolNames.remove(remoteToolReference.getName());
            } catch (Exception e) {
                LOG.infof(
                        "Failed to remove forward tool %s (server restart may be necessary)",
                        remoteToolReference.getName());
            }
        }
    }

    public int remove(ForwardReference forwardReferenceHint) {
        final List<ForwardReference> references = forwardReferenceRepository.findByName(forwardReferenceHint.getName());
        if (references.isEmpty()) {
            LOG.warnf("Forward reference does not exist", forwardReferenceHint.getName());
            return 0;
        }

        final ForwardReference forwardReference = references.getFirst();
        if (!removeLinkedEntries(forwardReference)) {
            LOG.warnf("Failed to remove tools and resources references for %s", forwardReference.getName());
        }

        return removeByName(forwardReference.getName());
    }

    public void forward(ForwardReference forwardReference) {
        try {
            final List<ForwardReference> references = forwardReferenceRepository.findByName(forwardReference.getName());
            if (!references.isEmpty()) {
                throw EntityAlreadyExistsException.forName(forwardReference.getName());
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
        Namespace ns;

        if (StringHelper.isEmpty(forwardReference.getNamespace())) {
            ns = namespacesBean.getDefaultNamespace();
        } else {
            if (!namespacesBean.exists(forwardReference.getNamespace())) {
                throw new WanakuException("Invalid namespace id: %s".formatted(forwardReference.getNamespace()));
            }
            ns = namespacesBean.getById(forwardReference.getNamespace());
        }

        McpSyncServer server = registry.getServerForPath(ns.getPath());

        final NameNamespacePair nameNamespacePair =
                new NameNamespacePair(forwardReference.getName(), forwardReference.getNamespace());

        String address = forwardReference.getAddress();

        final List<RemoteToolReference> locallyRegisteredTools = new ArrayList<>();
        executeForwardRegistration(forwardReference, nameNamespacePair, address, locallyRegisteredTools, ns, server);
    }

    private void registerTools(
            ForwardClient forwardClient,
            ForwardReference forwardReference,
            List<RemoteToolReference> locallyRegisteredTools,
            Namespace ns,
            McpSyncServer server) {
        Set<String> reservedNames = new HashSet<>();
        for (RemoteToolReference reference : mcpBridge.listTools(forwardClient)) {
            String remoteName = reference.getName();
            String localName = ForwardToolHelper.buildUniqueRemoteToolName(
                    remoteName, forwardReference.getName(), registeredToolNames::contains, reservedNames);

            if (!remoteName.equals(localName)) {
                LOG.infof("Binding remote tool %s as %s", remoteName, localName);
            } else {
                LOG.infof("Binding remote tool %s", remoteName);
            }

            RemoteToolReference localReference = ForwardToolHelper.copyRemoteToolReference(reference);
            localReference.setName(localName);
            localReference.setNamespace(forwardReference.getNamespace());

            ToolsHelper.registerTool(
                    localReference,
                    server,
                    ns,
                    (req, sessionId, ignored) ->
                            mcpBridge.executeTool(forwardClient.address(), req, sessionId, reference));

            reservedNames.add(localName);
            registeredToolNames.add(localName);
            locallyRegisteredTools.add(localReference);
        }
    }

    private void registerResources(ForwardClient forwardClient, Namespace ns, McpSyncServer server) {
        List<ResourceReference> resourceReferences = mcpBridge.listResources(forwardClient);
        for (ResourceReference reference : resourceReferences) {
            LOG.debugf("Exposing remote resource %s", reference.getName());
            ResourceHelper.expose(
                    reference, server, ns, (req, sid, res) -> mcpBridge.read(forwardClient, req, sid, res));
        }
    }

    private void cleanupPartiallyRegistered(
            List<RemoteToolReference> locallyRegisteredTools,
            NameNamespacePair nameNamespacePair,
            McpSyncServer server) {
        for (RemoteToolReference toolReference : locallyRegisteredTools) {
            try {
                server.removeTool(toolReference.getName());
                registeredToolNames.remove(toolReference.getName());
            } catch (Exception ignored) {
                LOG.warnf("Failed to remove forward tool %s after registration error", toolReference.getName());
            }
        }
        registeredRemoteToolsByForward.remove(nameNamespacePair);
    }

    private void executeForwardRegistration(
            ForwardReference forwardReference,
            NameNamespacePair nameNamespacePair,
            String address,
            List<RemoteToolReference> locallyRegisteredTools,
            Namespace ns,
            McpSyncServer server) {
        try (var forwardClient = ForwardClient.newClient(address)) {
            registerResources(forwardClient, ns, server);
            registerTools(forwardClient, forwardReference, locallyRegisteredTools, ns, server);

            registeredRemoteToolsByForward.put(nameNamespacePair, List.copyOf(locallyRegisteredTools));
            forwardRegistry.link(nameNamespacePair, forwardClient.address());
        } catch (WanakuException e) {
            cleanupPartiallyRegistered(locallyRegisteredTools, nameNamespacePair, server);
            throw e;
        } catch (Exception e) {
            cleanupPartiallyRegistered(locallyRegisteredTools, nameNamespacePair, server);
            throw new WanakuException(e);
        }
    }

    public List<ResourceReference> listAllResources() {
        List<ResourceReference> references = new ArrayList<>();
        for (String address : forwardRegistry.clients().values()) {
            try (var client = ForwardClient.newClient(address)) {
                references.addAll(mcpBridge.listResources(client));
            }
        }
        return references;
    }

    public List<RemoteToolReference> listAllTools() {
        List<RemoteToolReference> references = new ArrayList<>();
        registeredRemoteToolsByForward.values().forEach(references::addAll);
        return references;
    }

    public List<ToolReference> listAllAsTools() {
        return listAllTools().stream().map(RemoteToolReference::asToolReference).collect(Collectors.toList());
    }

    public List<ToolReference> listAllAsTools(String labelFilter) {
        if (StringHelper.isBlank(labelFilter)) {
            return listAllAsTools();
        }

        try {
            Predicate<LabelsAwareEntity<?>> filter = LabelExpressionParser.parse(labelFilter);
            return listAllTools().stream()
                    .filter(filter)
                    .map(RemoteToolReference::asToolReference)
                    .collect(Collectors.toList());
        } catch (LabelExpressionParser.LabelExpressionParseException e) {
            throw new WanakuException("Invalid label expression: %s".formatted(labelFilter), e);
        }
    }

    void loadForwards(@Observes StartupEvent event) {
        for (ForwardReference forwardReference : forwardReferenceRepository.listAll()) {
            try {
                registerForward(forwardReference);
            } catch (EntityAlreadyExistsException e) {
                LOG.errorf(
                        "Tried to register a tool named %s during startup, but it already exists",
                        forwardReference.getAddress());
            } catch (Exception e) {
                LOG.errorf("Error registering forwards from %s during startup", forwardReference.getAddress());
            }
        }
    }

    public List<ForwardReference> listForwards(String labelFilter) {
        return forwardReferenceRepository.listAll();
    }

    public List<ForwardReference> listForwards() {
        return listForwards(null);
    }

    public void update(ForwardReference forwardReference) {
        forwardReferenceRepository.update(forwardReference.getId(), forwardReference);
    }

    public void refresh(ForwardReference forwardReferenceHint) {
        List<ForwardReference> references = forwardReferenceRepository.findByName(forwardReferenceHint.getName());
        if (references.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Forward reference not found: %s".formatted(forwardReferenceHint.getName()));
        }

        ForwardReference forwardReference = references.getFirst();
        removeLinkedEntries(forwardReference);
        registerForward(forwardReference);
    }

    @Override
    protected WanakuRepository<ForwardReference, String> getRepository() {
        return forwardReferenceRepository;
    }
}
