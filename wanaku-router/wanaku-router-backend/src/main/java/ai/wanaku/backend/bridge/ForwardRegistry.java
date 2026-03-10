package ai.wanaku.backend.bridge;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.types.NameNamespacePair;

/**
 * Registry for managing MCP forward clients.
 * <p>
 * Tracks {@link ForwardClient} entries that pair a remote MCP server address
 * with a cached {@link dev.langchain4j.mcp.client.McpClient}, keyed by
 * {@link NameNamespacePair}.
 */
@ApplicationScoped
public class ForwardRegistry {
    private static final Logger LOG = Logger.getLogger(ForwardRegistry.class);

    private final Map<NameNamespacePair, ForwardClient> clients = new ConcurrentHashMap<>();

    public void link(NameNamespacePair service, ForwardClient forwardClient) {
        clients.put(service, forwardClient);
    }

    public void unlink(NameNamespacePair service) {
        ForwardClient removed = clients.remove(service);
        if (removed != null) {
            try {
                removed.client().close();
            } catch (Exception e) {
                LOG.warnf("Failed to close MCP client for %s: %s", removed.address(), e.getMessage());
            }
        }
    }

    public ForwardClient getClient(NameNamespacePair service) {
        return clients.get(service);
    }

    public Map<NameNamespacePair, ForwardClient> clients() {
        return clients;
    }
}
