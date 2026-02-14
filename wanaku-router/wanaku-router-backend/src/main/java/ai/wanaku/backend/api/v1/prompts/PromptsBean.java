package ai.wanaku.backend.api.v1.prompts;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.backend.api.v1.namespaces.NamespacesBean;
import ai.wanaku.backend.common.AbstractBean;
import ai.wanaku.backend.common.PromptHelper;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.io.PromptPayload;
import ai.wanaku.core.mcp.common.resolvers.PromptsResolver;
import ai.wanaku.core.persistence.api.PromptReferenceRepository;
import ai.wanaku.core.persistence.api.WanakuRepository;
import ai.wanaku.core.util.StringHelper;

@ApplicationScoped
public class PromptsBean extends AbstractBean<PromptReference> {
    private static final Logger LOG = Logger.getLogger(PromptsBean.class);

    @Inject
    PromptManager promptManager;

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
        // Register with MCP PromptManager
        registerPrompt(promptReference);

        // if all goes well, persist the prompt, so it can be loaded back when restarting
        return promptReferenceRepository.persist(promptReference);
    }

    public PromptReference add(PromptPayload promptPayload) {
        return add(promptPayload.getPayload());
    }

    private void registerPrompt(PromptReference promptReference) {
        if (!StringHelper.isEmpty(promptReference.getNamespace())) {
            final Namespace namespace = namespacesBean.alocateNamespace(promptReference.getNamespace());

            PromptHelper.registerPrompt(promptReference, promptManager, namespace, this::handlePromptGet);
        } else {
            PromptHelper.registerPrompt(promptReference, promptManager, this::handlePromptGet);
        }
    }

    private io.quarkiverse.mcp.server.PromptResponse handlePromptGet(
            PromptManager.PromptArguments args, PromptReference promptReference) {
        return PromptHelper.expandAndConvert(args.args(), promptReference);
    }

    public List<PromptReference> list() {
        return promptReferenceRepository.listAll();
    }

    void loadPrompts(@Observes StartupEvent ev) {
        // Preload namespaces
        namespacesBean.preload();

        // Register all prompts with MCP server
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
                promptManager.removePrompt(name);
            }
        }

        return removed;
    }

    public void update(PromptReference resource) {
        // Fetch the existing prompt by name to get its ID
        PromptReference existing = getByName(resource.getName());
        if (existing != null) {
            // Set the ID from the existing prompt
            resource.setId(existing.getId());
            promptReferenceRepository.update(resource.getId(), resource);

            // Remove old prompt from MCP and re-register with updated content
            promptManager.removePrompt(resource.getName());
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
