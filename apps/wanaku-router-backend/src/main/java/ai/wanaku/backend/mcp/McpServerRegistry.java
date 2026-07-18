package ai.wanaku.backend.mcp;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Route;
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
    private static final Set<String> PROTECTED_NAMESPACES =
            Set.of(DEFAULT_NAMESPACE, INTERNAL_NAMESPACE, PUBLIC_NAMESPACE);

    private final Map<String, McpSyncServer> servers = new ConcurrentHashMap<>();
    private final Map<String, VertxStreamableTransportProvider> transportProviders = new ConcurrentHashMap<>();
    private final Map<String, List<Route>> namespaceRoutes = new ConcurrentHashMap<>();

    @Inject
    Router router;

    void init(@Observes @Priority(1) StartupEvent ev) {
        createServerIfAbsent(INTERNAL_NAMESPACE, "/" + INTERNAL_NAMESPACE + "/mcp");
        createServerIfAbsent(PUBLIC_NAMESPACE, "/" + PUBLIC_NAMESPACE + "/mcp");
        addAlias(DEFAULT_NAMESPACE, PUBLIC_NAMESPACE, "/mcp");
        LOG.infof("MCP Server Registry initialized with %d namespaces", servers.size());
    }

    public synchronized void createServerIfAbsent(String namespace, String basePath) {
        if (servers.containsKey(namespace)) {
            return;
        }

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
                .validateToolInputs(false)
                .build();

        String pathWithSlash = basePath.endsWith("/") ? basePath : basePath + "/";
        List<Route> routes = new ArrayList<>();
        routes.add(router.route(basePath).handler(BodyHandler.create()));
        routes.add(router.route(pathWithSlash).handler(BodyHandler.create()));
        routes.add(router.post(basePath).blockingHandler(transportProvider.postHandler(), false));
        routes.add(router.post(pathWithSlash).blockingHandler(transportProvider.postHandler(), false));
        routes.add(router.get(basePath).blockingHandler(transportProvider.getHandler(), false));
        routes.add(router.get(pathWithSlash).blockingHandler(transportProvider.getHandler(), false));
        routes.add(router.delete(basePath).blockingHandler(transportProvider.deleteHandler(), false));
        routes.add(router.delete(pathWithSlash).blockingHandler(transportProvider.deleteHandler(), false));

        servers.put(namespace, server);
        transportProviders.put(namespace, transportProvider);
        namespaceRoutes.put(namespace, routes);
        LOG.infof("Registered MCP server for namespace '%s' at path '%s'", namespace, basePath);
    }

    private synchronized void addAlias(String alias, String targetNamespace, String basePath) {
        VertxStreamableTransportProvider targetProvider = transportProviders.get(targetNamespace);
        McpSyncServer targetServer = servers.get(targetNamespace);
        if (targetProvider == null || targetServer == null) {
            throw new IllegalStateException("Target namespace not found: " + targetNamespace);
        }

        String pathWithSlash = basePath.endsWith("/") ? basePath : basePath + "/";
        List<Route> routes = new ArrayList<>();
        routes.add(router.route(basePath).handler(BodyHandler.create()));
        routes.add(router.route(pathWithSlash).handler(BodyHandler.create()));
        routes.add(router.post(basePath).blockingHandler(targetProvider.postHandler(), false));
        routes.add(router.post(pathWithSlash).blockingHandler(targetProvider.postHandler(), false));
        routes.add(router.get(basePath).blockingHandler(targetProvider.getHandler(), false));
        routes.add(router.get(pathWithSlash).blockingHandler(targetProvider.getHandler(), false));
        routes.add(router.delete(basePath).blockingHandler(targetProvider.deleteHandler(), false));
        routes.add(router.delete(pathWithSlash).blockingHandler(targetProvider.deleteHandler(), false));

        servers.put(alias, targetServer);
        namespaceRoutes.put(alias, routes);
        LOG.infof("Registered alias '%s' at path '%s' -> namespace '%s'", alias, basePath, targetNamespace);
    }

    public synchronized void removeServer(String namespace) {
        if (PROTECTED_NAMESPACES.contains(namespace)) {
            LOG.warnf("Refusing to remove protected namespace MCP server: %s", namespace);
            return;
        }

        McpSyncServer server = servers.remove(namespace);
        if (server != null) {
            try {
                server.close();
            } catch (Exception e) {
                LOG.debugf(e, "Error closing MCP server for namespace %s", namespace);
            }
        }

        transportProviders.remove(namespace);

        List<Route> routes = namespaceRoutes.remove(namespace);
        if (routes != null) {
            routes.forEach(route -> {
                try {
                    route.remove();
                } catch (Exception e) {
                    LOG.debugf(e, "Error removing route for namespace %s", namespace);
                }
            });
        }

        LOG.infof("Removed MCP server for namespace '%s'", namespace);
    }

    public boolean hasServer(String namespace) {
        return servers.containsKey(namespace);
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
        namespaceRoutes.clear();
    }
}
