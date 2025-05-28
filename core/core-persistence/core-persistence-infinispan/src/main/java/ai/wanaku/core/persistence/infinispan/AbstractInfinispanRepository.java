package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.WanakuEntity;
import ai.wanaku.core.persistence.api.WanakuRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

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
        final Cache<Object, String> cache = cacheManager.getCache(entityName());

        try {
            if (entity.getId() == null) {
                entity.setId(newId());
            }

            String json = mapper.writeValueAsString(entity);

            try {
                lock.lock();
                cache.put(entity.getId(), json);
            } finally {
                lock.unlock();
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return entity;
    }

    @Override
    public List<A> listAll() {
        final Cache<Object, String> cache = cacheManager.getCache(entityName());
        return cache.values().stream().map(this::convert).toList();
    }

    private A convert(String data) {
        try {
            return mapper.readValue(data, entityType());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean deleteById(K id) {
        final Cache<Object, A> cache = cacheManager.getCache(entityName());

        if (id == null) {
            return false;
        }

        if (cache.remove(id) != null) {
            return true;
        }

        return false;
    }

    @Override
    public A findById(K id) {
        final Cache<Object, String> cache = cacheManager.getCache(entityName());

        try {
            final String strVal = cache.get(id);
            if (strVal == null) {
                return null;
            }

            return mapper.readValue(strVal, entityType());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean update(K id, A entity) {
        final Cache<Object, String> cache = cacheManager.getCache(entityName());

        try {
            try {
                lock.lock();
                if (cache.put(id, mapper.writeValueAsString(entity)) != null) {
                    return true;
                }
            } finally {
                lock.unlock();
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return false;
    }

    public boolean update(K id, Consumer<A> consumer) {
        final Cache<Object, String> cache = cacheManager.getCache(entityName());

        try {
            try {
                lock.lock();
                A entity = findById(id);
                if (entity == null) {
                    entity = newEntity();
                    entity.setId(id);
                }

                consumer.accept(entity);

                if (cache.put(id, mapper.writeValueAsString(entity)) != null) {
                    return true;
                }
            } finally {
                lock.unlock();
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

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
