package ai.wanaku.backend.bridge;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores MCP root configurations for a forward reference.
 * <p>
 * Some upstream MCP servers (e.g., the filesystem server) require the client to
 * advertise root directories via {@code roots/list}. This entity persists the
 * root name-to-URI mappings for each forward, keyed by the forward name.
 */
public class ForwardRoots {

    private String forwardName;
    private Map<String, String> roots;

    public ForwardRoots() {
        this.roots = new HashMap<>();
    }

    public ForwardRoots(String forwardName, Map<String, String> roots) {
        this.forwardName = forwardName;
        this.roots = roots != null ? new HashMap<>(roots) : new HashMap<>();
    }

    public String getForwardName() {
        return forwardName;
    }

    public void setForwardName(String forwardName) {
        this.forwardName = forwardName;
    }

    public Map<String, String> getRoots() {
        return roots;
    }

    public void setRoots(Map<String, String> roots) {
        this.roots = roots != null ? roots : new HashMap<>();
    }
}
