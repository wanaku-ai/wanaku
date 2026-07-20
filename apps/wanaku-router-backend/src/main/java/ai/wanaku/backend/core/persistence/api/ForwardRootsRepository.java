package ai.wanaku.backend.core.persistence.api;

import ai.wanaku.backend.bridge.ForwardRoots;

/**
 * Repository interface for managing {@link ForwardRoots} entities.
 * <p>
 * Stores root directory URIs per forward reference name, used when connecting
 * to upstream MCP servers that require the {@code roots/list} capability.
 */
public interface ForwardRootsRepository extends WanakuRepository<ForwardRoots, String> {}
