package ai.wanaku.server.quarkus.api.v1.tools;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import ai.wanaku.server.quarkus.common.ToolsHelper;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.runtime.StartupEvent;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ToolsBean {
    private static final Logger LOG = Logger.getLogger(ToolsBean.class);

    @Inject
    ToolManager toolManager;

    @Inject
    ToolsResolver toolsResolver;

    @Inject
    Instance<ToolReferenceRepository> toolReferenceRepositoryInstance;

    private ToolReferenceRepository toolReferenceRepository;

    @PostConstruct
    void init() {
        toolReferenceRepository = toolReferenceRepositoryInstance.get();
    }

    public ToolReference add(ToolReference toolReference) {
        // First, merge properties (arguments) defined by the remote
        toolsResolver.loadProperties(toolReference);

        // then registers the tool with the tool manager
        registerTool(toolReference);

        // if all goes well, persist the tool, so it can be loaded back when restarting
        return toolReferenceRepository.persist(toolReference);
    }

    private void registerTool(ToolReference toolReference) throws ToolNotFoundException {
        LOG.debugf("Registering tool: %s", toolReference.getName());
        Tool tool = toolsResolver.resolve(toolReference);

        ToolsHelper.registerTool(toolReference, toolManager, tool::call);
    }


    public List<ToolReference> list() {
        return toolReferenceRepository.listAll();
    }

    void loadTools(@Observes StartupEvent ev) {
        for (ToolReference toolReference : list()) {
            registerTool(toolReference);
        }
    }

    private boolean removeReference(String name, ToolReference toolReference) {
        if (toolReference.getName().equals(name)) {
            try {
                toolManager.removeTool(name);
            } finally {
                toolReferenceRepository.deleteById(toolReference.getId());
            }

            return true;
        }

        return false;
    }

    public void remove(String name) {
        if (!toolReferenceRepository.remove(ref -> removeReference(name, ref))) {
            LOG.warnf("No references named %s where found", name);
        }
    }

    public void update(ToolReference resource) {
        toolReferenceRepository.update(resource.getName(), resource);
    }
}
