package ai.wanaku.server.quarkus.api.v1.tools;

import ai.wanaku.core.persistence.api.WanakuRepository;
import ai.wanaku.server.quarkus.common.AbstractBean;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.io.ToolPayload;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import ai.wanaku.server.quarkus.common.ToolsHelper;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.runtime.StartupEvent;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ToolsBean extends AbstractBean<ToolReference> {
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
        // then registers the tool with the tool manager
        registerTool(toolReference);

        // if all goes well, persist the tool, so it can be loaded back when restarting
        return toolReferenceRepository.persist(toolReference);
    }

    public ToolReference add(ToolPayload toolPayload) {
        // First, provision the tool (i.e.: configuration, secrets, etc) in the target defined by the remote
        toolsResolver.provision(toolPayload);

        return add(toolPayload.getToolReference());
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

    public void update(ToolReference resource) {
        toolReferenceRepository.update(resource.getId(), resource);
    }

    public ToolReference getByName(String name) {
        List<ToolReference> tools =  toolReferenceRepository.findByName(name);
        return tools.isEmpty() ? null : tools.getFirst();
    }

    public ToolReference getById(String id) {
        return toolReferenceRepository.findById(id);
    }

    @Override
    protected  WanakuRepository<ToolReference, String> getRepository() {
        return toolReferenceRepository;
    }
}
