package ai.wanaku.core.service.discovery;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.mcp.providers.Repository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.valkey.JedisPool;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class ValkeyRepository<T> implements Repository<T> {
    private static final Logger LOG = Logger.getLogger(ValkeyRegistry.class);

    @Inject
    JedisPool jedisPool;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void save(String keyPrefix, String keySuffix, T data) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            try {
                String saved = jedis.set(keyPrefix + ":" + keySuffix, mapper.writeValueAsString(data));
                LOG.debugf("Entity saved: %s", saved);
            } catch (JsonProcessingException e) {
                throw new WanakuException("Error while converting Tool or Resource to JSON", e);
            }
        }
    }

    @Override
    public void delete(String keyPrefix, String keySuffix) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            long removed = jedis.del(keyPrefix + ":" + keySuffix);

            if(removed != 1) {
                throw getException();
            }
        }
    }

    public T get(String keyPrefix, String keySuffix) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            try {
                String data = jedis.get(keyPrefix + ":" + keySuffix);

                if (data == null) {
                    throw getException();
                }

                return mapper.readValue(data, getCls());
            } catch (JsonProcessingException e) {
                throw new WanakuException("Can't convert JSON to Object", e);
            }
        }
    }

    public List<T> getAll(String keyPrefix) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            try {
                Set<String> keys = jedis.keys(keyPrefix + ":*");

                if (keys == null || keys.isEmpty()) {
                    return List.of();
                }

                List<T> result = new ArrayList<>();
                for (String key : keys) {
                    result.add(mapper.readValue(jedis.get(key), getCls()));
                }

                return result;
            } catch (JsonProcessingException e) {
                throw new WanakuException("Can't convert JSON to Object", e);
            }
        }
    }

    protected abstract Class<T> getCls();

    protected abstract WanakuException getException();
}
