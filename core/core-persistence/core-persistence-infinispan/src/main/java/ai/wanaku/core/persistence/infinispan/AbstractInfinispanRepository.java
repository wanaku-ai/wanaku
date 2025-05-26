package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.core.persistence.api.WanakuRepository;
import ai.wanaku.core.persistence.types.WanakuEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public abstract class AbstractInfinispanRepository<A, T extends WanakuEntity, K> implements WanakuRepository<A, T, K> {

    protected final EmbeddedCacheManager cacheManager;
    private final ObjectMapper mapper;

    protected AbstractInfinispanRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        this.cacheManager = cacheManager;
        mapper = new ObjectMapper();

        configure(configuration);

    }

    @Override
    public void persist(A model) {
        final Cache<Object, String> cache = cacheManager.getCache(entityName());

        T entity = convertToEntity(model);
        try {
            String json = mapper.writeValueAsString(entity);
            cache.put(entity.getId(), json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<A> listAll() {
        final Cache<Object, String> cache = cacheManager.getCache(entityName());
        return convertToModels(cache.values().stream().map(this::convert).toList());
    }

    private T convert(String data) {
        try {
            return mapper.readValue(data, entityType());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean deleteById(K id) {
        final Cache<Object, T> cache = cacheManager.getCache(entityName());

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

            return convertToModel(mapper.readValue(strVal, entityType()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean update(K id, A model) {
        final Cache<Object, String> cache = cacheManager.getCache(entityName());

        T entity =  convertToEntity(model);

        try {
            if (cache.put(id, mapper.writeValueAsString(entity)) != null) {
                return true;
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return false;
    }

    protected abstract Class<T> entityType();

    protected abstract String entityName();

    protected void configure(Configuration configuration) {
        cacheManager.defineConfiguration(entityName(), configuration);
    }


    // For testing only
    void deleteALl() {
        final Cache<Object, T> cache = cacheManager.getCache(entityName());

        cache.clear();
    }
}
