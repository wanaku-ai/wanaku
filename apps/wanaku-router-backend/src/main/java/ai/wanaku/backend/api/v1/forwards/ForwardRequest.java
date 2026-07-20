package ai.wanaku.backend.api.v1.forwards;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;

/**
 * Request payload for adding or updating a forward reference with optional MCP roots.
 * <p>
 * The roots field configures root directories that are advertised to the upstream MCP
 * server when it sends a {@code roots/list} request. This is required by servers such as
 * the filesystem server, which need to know which directories they are allowed to access.
 * <p>
 * Each root entry contains a URI (e.g., {@code file:///path/to/dir}) and an optional
 * human-readable name.
 */
public class ForwardRequest {
    private ForwardReference forward;
    private List<RootEntry> roots;

    public ForwardRequest() {}

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
