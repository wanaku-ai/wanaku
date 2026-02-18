package ai.wanaku.core.persistence.infinispan;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuEntity;
import ai.wanaku.core.persistence.api.WanakuRepository;

public abstract class AbstractInfinispanRepository<A extends WanakuEntity<K>, K> implements WanakuRepository<A, K> {

    protected final EmbeddedCacheManager cacheManager;
    protected final ReentrantLock lock = new ReentrantLock();

    private static final String DEFAULT_DELETE_TEMPLATE = "DELETE FROM %s r WHERE %s";

    protected AbstractInfinispanRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        this.cacheManager = cacheManager;

        configure(configuration);
    }

    @Override
    public A persist(A entity) {
        try {
            lock.lock();
            if (entity.getId() == null) {
                entity.setId(newId());
            }

            final Cache<Object, A> cache = cacheManager.getCache(entityName());
            cache.put(entity.getId(), entity);
        } finally {
            lock.unlock();
        }
        return entity;
    }

    @Override
    public List<A> listAll() {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());
        return cache.values().stream().toList();
    }

    @Override
    public boolean deleteById(K id) {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());

        if (id == null) {
            return false;
        }

        return cache.remove(id) != null;
    }

    @Override
    public A findById(K id) {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());
        return cache.get(id);
    }

    @Override
    public boolean update(K id, A entity) {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());

        try {
            lock.lock();

            if (cache.put(id, entity) != null) {
                return true;
            }
        } finally {
            lock.unlock();
        }

        return true;
    }

    public boolean update(K id, Consumer<A> consumer) {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());

        try {
            lock.lock();
            A entity = findById(id);
            if (entity == null) {
                entity = newEntity();
                entity.setId(id);
            }

            consumer.accept(entity);
            if (cache.put(id, entity) != null) {
                return true;
            }
        } finally {
            lock.unlock();
        }

        return false;
    }

    protected abstract Class<A> entityType();

    protected abstract String entityName();

    protected abstract K newId();

    protected A newEntity() {
        try {
            return entityType().getDeclaredConstructor().newInstance();
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new WanakuException(e);
        }
    }

    protected void configure(Configuration configuration) {
        // Only define configuration if it doesn't already exist
        // This prevents errors when multiple tests share the same Quarkus session
        if (cacheManager.getCacheConfiguration(entityName()) == null) {
            cacheManager.defineConfiguration(entityName(), configuration);
        }
    }

    // For testing only
    protected void deleteALl() {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());

        try {
            lock.lock();
            cache.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Deprecated
    public boolean remove(Predicate<A> matching) {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());

        try {
            lock.lock();
            return cache.values().removeIf(matching);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes entities where the specified field matches the given value.
     * Uses an Ickle query for efficient bulk deletion.
     *
     * @param fieldName the name of the field to match against, must not be null or blank
     * @param fieldValue the value that the field must equal for removal, must not be null
     * @return the number of entities removed
     * @throws IllegalArgumentException if fieldName is null/blank or fieldValue is null
     * @throws WanakuException if the query execution fails
     */
    public int removeByField(String fieldName, Object fieldValue) {
        return removeByFields(Map.of(fieldName, fieldValue));
    }

    /**
     * Removes entities where all specified fields match their corresponding values.
     * Uses an Ickle query with AND conditions for efficient bulk deletion.
     *
     * @param fields map of field names to their required values for removal
     * @return the number of entities removed
     * @throws IllegalArgumentException if fields map is null or empty
     * @throws WanakuException if the query execution fails
     */
    public int removeByFields(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Fields map cannot be null or empty");
        }
        var cache = cacheManager.getCache(entityName());
        var whereClause = fields.keySet().stream()
                .map(field -> "r.%s = :%s".formatted(field, field))
                .collect(Collectors.joining(" AND "));

        var queryString = DEFAULT_DELETE_TEMPLATE.formatted(entityType().getCanonicalName(), whereClause);
        var query = cache.query(queryString);

        // Set all parameters
        fields.forEach(query::setParameter);

        return query.executeStatement();
    }

    @Override
    public int size() {
        final Cache<Object, Namespace> cache = cacheManager.getCache(entityName());

        return cache.size();
    }

    @Override
    public int removeAll() {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());
        int sizeBefore = cache.size();
        cache.values().removeIf(x -> true);
        return sizeBefore - cache.size();
    }

    public boolean exists(K key) {
        return findById(key) != null;
    }
}
