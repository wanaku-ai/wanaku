package ai.wanaku.core.persistence.infinispan.remote;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.infinispan.client.hotrod.RemoteCache;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.WanakuEntity;
import ai.wanaku.core.persistence.api.WanakuRepository;

public abstract class AbstractRemoteInfinispanRepository<A extends WanakuEntity<K>, K>
        implements WanakuRepository<A, K, K> {

    protected final RemoteCache<Object, A> cache;
    protected final ReentrantLock lock = new ReentrantLock();

    private static final String DEFAULT_DELETE_TEMPLATE = "DELETE FROM %s r WHERE %s";

    protected AbstractRemoteInfinispanRepository(RemoteCache<Object, A> cache) {
        this.cache = cache;
    }

    @Override
    public A persist(A entity) {
        try {
            lock.lock();
            if (entity.getId() == null) {
                entity.setId(newId());
            }
            cache.put(entity.getId(), entity);
        } finally {
            lock.unlock();
        }
        return entity;
    }

    @Override
    public List<A> listAll() {
        return cache.values().stream().toList();
    }

    @Override
    public boolean deleteById(K id) {
        if (id == null) {
            return false;
        }
        // HotRod RemoteCache.remove() returns null by default (no FORCE_RETURN_VALUE).
        // Check existence first, then remove.
        if (!cache.containsKey(id)) {
            return false;
        }
        cache.remove(id);
        return true;
    }

    @Override
    public A findById(K id) {
        return (A) cache.get(id);
    }

    @Override
    public boolean update(K id, A entity) {
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

    @Override
    @Deprecated
    public boolean remove(Predicate<A> matching) {
        try {
            lock.lock();
            return cache.values().removeIf(matching);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int removeByField(String fieldName, Object fieldValue) {
        return removeByFields(Map.of(fieldName, fieldValue));
    }

    @Override
    public int removeByFields(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Fields map cannot be null or empty");
        }
        var whereClause = fields.keySet().stream()
                .map(field -> "r.%s = :%s".formatted(field, field))
                .collect(Collectors.joining(" AND "));
        var queryString = DEFAULT_DELETE_TEMPLATE.formatted(entityType().getCanonicalName(), whereClause);
        var query = cache.query(queryString);
        fields.forEach(query::setParameter);
        return query.executeStatement();
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public int removeAll() {
        int sizeBefore = cache.size();
        cache.clear();
        return sizeBefore - cache.size();
    }

    @Override
    public boolean exists(K key) {
        return findById(key) != null;
    }
}
