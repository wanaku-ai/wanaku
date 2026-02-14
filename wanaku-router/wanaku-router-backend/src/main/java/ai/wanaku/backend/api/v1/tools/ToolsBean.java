package ai.wanaku.backend.api.v1.tools;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import org.jboss.logging.Logger;
import io.micrometer.common.util.StringUtils;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.backend.api.v1.namespaces.NamespacesBean;
import ai.wanaku.backend.common.LabelsAwareWanakuEntityBean;
import ai.wanaku.backend.common.ToolsHelper;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.exceptions.ToolNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import ai.wanaku.core.persistence.api.WanakuRepository;
import ai.wanaku.core.util.StringHelper;

@ApplicationScoped
public class ToolsBean extends LabelsAwareWanakuEntityBean<ToolReference> {
    private static final Logger LOG = Logger.getLogger(ToolsBean.class);

    @Inject
    ToolManager toolManager;

    @Inject
    ToolsResolver toolsResolver;

    @Inject
    NamespacesBean namespacesBean;

    @Inject
    Instance<ToolReferenceRepository> toolReferenceRepositoryInstance;

    private ToolReferenceRepository toolReferenceRepository;

    @PostConstruct
    void init() {
        toolReferenceRepository = toolReferenceRepositoryInstance.get();
    }

    public ToolReference add(ToolReference toolReference) {
        // then registers the tool with the tool manager
        registerTool(toolReference);

        // if all goes well, persist the tool, so it can be loaded back when restarting
        return toolReferenceRepository.persist(toolReference);
    }

    public ToolReference add(ToolPayload toolPayload) {
        // First, provision the tool (i.e.: configuration, secrets, etc) in the target defined by the remote
        toolsResolver.provision(toolPayload);

        return add(toolPayload.getPayload());
    }

    private void registerTool(ToolReference toolReference) throws ToolNotFoundException {
        if (!StringHelper.isEmpty(toolReference.getNamespace())) {

            final Namespace namespace = namespacesBean.alocateNamespace(toolReference.getNamespace());

            Tool tool = toolsResolver.resolve(toolReference);
            ToolsHelper.registerTool(toolReference, toolManager, namespace, tool::call);

        } else {
            Tool tool = toolsResolver.resolve(toolReference);
            ToolsHelper.registerTool(toolReference, toolManager, tool::call);
        }
    }

    public List<ToolReference> list() {
        return toolReferenceRepository.listAll();
    }

    public List<ToolReference> list(String labelFilter) {
        if (StringUtils.isBlank(labelFilter)) {
            return toolReferenceRepository.listAll();
        }
        return toolReferenceRepository.findAllFilterByLabelExpression(labelFilter);
    }

    void loadTools(@Observes StartupEvent ev) {
        // Preload data
        namespacesBean.preload();

        for (ToolReference toolReference : list()) {
            try {
                registerTool(toolReference);
            } catch (EntityAlreadyExistsException e) {
                LOG.errorf(
                        "Error registering a tool named %s during startup, but it already exists",
                        toolReference.getName());
            }
        }
    }

    public int remove(String name) throws WanakuException {
        int removed = 0;
        try {
            removed = removeByName(name);
        } finally {
            if (removed > 0) {
                toolManager.removeTool(name);
            }
        }

        return removed;
    }

    public void update(ToolReference resource) {
        toolReferenceRepository.update(resource.getId(), resource);
    }

    public ToolReference getByName(String name) {
        List<ToolReference> tools = toolReferenceRepository.findByName(name);
        return tools.isEmpty() ? null : tools.getFirst();
    }

    @Override
    protected WanakuRepository<ToolReference, String> getRepository() {
        return toolReferenceRepository;
    }

    /**
     * Removes all tool references that match the given label expression.
     * <p>
     * This method extends the superclass implementation by also unregistering
     * each matching tool from the {@link ToolManager} before removing it from
     * the repository. This ensures that removed tools are no longer available
     * for execution by AI agents.
     * </p>
     * <p>
     * The removal process for each matching tool:
     * </p>
     * <ol>
     *   <li>Query all tools matching the label expression</li>
     *   <li>For each tool: unregister from ToolManager and remove from repository</li>
     *   <li>Return the count of successfully removed tools</li>
     * </ol>
     * <p>
     * For detailed information about label expression syntax and examples,
     * see the superclass documentation.
     * </p>
     *
     * @param labelExpression the label filter expression to match tools for removal
     * @return the number of tool references that were removed
     * @throws WanakuException if the label expression is invalid or malformed, or if the
     *                         removal operation fails
     * @see LabelsAwareWanakuEntityBean#removeIf(String)
     * @see #remove(String)
     * @see ToolManager#removeTool(String)
     */
    @Override
    public int removeIf(String labelExpression) throws WanakuException {
        List<ToolReference> toRemove = list(labelExpression);
        toRemove.stream().map(t -> t.getName()).forEach(this::remove);
        return toRemove.size();
    }
}
