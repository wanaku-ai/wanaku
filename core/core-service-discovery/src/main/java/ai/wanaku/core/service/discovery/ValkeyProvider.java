package ai.wanaku.core.service.discovery;

import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.valkey.JedisPool;
import io.valkey.JedisPoolConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Provides a configuration-driven connection to Valkey using Jedis.
 */
@LookupIfProperty(name = "wanaku.service.persistence", stringValue = "valkey")
@ApplicationScoped
public class ValkeyProvider {

    /**
     * The host address of the Redis instance (default: localhost).
     */
    @ConfigProperty(name = "valkey.host", defaultValue = "localhost")
    String host;

    /**
     * The port number of the Redis instance (default: 6379).
     */
    @ConfigProperty(name = "valkey.port", defaultValue = "6379")
    int port;

    /**
     * The connection timeout in seconds for the Redis instance (default: 10).
     */
    @ConfigProperty(name = "valkey.timeout", defaultValue = "10")
    int timeout;

    /**
     * Produces a JedisPool instance with the configured settings.
     *
     * @return A JedisPool instance for connecting to Redis.
     */
    @Produces
    public JedisPool redisClient() {
        JedisPoolConfig config = new JedisPoolConfig();

        // It is recommended that you set maxTotal = maxIdle = 2*minIdle for best performance
        config.setMaxTotal(32);
        config.setMaxIdle(32);
        config.setMinIdle(16);

        return new JedisPool(config, host, port, timeout, null);
    }
}
