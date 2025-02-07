package org.wanaku.routers.camel.resolvers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.wanaku.api.exceptions.ToolNotFoundException;
import org.wanaku.api.resolvers.ToolsResolver;
import org.wanaku.api.types.McpTool;
import org.wanaku.api.types.McpToolStatus;
import org.wanaku.routers.camel.proxies.ToolsProxy;

public class CamelToolsResolver implements ToolsResolver {
    private static final Logger LOG = Logger.getLogger(CamelResourceResolver.class);
    private final File indexFile;
    private final Map<String, ? extends ToolsProxy> proxies;

    public CamelToolsResolver(File indexFile, Map<String, ? extends ToolsProxy> proxies) {
        this.indexFile = indexFile;
        this.proxies = proxies;
    }

    @Override
    public File indexLocation() {
        return indexFile;
    }

    @Override
    public List<McpTool> list() {
        List<McpTool> all = new ArrayList<>();
        List<? extends ToolsProxy> list = proxies.values().stream().toList();
        for (ToolsProxy proxy : list) {
            LOG.infof("Querying proxy %s for managed tools", proxy.name());
            all.addAll(proxy.list(indexLocation()));
        }

        return all;
    }

    @Override
    public McpTool find(String name) throws ToolNotFoundException {
        List<? extends ToolsProxy> list = proxies.values().stream().toList();
        for (ToolsProxy proxy : list) {
            LOG.infof("Querying proxy %s for managed tools", proxy.name());
            List<McpTool> managedTools = proxy.list(indexLocation());
            Optional<McpTool> first = managedTools.stream().filter(t -> t.name.equals(name)).findFirst();
            if (first.isPresent()) {
                return first.get();
            }
        }

        throw ToolNotFoundException.forName(name);
    }

    @Override
    public McpToolStatus call(McpTool tool, Map<String, Object> properties) {
        List<? extends ToolsProxy> list = proxies.values().stream().toList();
        for (ToolsProxy proxy : list) {
            McpToolStatus call = proxy.call(tool, properties);
            return call;
        }

        return null;
    }
}
