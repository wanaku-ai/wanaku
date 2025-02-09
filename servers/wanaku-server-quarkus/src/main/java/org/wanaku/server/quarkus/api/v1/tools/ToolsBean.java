package org.wanaku.server.quarkus.api.v1.tools;

import java.io.File;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.wanaku.api.resolvers.ToolsResolver;
import org.wanaku.api.types.ToolReference;
import org.wanaku.core.util.IndexHelper;

@ApplicationScoped
public class ToolsBean {
    @Inject
    ToolsResolver toolsResolver;

    public void add(ToolReference mcpResource) {
        File indexFile = toolsResolver.indexLocation();
        try {
            List<ToolReference> toolReferences = IndexHelper.loadToolsIndex(indexFile);
            toolReferences.add(mcpResource);
            IndexHelper.saveToolsIndex(indexFile, toolReferences);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
