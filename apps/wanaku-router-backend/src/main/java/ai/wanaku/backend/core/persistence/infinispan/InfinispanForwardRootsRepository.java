package ai.wanaku.backend.core.persistence.infinispan;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.backend.bridge.ForwardRoots;

/**
 * Infinispan-based repository for persisting {@link ForwardRoots} entries.
 * <p>
 * Each entry is keyed by forward name and stores the root name-to-URI mappings
 * that the MCP client should advertise to the upstream server.
 */
public class InfinispanForwardRootsRepository {
    private static final String CACHE_NAME = "forwardRoots";

    private final EmbeddedCacheManager cacheManager;
    private final ReentrantLock lock = new ReentrantLock();

    public InfinispanForwardRootsRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        this.cacheManager = cacheManager;

        if (cacheManager.getCacheConfiguration(CACHE_NAME) == null) {
            cacheManager.defineConfiguration(CACHE_NAME, configuration);
        }
    }

    /**
     * Stores or replaces the roots configuration for a forward.
     *
     * @param forwardRoots the roots configuration to store
     */
    public void store(ForwardRoots forwardRoots) {
        if (forwardRoots == null || forwardRoots.getForwardName() == null) {
            return;
        }
        try {
            lock.lock();
            Cache<String, ForwardRoots> cache = cacheManager.getCache(CACHE_NAME);
            cache.put(forwardRoots.getForwardName(), forwardRoots);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves the roots configuration for a forward.
     *
     * @param forwardName the forward name
     * @return an Optional containing the roots if found, empty otherwise
     */
    public Optional<ForwardRoots> findByName(String forwardName) {
        if (forwardName == null) {
            return Optional.empty();
        }
        Cache<String, ForwardRoots> cache = cacheManager.getCache(CACHE_NAME);
        return Optional.ofNullable(cache.get(forwardName));
    }

    /**
     * Removes the roots configuration for a forward.
     *
     * @param forwardName the forward name
     * @return true if the entry was removed, false if it did not exist
     */
    public boolean remove(String forwardName) {
        if (forwardName == null) {
            return false;
        }
        Cache<String, ForwardRoots> cache = cacheManager.getCache(CACHE_NAME);
        return cache.remove(forwardName) != null;
    }
}
