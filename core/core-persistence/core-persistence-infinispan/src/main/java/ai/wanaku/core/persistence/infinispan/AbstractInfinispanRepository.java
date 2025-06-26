package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.WanakuEntity;
import ai.wanaku.core.persistence.api.WanakuRepository;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public abstract class AbstractInfinispanRepository <A extends WanakuEntity<K>, K> implements WanakuRepository<A, K> {

    protected final EmbeddedCacheManager cacheManager;
    private final ReentrantLock lock = new ReentrantLock();

    protected AbstractInfinispanRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        this.cacheManager = cacheManager;

        configure(configuration);
    }

    @Override
    public A persist(A entity) {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());

        if (entity.getId() == null) {
            entity.setId(newId());
        }

        try {
            lock.lock();
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

            if (cache.put(id, entity) != null)  {
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
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new WanakuException(e);
        }
    }
    ;

    protected void configure(Configuration configuration) {
        cacheManager.defineConfiguration(entityName(), configuration);
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
    public boolean remove(Predicate<A> matching) {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());

        try {
            lock.lock();
            return cache.values().removeIf(matching);
        } finally {
            lock.unlock();
        }
    }
}
