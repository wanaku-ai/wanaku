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

    private final Map<NameNamespacePair, String> clients = new ConcurrentHashMap<>();

    /**
     * Links a forward client to a service identifier.
     * <p>
     * Registers the provided address under the given {@link NameNamespacePair},
     * making it available for subsequent lookups. If a client already exists for the given
     * service identifier, it will be replaced without closing the previous client.
     *
     * @param service the service identifier (name and namespace pair)
     * @param forwardClientAddress the forward client address to register
     * @see ForwardClient
     */
    public void link(NameNamespacePair service, String forwardClientAddress) {
        clients.put(service, forwardClientAddress);
    }

    /**
     * Unlinks and closes a forward client address associated with a service identifier.
     * <p>
     * Removes the address associated with the given {@link NameNamespacePair}
     * <p>
     * This method is idempotent - calling it multiple times with the same service identifier
     * has no effect after the first call.
     *
     * @param service the service identifier (name and namespace pair)
     */
    public void unlink(NameNamespacePair service) {
        clients.remove(service);
    }

    /**
     * Retrieves the forward client address associated with a service identifier.
     * <p>
     * Returns the address registered under the given {@link NameNamespacePair},
     * or {@code null} if no client is registered for that identifier.
     *
     * @param service the service identifier (name and namespace pair)
     * @return the forward client, or {@code null} if not found
     * @see ForwardClient
     */
    public String getClientAddress(NameNamespacePair service) {
        return clients.get(service);
    }

    /**
     * Returns an unmodifiable view of all registered forward clients.
     * <p>
     * The returned map contains all currently registered addresses for {@link ForwardClient} instances,
     * keyed by their {@link NameNamespacePair} identifiers. The map is a snapshot and
     * modifications to it will not affect the registry.
     *
     * @return an unmodifiable map of all registered forward client addresses
     */
    public Map<NameNamespacePair, String> clients() {
        return clients;
    }
}
