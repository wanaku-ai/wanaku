package ai.wanaku.core.services.api;

import java.util.Map;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;

/**
 * Request wrapper that pairs a {@link ForwardReference} with optional
 * MCP root configurations.
 * <p>
 * Some upstream MCP servers (e.g., the filesystem server) require the client
 * to advertise root directories via {@code roots/list}. This wrapper lets
 * callers specify root mappings alongside the forward definition.
 * <p>
 * The JSON representation flattens the ForwardReference fields into the
 * top level, so the body looks like:
 * <pre>{@code
 * {
 *   "name": "filesystem",
 *   "address": "http://localhost:3001/sse",
 *   "namespace": "...",
 *   "roots": {
 *     "myroot": "file:///home/user/data"
 *   }
 * }
 * }</pre>
 * <p>
 * Backward-compatible: clients that omit the {@code roots} field create a
 * forward without roots (same behavior as before).
 */
public class ForwardRequest {

    private String name;
    private String address;
    private String namespace;
    private Map<String, String> roots;

    public ForwardRequest() {}

    /**
     * Converts the flat fields into a {@link ForwardReference}.
     */
    public ForwardReference toForwardReference() {
        ForwardReference ref = new ForwardReference();
        ref.setName(name);
        ref.setAddress(address);
        ref.setNamespace(namespace);
        return ref;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Map<String, String> getRoots() {
        return roots;
    }

    public void setRoots(Map<String, String> roots) {
        this.roots = roots;
    }
}
