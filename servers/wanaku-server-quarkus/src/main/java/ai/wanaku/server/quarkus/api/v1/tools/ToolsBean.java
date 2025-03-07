package ai.wanaku.server.quarkus.api.v1.tools;

import java.io.File;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;
import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.util.IndexHelper;

@ApplicationScoped
public class ToolsBean {
    private static final Logger LOG = Logger.getLogger(ToolsBean.class);

    @Inject
    ToolManager toolManager;

    @Inject
    ToolsResolver toolsResolver;

    public void add(ToolReference mcpResource) {
        registerTool(mcpResource);

        File indexFile = toolsResolver.indexLocation();
        try {
            List<ToolReference> toolReferences = IndexHelper.loadToolsIndex(indexFile);
            toolReferences.add(mcpResource);
            IndexHelper.saveToolsIndex(indexFile, toolReferences);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        File indexFile = toolsResolver.indexLocation();
        try {
            return IndexHelper.loadToolsIndex(indexFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // TODO:
    private Class<?> toType(ToolReference mcpResource) {
        return String.class;
    }

    void loadTools(@Observes StartupEvent ev) {
        File indexFile = toolsResolver.indexLocation();
        if (!indexFile.exists()) {
            LOG.warnf("Index file not found: %s", indexFile);
            return;
        }

        try {
            List<ToolReference> toolReferences = IndexHelper.loadToolsIndex(indexFile);
            for (ToolReference toolReference : toolReferences) {
                registerTool(toolReference);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to load tools from file: %s", indexFile);
            throw new RuntimeException(e);
        }
    }

    public void remove(String name) {
        toolManager.removeTool(name);

        File indexFile = toolsResolver.indexLocation();
        try {
            List<ToolReference> toolReferences = IndexHelper.loadToolsIndex(indexFile);
            toolReferences.removeIf(tool -> tool.getName().equals(name));
            IndexHelper.saveToolsIndex(indexFile, toolReferences);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to remove tools from file: %s", indexFile);
            throw new RuntimeException(e);
        }
    }
}
