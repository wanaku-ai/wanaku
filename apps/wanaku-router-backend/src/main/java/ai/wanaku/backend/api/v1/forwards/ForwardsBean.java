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
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.backend.api.v1.namespaces.NamespacesBean;
import ai.wanaku.backend.bridge.ForwardClient;
import ai.wanaku.backend.bridge.ForwardRegistry;
import ai.wanaku.backend.bridge.ForwardRootEntry;
import ai.wanaku.backend.bridge.ForwardRoots;
import ai.wanaku.backend.bridge.McpBridge;
import ai.wanaku.backend.common.AbstractBean;
import ai.wanaku.backend.common.ResourceHelper;
import ai.wanaku.backend.common.ToolsHelper;
import ai.wanaku.backend.core.mcp.util.LabelExpressionParser;
import ai.wanaku.backend.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.backend.core.persistence.api.ForwardRootsRepository;
import ai.wanaku.backend.core.persistence.api.WanakuRepository;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.exceptions.ResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.capabilities.sdk.api.types.LabelsAwareEntity;
import ai.wanaku.capabilities.sdk.api.types.NameNamespacePair;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.RemoteToolReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.util.StringHelper;
import dev.langchain4j.mcp.client.McpRoot;

@ApplicationScoped
public class ForwardsBean extends AbstractBean<ForwardReference> {
    private static final Logger LOG = Logger.getLogger(ForwardsBean.class);

    private final Map<NameNamespacePair, List<RemoteToolReference>> registeredRemoteToolsByForward =
            new ConcurrentHashMap<>();

    @Inject
    ResourceManager resourceManager;

    @Inject
    ToolManager toolManager;

    @Inject
    NamespacesBean namespacesBean;

    @Inject
    Instance<ForwardReferenceRepository> forwardReferenceRepositoryInstance;

    @Inject
    Instance<ForwardRootsRepository> forwardRootsRepositoryInstance;

    @Inject
    ForwardRegistry forwardRegistry;

    @Inject
    McpBridge mcpBridge;

    private ForwardReferenceRepository forwardReferenceRepository;
    private ForwardRootsRepository forwardRootsRepository;

    @PostConstruct
    void init() {
        forwardReferenceRepository = forwardReferenceRepositoryInstance.get();
        forwardRootsRepository = forwardRootsRepositoryInstance.get();
    }

    private boolean removeLinkedEntries(ForwardReference forwardReference) {
        final NameNamespacePair nameNamespacePair =
                new NameNamespacePair(forwardReference.getName(), forwardReference.getNamespace());

        String address = forwardRegistry.getClientAddress(nameNamespacePair);
        if (address == null) {
            return false;
        }

        List<McpRoot> roots = forwardRegistry.getRoots(nameNamespacePair);
        try (var forwardClient = ForwardClient.newClient(address, roots)) {
            removeRemoteTools(nameNamespacePair, forwardClient);
            removeRemoteResources(forwardClient);
        } finally {
            forwardRegistry.unlink(nameNamespacePair);
        }

        return true;
    }

    private void removeRemoteResources(ForwardClient forwardClient) {
        final List<ResourceReference> resourceReferences = mcpBridge.listResources(forwardClient);
        for (ResourceReference remoteResource : resourceReferences) {
            LOG.infof("Removing remote resource %s", remoteResource);
            try {
                resourceManager.removeResource(remoteResource.getLocation());
            } catch (Exception e) {
                LOG.infof(
                        "Failed to remove forward tool %s (server restart may be necessary)", remoteResource.getName());
            }
        }
    }

    private void removeRemoteTools(NameNamespacePair nameNamespacePair, ForwardClient forwardClient) {
        List<RemoteToolReference> remoteToolReferences = registeredRemoteToolsByForward.remove(nameNamespacePair);
        if (remoteToolReferences == null) {

            remoteToolReferences = mcpBridge.listTools(forwardClient);
        }

        for (RemoteToolReference remoteToolReference : remoteToolReferences) {
            LOG.infof("Removing remote tool %s", remoteToolReference);
            try {
                toolManager.removeTool(remoteToolReference.getName());
            } catch (Exception e) {
                LOG.infof(
                        "Failed to remove forward tool %s (server restart may be necessary)",
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

        // Remove persisted roots
        removePersistedRoots(forwardReference.getName());

        // Remove from the repository
        return removeByName(forwardReference.getName());
    }

    public void forward(ForwardReference forwardReference) {
        forward(forwardReference, null);
    }

    public void forward(ForwardReference forwardReference, List<ForwardRequest.RootEntry> rootEntries) {
        try {
            // The input record is incomplete (i.e.: missing the ID, so we lookup the full record on the repository).
            final List<ForwardReference> references = forwardReferenceRepository.findByName(forwardReference.getName());
            if (!references.isEmpty()) {
                throw EntityAlreadyExistsException.forName(forwardReference.getName());
            }

            List<McpRoot> mcpRoots = toMcpRoots(rootEntries);
            registerForward(forwardReference, mcpRoots);

            forwardReferenceRepository.persist(forwardReference);
            persistRoots(forwardReference.getName(), rootEntries);
        } catch (WanakuException e) {
            throw e;
        } catch (Exception e) {
            throw new WanakuException(e);
        }
    }

    private void registerForward(ForwardReference forwardReference) {
        List<McpRoot> mcpRoots = loadPersistedRoots(forwardReference.getName());
        registerForward(forwardReference, mcpRoots);
    }

    private void registerForward(ForwardReference forwardReference, List<McpRoot> mcpRoots) {
        Namespace ns;

        if (StringHelper.isEmpty(forwardReference.getNamespace())) {
            ns = namespacesBean.getDefaultNamespace();
        } else {
            if (!namespacesBean.exists(forwardReference.getNamespace())) {
                throw new WanakuException("Invalid namespace id: %s".formatted(forwardReference.getNamespace()));
            }
            ns = namespacesBean.getById(forwardReference.getNamespace());
        }

        final NameNamespacePair nameNamespacePair =
                new NameNamespacePair(forwardReference.getName(), forwardReference.getNamespace());

        String address = forwardReference.getAddress();

        final List<RemoteToolReference> locallyRegisteredTools = new ArrayList<>();
        executeForwardRegistration(forwardReference, nameNamespacePair, address, locallyRegisteredTools, ns, mcpRoots);
    }

    private void registerTools(
            ForwardClient forwardClient,
            ForwardReference forwardReference,
            List<RemoteToolReference> locallyRegisteredTools,
            Namespace ns)
            throws ServiceUnavailableException {
        Set<String> reservedNames = new HashSet<>();
        for (RemoteToolReference reference : mcpBridge.listTools(forwardClient)) {
            String remoteName = reference.getName();
            String localName = ForwardToolHelper.buildUniqueRemoteToolName(
                    remoteName, forwardReference.getName(), name -> toolManager.getTool(name) != null, reservedNames);

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
                    toolManager,
                    ns,
                    (args, ignored) -> mcpBridge.executeTool(forwardClient.address(), args, reference));

            reservedNames.add(localName);
            locallyRegisteredTools.add(localReference);
        }
    }

    private void registerResources(ForwardClient forwardClient, Namespace ns) throws ServiceUnavailableException {
        List<ResourceReference> resourceReferences = mcpBridge.listResources(forwardClient);
        for (ResourceReference reference : resourceReferences) {
            LOG.debugf("Exposing remote resource %s", reference.getName());
            ResourceHelper.expose(
                    reference, resourceManager, ns, (args, res) -> mcpBridge.read(forwardClient, args, res));
        }
    }

    /*
     * Best-effort cleanup to avoid leaving partially-registered tools around
     */
    private void cleanupPartiallyRegistered(
            List<RemoteToolReference> locallyRegisteredTools, NameNamespacePair nameNamespacePair) {
        // Best-effort cleanup to avoid leaving partially-registered tools around
        for (RemoteToolReference toolReference : locallyRegisteredTools) {
            try {
                toolManager.removeTool(toolReference.getName());
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
            List<McpRoot> mcpRoots) {
        try (var forwardClient = ForwardClient.newClient(address, mcpRoots)) {
            registerResources(forwardClient, ns);
            registerTools(forwardClient, forwardReference, locallyRegisteredTools, ns);

            registeredRemoteToolsByForward.put(nameNamespacePair, List.copyOf(locallyRegisteredTools));
            forwardRegistry.link(nameNamespacePair, forwardClient.address(), mcpRoots);
        } catch (WanakuException e) {
            cleanupPartiallyRegistered(locallyRegisteredTools, nameNamespacePair);

            throw e;
        } catch (Exception e) {
            cleanupPartiallyRegistered(locallyRegisteredTools, nameNamespacePair);

            throw new WanakuException(e);
        }
    }

    public List<ResourceReference> listAllResources() {
        List<ResourceReference> references = new ArrayList<>();
        for (Map.Entry<NameNamespacePair, String> entry :
                forwardRegistry.clients().entrySet()) {
            List<McpRoot> roots = forwardRegistry.getRoots(entry.getKey());
            try (var client = ForwardClient.newClient(entry.getValue(), roots)) {
                references.addAll(mcpBridge.listResources(client));
            }
        }

        return references;
    }

    /**
     * List all tools from forward services
     * @return
     */
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
        // Label filtering is not supported for forwards
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

        // Remove existing tools/resources and close the MCP client
        removeLinkedEntries(forwardReference);

        // Re-register with fresh data from remote server
        registerForward(forwardReference);
    }

    private static List<McpRoot> toMcpRoots(List<ForwardRequest.RootEntry> rootEntries) {
        if (rootEntries == null || rootEntries.isEmpty()) {
            return List.of();
        }
        return rootEntries.stream()
                .map(e -> new McpRoot(e.getName(), e.getUri()))
                .collect(Collectors.toList());
    }

    private void persistRoots(String forwardName, List<ForwardRequest.RootEntry> rootEntries) {
        if (rootEntries == null || rootEntries.isEmpty()) {
            return;
        }

        ForwardRoots forwardRoots = new ForwardRoots();
        forwardRoots.setForwardName(forwardName);
        forwardRoots.setRoots(rootEntries.stream()
                .map(e -> new ForwardRootEntry(e.getUri(), e.getName()))
                .collect(Collectors.toList()));
        forwardRootsRepository.persist(forwardRoots);
    }

    private List<McpRoot> loadPersistedRoots(String forwardName) {
        List<ForwardRoots> stored = forwardRootsRepository.findByForwardName(forwardName);
        if (stored.isEmpty()) {
            return List.of();
        }
        return stored.getFirst().getRoots().stream()
                .map(e -> new McpRoot(e.getName(), e.getUri()))
                .collect(Collectors.toList());
    }

    private void removePersistedRoots(String forwardName) {
        List<ForwardRoots> stored = forwardRootsRepository.findByForwardName(forwardName);
        for (ForwardRoots roots : stored) {
            forwardRootsRepository.deleteById(roots.getId());
        }
    }

    @Override
    protected WanakuRepository<ForwardReference, String> getRepository() {
        return forwardReferenceRepository;
    }
}
