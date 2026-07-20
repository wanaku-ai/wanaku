package ai.wanaku.core.services.api;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;

/**
 * Request object for adding or updating a forward reference with optional root directories.
 * <p>
 * Some upstream MCP servers (e.g., the MCP filesystem server) require the client to provide
 * a list of root directories via the {@code roots/list} capability. This class wraps a
 * {@link ForwardReference} together with an optional list of root URIs that will be passed
 * to the MCP client when connecting to the upstream server.
 */
public class ForwardRequest {
    private ForwardReference forwardReference;
    private List<String> roots;

    public ForwardRequest() {}

    public ForwardRequest(ForwardReference forwardReference, List<String> roots) {
        this.forwardReference = forwardReference;
        this.roots = roots;
    }

    public ForwardReference getForwardReference() {
        return forwardReference;
    }

    public void setForwardReference(ForwardReference forwardReference) {
        this.forwardReference = forwardReference;
    }

    public List<String> getRoots() {
        return roots;
    }

    public void setRoots(List<String> roots) {
        this.roots = roots;
    }
}
