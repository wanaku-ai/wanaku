package ai.wanaku.backend.api.v1.tools;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.persistence.api.WanakuRepository;
import ai.wanaku.backend.common.AbstractBean;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.Namespace;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.io.ToolPayload;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import ai.wanaku.core.util.StringHelper;
import ai.wanaku.backend.api.v1.namespaces.NamespacesBean;
import ai.wanaku.backend.common.ToolsHelper;
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

    void loadTools(@Observes StartupEvent ev) {
        // Preload data
        namespacesBean.preload();

        for (ToolReference toolReference : list()) {
            registerTool(toolReference);
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
        List<ToolReference> tools =  toolReferenceRepository.findByName(name);
        return tools.isEmpty() ? null : tools.getFirst();
    }

    @Override
    protected  WanakuRepository<ToolReference, String> getRepository() {
        return toolReferenceRepository;
    }
}
