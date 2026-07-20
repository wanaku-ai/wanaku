package ai.wanaku.backend.api.v1.prompts;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.backend.api.v1.namespaces.NamespacesBean;
import ai.wanaku.backend.common.AbstractBean;
import ai.wanaku.backend.common.PromptHelper;
import ai.wanaku.backend.core.mcp.common.resolvers.PromptsResolver;
import ai.wanaku.backend.core.persistence.api.PromptReferenceRepository;
import ai.wanaku.backend.core.persistence.api.WanakuRepository;
import ai.wanaku.backend.mcp.McpServerRegistry;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.io.PromptPayload;
import ai.wanaku.core.util.StringHelper;

@ApplicationScoped
public class PromptsBean extends AbstractBean<PromptReference> {
    private static final Logger LOG = Logger.getLogger(PromptsBean.class);

    @Inject
    McpServerRegistry registry;

    @Inject
    PromptsResolver promptsResolver;

    @Inject
    NamespacesBean namespacesBean;

    @Inject
    Instance<PromptReferenceRepository> promptReferenceRepositoryInstance;

    private PromptReferenceRepository promptReferenceRepository;

    @PostConstruct
    void init() {
        promptReferenceRepository = promptReferenceRepositoryInstance.get();
    }

    public PromptReference add(PromptReference promptReference) {
        List<PromptReference> existing = promptReferenceRepository.findByName(promptReference.getName());
        if (!existing.isEmpty()) {
            throw EntityAlreadyExistsException.forName(promptReference.getName());
        }
        registerPrompt(promptReference);
        return promptReferenceRepository.persist(promptReference);
    }

    public PromptReference add(PromptPayload promptPayload) {
        return add(promptPayload.getPayload());
    }

    private void registerPrompt(PromptReference promptReference) {
        if (!StringHelper.isEmpty(promptReference.getNamespace())) {
            final Namespace namespace = namespacesBean.alocateNamespace(promptReference.getNamespace());
            McpSyncServer server = registry.getServerForPath(namespace.getPath());
            PromptHelper.registerPrompt(promptReference, server, namespace, this::handlePromptGet);
        } else {
            PromptHelper.registerPrompt(promptReference, registry.getPublicServer(), this::handlePromptGet);
        }
    }

    private McpSchema.GetPromptResult handlePromptGet(
            McpSchema.GetPromptRequest request, PromptReference promptReference) {
        Map<String, String> stringArgs = new java.util.HashMap<>();
        if (request.arguments() != null) {
            request.arguments().forEach((k, v) -> stringArgs.put(k, v != null ? v.toString() : null));
        }
        return PromptHelper.expandAndConvert(stringArgs, promptReference);
    }

    public List<PromptReference> list() {
        return promptReferenceRepository.listAll();
    }

    void loadPrompts(@Observes StartupEvent ev) {
        namespacesBean.preload();

        for (PromptReference promptReference : list()) {
            registerPrompt(promptReference);
        }

        LOG.infof("Loaded %d prompts from repository", list().size());
    }

    public int remove(String name) throws WanakuException {
        int removed = 0;
        try {
            removed = removeByName(name);
        } finally {
            if (removed > 0) {
                try {
                    registry.getPublicServer().removePrompt(name);
                } catch (Exception e) {
                    LOG.debugf(e, "Prompt %s not found on public server for removal", name);
                }
            }
        }
        return removed;
    }

    public void update(PromptReference resource) {
        PromptReference existing = getByName(resource.getName());
        if (existing != null) {
            resource.setId(existing.getId());
            promptReferenceRepository.update(resource.getId(), resource);

            try {
                registry.getPublicServer().removePrompt(resource.getName());
            } catch (Exception e) {
                LOG.debugf(e, "Prompt %s not found on public server for removal during update", resource.getName());
            }
            registerPrompt(resource);
        }
    }

    public PromptReference getByName(String name) {
        List<PromptReference> prompts = promptReferenceRepository.findByName(name);
        return prompts.isEmpty() ? null : prompts.getFirst();
    }

    @Override
    protected WanakuRepository<PromptReference, String> getRepository() {
        return promptReferenceRepository;
    }
}
