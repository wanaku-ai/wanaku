package ai.wanaku.backend.core.persistence.infinispan.discovery;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

/**
 * An in-memory-only Infinispan cache for service lookup results.
 * Entries expire automatically after a configurable TTL.
 */
public class ServiceLookupCache {
    private static final String CACHE_NAME = "service-lookup-cache";
    private static final String KEY_SEPARATOR = "|";

    private final Cache<String, List<ServiceTarget>> cache;

    public ServiceLookupCache(EmbeddedCacheManager cacheManager, long ttlSeconds) {
        Configuration config = new ConfigurationBuilder()
                .clustering()
                .cacheMode(CacheMode.LOCAL)
                .expiration()
                .lifespan(ttlSeconds, TimeUnit.SECONDS)
                .build();

        if (cacheManager.getCacheConfiguration(CACHE_NAME) == null) {
            cacheManager.defineConfiguration(CACHE_NAME, config);
        }

        this.cache = cacheManager.getCache(CACHE_NAME);
    }

    private static String key(String serviceName, String serviceType) {
        return serviceName + KEY_SEPARATOR + serviceType;
    }

    /**
     * Get a cached lookup result.
     *
     * @param serviceName the service name
     * @param serviceType the service type
     * @return the cached result, or null if not present or expired
     */
    public List<ServiceTarget> get(String serviceName, String serviceType) {
        return cache.get(key(serviceName, serviceType));
    }

    /**
     * Cache a lookup result.
     *
     * @param serviceName the service name
     * @param serviceType the service type
     * @param result the query result to cache
     */
    public void put(String serviceName, String serviceType, List<ServiceTarget> result) {
        cache.put(key(serviceName, serviceType), List.copyOf(result));
    }

    /**
     * Evict all entries whose key starts with the given service name.
     *
     * @param serviceName the service name to evict
     */
    public void evictByServiceName(String serviceName) {
        String prefix = serviceName + KEY_SEPARATOR;
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Clear all cached entries.
     */
    public void clear() {
        cache.clear();
    }
}
