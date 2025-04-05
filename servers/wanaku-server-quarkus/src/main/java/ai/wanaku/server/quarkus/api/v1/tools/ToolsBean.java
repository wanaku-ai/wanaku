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

    public void add(ToolReference mcpResource) {
        registerTool(mcpResource);
        toolReferenceRepository.persist(mcpResource);
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

    public void remove(String name) {
        try {
            toolManager.removeTool(name);
        } finally {
            toolReferenceRepository.deleteById(name);
        }
    }

    public void update(ToolReference resource) {
        toolReferenceRepository.update(resource.getName(), resource);
    }
}
