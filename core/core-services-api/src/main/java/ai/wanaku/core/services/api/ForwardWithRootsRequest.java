package ai.wanaku.core.services.api;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;

/**
 * Request payload for adding a forward reference with MCP roots configuration.
 * <p>
 * The roots are directories or resources that the upstream MCP server is allowed to access.
 * Some MCP servers (e.g., the filesystem server) require at least one root and will refuse
 * to operate if the client returns an empty roots list when the server sends a
 * {@code roots/list} request.
 */
public class ForwardWithRootsRequest {
    private ForwardReference forward;
    private List<RootEntry> roots;

    public ForwardWithRootsRequest() {}

    public ForwardReference getForward() {
        return forward;
    }

    public void setForward(ForwardReference forward) {
        this.forward = forward;
    }

    public List<RootEntry> getRoots() {
        return roots;
    }

    public void setRoots(List<RootEntry> roots) {
        this.roots = roots;
    }

    /**
     * Represents a single MCP root entry with a URI and optional name.
     */
    public static class RootEntry {
        private String uri;
        private String name;

        public RootEntry() {}

        public RootEntry(String uri, String name) {
            this.uri = uri;
            this.name = name;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
