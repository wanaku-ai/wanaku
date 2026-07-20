package ai.wanaku.backend.bridge;

import java.util.ArrayList;
import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.WanakuEntity;

/**
 * Stores root directory URIs associated with a forward reference.
 * <p>
 * Some upstream MCP servers (e.g., the MCP filesystem server) require the client
 * to provide root directories via the {@code roots/list} capability. This entity
 * stores the configured roots for a given forward, keyed by the forward name.
 */
public class ForwardRoots implements WanakuEntity<String> {
    private String name;
    private List<String> rootUris;

    public ForwardRoots() {
        this.rootUris = new ArrayList<>();
    }

    public ForwardRoots(String name, List<String> rootUris) {
        this.name = name;
        this.rootUris = rootUris != null ? new ArrayList<>(rootUris) : new ArrayList<>();
    }

    @Override
    public String getId() {
        return name;
    }

    @Override
    public void setId(String id) {
        this.name = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getRootUris() {
        return rootUris;
    }

    public void setRootUris(List<String> rootUris) {
        this.rootUris = rootUris;
    }
}
