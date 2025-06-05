package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.WanakuEntity;
import ai.wanaku.core.persistence.api.WanakuRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public abstract class AbstractInfinispanRepository<A extends WanakuEntity<K>, K> implements WanakuRepository<A, K> {

    protected final EmbeddedCacheManager cacheManager;
    private final ObjectMapper mapper;
    private final ReentrantLock lock = new ReentrantLock();

    protected AbstractInfinispanRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        this.cacheManager = cacheManager;
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

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

      //  try {
            try {
                lock.lock();
                cache.put(id, entity);
                /*if (cache.put(id, mapper.writeValueAsString(entity)) != null) {
                    return true;
                }*/
            } finally {
                lock.unlock();
            }

    /*    } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }*/

        return false;
    }

    public boolean update(K id, Consumer<A> consumer) {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());

     //   try {
            try {
                lock.lock();
                A entity = findById(id);
                if (entity == null) {
                    entity = newEntity();
                    entity.setId(id);
                }

                consumer.accept(entity);
                cache.put(id, entity);
                /*if (cache.put(id, mapper.writeValueAsString(entity)) != null) {
                    return true;
                }*/
            } finally {
                lock.unlock();
            }

       /* } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }*/

        return false;
    }

    protected abstract Class<A> entityType();

    protected abstract String entityName();

    protected abstract K newId();

    protected A newEntity() {
        try {
            return entityType().getDeclaredConstructor().newInstance();
        } catch (InstantiationException e) {
            throw new WanakuException(e);
        } catch (IllegalAccessException e) {
            throw new WanakuException(e);
        } catch (InvocationTargetException e) {
            throw new WanakuException(e);
        } catch (NoSuchMethodException e) {
            throw new WanakuException(e);
        }
    };

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
}
