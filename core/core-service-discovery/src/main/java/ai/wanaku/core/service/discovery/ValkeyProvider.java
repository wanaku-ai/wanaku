package ai.wanaku.core.service.discovery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.valkey.JedisPool;
import io.valkey.JedisPoolConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ValkeyProvider {

    @ConfigProperty(name = "valkey.host", defaultValue = "localhost")
    String host;

    @ConfigProperty(name = "valkey.port", defaultValue = "6379")
    int port;

    @ConfigProperty(name = "valkey.timeout", defaultValue = "10")
    int timeout;

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
