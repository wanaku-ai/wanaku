package ai.wanaku.backend.mcp;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import ai.wanaku.backend.mcp.transport.VertxStreamableTransportProvider;
import ai.wanaku.core.util.VersionHelper;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class McpServerRegistry {
    private static final Logger LOG = Logger.getLogger(McpServerRegistry.class);

    private static final String DEFAULT_NAMESPACE = "default";
    private static final String INTERNAL_NAMESPACE = "wanaku-internal";
    private static final String PUBLIC_NAMESPACE = "public";
    private static final int NUM_NAMESPACES = 10;

    private final Map<String, McpSyncServer> servers = new ConcurrentHashMap<>();
    private final Map<String, VertxStreamableTransportProvider> transportProviders = new ConcurrentHashMap<>();
    private final List<String> namespaceNames;

    @Inject
    Router router;

    public McpServerRegistry() {
        List<String> names = new ArrayList<>();
        names.add(DEFAULT_NAMESPACE);
        names.add(INTERNAL_NAMESPACE);
        for (int i = 0; i < NUM_NAMESPACES; i++) {
            names.add("ns-" + i);
        }
        names.add(PUBLIC_NAMESPACE);
        this.namespaceNames = Collections.unmodifiableList(names);
    }

    void init(@Observes @Priority(1) StartupEvent ev) {
        for (String namespace : namespaceNames) {
            String path;
            if (DEFAULT_NAMESPACE.equals(namespace)) {
                path = "/mcp";
            } else {
                path = "/" + namespace + "/mcp";
            }
            createServer(namespace, path);
        }
        LOG.infof("MCP Server Registry initialized with %d namespaces", servers.size());
    }

    private void createServer(String namespace, String basePath) {
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        VertxStreamableTransportProvider transportProvider = new VertxStreamableTransportProvider(jsonMapper);

        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo(new McpSchema.Implementation("Wanaku", VersionHelper.VERSION))
                .capabilities(new McpSchema.ServerCapabilities(
                        null,
                        null,
                        new McpSchema.ServerCapabilities.LoggingCapabilities(),
                        new McpSchema.ServerCapabilities.PromptCapabilities(true),
                        new McpSchema.ServerCapabilities.ResourceCapabilities(true, true),
                        new McpSchema.ServerCapabilities.ToolCapabilities(true)))
                .build();

        router.route(basePath).handler(BodyHandler.create());
        router.post(basePath).blockingHandler(transportProvider.postHandler(), false);
        router.get(basePath).blockingHandler(transportProvider.getHandler(), false);
        router.delete(basePath).blockingHandler(transportProvider.deleteHandler(), false);

        servers.put(namespace, server);
        transportProviders.put(namespace, transportProvider);
        LOG.debugf("Registered MCP server for namespace '%s' at path '%s'", namespace, basePath);
    }

    public McpSyncServer getServer(String namespace) {
        McpSyncServer server = servers.get(namespace);
        if (server == null) {
            throw new IllegalArgumentException("No MCP server for namespace: " + namespace);
        }
        return server;
    }

    public McpSyncServer getDefaultServer() {
        return getServer(DEFAULT_NAMESPACE);
    }

    public McpSyncServer getInternalServer() {
        return getServer(INTERNAL_NAMESPACE);
    }

    public McpSyncServer getPublicServer() {
        return getServer(PUBLIC_NAMESPACE);
    }

    public McpSyncServer getServerForPath(String path) {
        for (Map.Entry<String, McpSyncServer> entry : servers.entrySet()) {
            if (path != null && path.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        return getPublicServer();
    }

    public List<String> getNamespaceNames() {
        return namespaceNames;
    }

    @PreDestroy
    void shutdown() {
        servers.values().forEach(server -> {
            try {
                server.close();
            } catch (Exception e) {
                LOG.debugf(e, "Error closing MCP server");
            }
        });
        servers.clear();
        transportProviders.clear();
    }
}
