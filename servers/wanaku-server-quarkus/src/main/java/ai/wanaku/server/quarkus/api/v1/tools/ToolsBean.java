package ai.wanaku.server.quarkus.api.v1.tools;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.providers.Repository;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class ToolsBean {
    private static final Logger LOG = Logger.getLogger(ToolsBean.class);

    @Inject
    ToolManager toolManager;

    @Inject
    ToolsResolver toolsResolver;

    @Inject
    Repository<ToolReference> repository;

    public void add(ToolReference mcpResource) {
        registerTool(mcpResource);

        String index = toolsResolver.index();
        repository.save(index, mcpResource.getName(), mcpResource);
    }

    private void registerTool(ToolReference toolReference) throws ToolNotFoundException {
        LOG.debugf("Registering tool: %s", toolReference.getName());
        Tool tool = toolsResolver.resolve(toolReference);

        ToolManager.ToolDefinition toolDefinition = toolManager.newTool(toolReference.getName())
                .setDescription(toolReference.getDescription());

        final boolean required = isRequired(toolReference);

        Class<?> type = toType(toolReference);
        toolReference.getInputSchema().getProperties().forEach((key, value) ->
                toolDefinition.addArgument(key, value.getDescription(), required, type));

        toolDefinition
                .setHandler(ta -> tool.call(toolReference, ta))
                .register();
    }

    private static boolean isRequired(ToolReference toolReference) {
        boolean required = false;
        List<String> requiredList = toolReference.getInputSchema().getRequired();

        if (requiredList != null) {
            required = requiredList.contains(toolReference.getName());
        }
        return required;
    }

    public List<ToolReference> list() {
        String index = toolsResolver.index();
        return repository.getAll(index);
    }

    // TODO:
    private Class<?> toType(ToolReference mcpResource) {
        return String.class;
    }

    void loadTools(@Observes StartupEvent ev) {
        List<ToolReference> tools = list();

        if (tools.isEmpty()) {
            LOG.warnf("Index %s not found", toolsResolver.index());
            return;
        }

        for (ToolReference toolReference : tools) {
            registerTool(toolReference);
        }
    }

    public void remove(String name) {
        toolManager.removeTool(name);

        String index = toolsResolver.index();
        repository.delete(index, name);
    }
}
