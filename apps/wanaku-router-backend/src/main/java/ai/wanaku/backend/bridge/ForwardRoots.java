package ai.wanaku.backend.bridge;

import java.util.ArrayList;
import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.WanakuEntity;

/**
 * Stores the list of MCP roots configured for a forward reference.
 * <p>
 * This entity is persisted alongside forward references so that root
 * configuration survives server restarts. The {@code forwardName} field
 * matches the name of the corresponding {@link ai.wanaku.capabilities.sdk.api.types.ForwardReference}.
 */
public class ForwardRoots implements WanakuEntity<String> {
    private String id;
    private String forwardName;
    private List<ForwardRootEntry> roots = new ArrayList<>();

    public ForwardRoots() {}

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getForwardName() {
        return forwardName;
    }

    public void setForwardName(String forwardName) {
        this.forwardName = forwardName;
    }

    public List<ForwardRootEntry> getRoots() {
        return roots;
    }

    public void setRoots(List<ForwardRootEntry> roots) {
        this.roots = roots;
    }
}
