package ai.wanaku.backend.bridge;

/**
 * Represents a single MCP root entry with a URI and optional name.
 * <p>
 * MCP roots are directories or resources that an MCP server is allowed to access.
 * Some MCP servers (e.g., the filesystem server) require at least one root to be
 * configured, and will refuse to operate if the client returns an empty roots list.
 */
public class ForwardRootEntry {
    private String uri;
    private String name;

    public ForwardRootEntry() {}

    public ForwardRootEntry(String uri, String name) {
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
