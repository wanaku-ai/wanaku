package ai.wanaku.core.service.discovery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;
import io.quarkus.runtime.ShutdownEvent;
import io.valkey.Jedis;
import io.valkey.JedisPool;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * Host information is stored as keys in a hashmap in Valkey. Suppose, for instance, a service
 * named "ftp". It would contain keys such as:
 *
 * wanaku-target-address -> ip:port
 * wanaku-target-type -> tool-invoker
 *
 * And also the configurations for the service as a hashmap
 * config1 -> description1, config2 -> description2, etc.
 *
 * This class is basically iterating over the hashmap
 */
@ApplicationScoped
public class ValkeyRegistry implements ServiceRegistry {
    private static final Logger LOG = Logger.getLogger(ValkeyRegistry.class);

    @Inject
    JedisPool jedisPool;

    @Override
    public void register(ServiceTarget serviceTarget, Map<String, String> configurations) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            jedis.hset(serviceTarget.getService(), ReservedKeys.WANAKU_TARGET_ADDRESS, serviceTarget.toAddress());
            jedis.hset(serviceTarget.getService(), ReservedKeys.WANAKU_TARGET_TYPE, serviceTarget.getServiceType().asValue());

            LOG.infof("Service %s with target %s registered", serviceTarget.getService(), serviceTarget.toAddress());

            for (var entry : configurations.entrySet()) {
                LOG.infof("Registering configuration %s for service %s", entry.getKey(), serviceTarget.getService());
                jedis.hset(serviceTarget.getService(), entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to register service %s: %s", serviceTarget.getService(), e.getMessage());
        }
    }

    @Override
    public void deregister(String service) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            jedis.del(service);
            LOG.infof("Service %s registered", service);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to register service %s: %s", service, e.getMessage());
        }
    }

    @Override
    public Service getService(String service) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            Set<String> configs = jedis.hkeys(service);
            return toService(jedis, configs);
        }
    }

    @Override
    public Map<String, Service> getEntries(ServiceType serviceType) {
        Map<String, Service> entries = new HashMap<>();
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys("*");
            for (String key : keys) {
                String sType = jedis.hget(key, ReservedKeys.WANAKU_TARGET_TYPE);
                if (serviceType.asValue().equals(sType)) {
                    Set<String> configs = jedis.hkeys(key);

                    Service service = toService(jedis, configs);
                    String address = jedis.hget(key, ReservedKeys.WANAKU_TARGET_ADDRESS);
                    service.setTarget(address);

                    entries.put(key, service);
                }
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed list services: %s", e.getMessage());
        }
        return entries;
    }

    private static Service toService(Jedis jedis, Set<String> configs) {
        Service service = new Service();

        Map<String, Configuration> configurationMap = new HashMap<>();

        for (String config : configs) {
            if (!ReservedKeys.ALL_KEYS.contains(config)) {
                Configuration configuration = toConfiguration(jedis, config);
                configurationMap.put(config, configuration);
            }
        }

        Configurations configurations = new Configurations();
        configurations.setConfigurations(configurationMap);
        service.setConfigurations(configurations);

        return service;
    }

    private static Configuration toConfiguration(Jedis jedis, String config) {
        Configuration configuration = new Configuration();
        configuration.setValue(config);
        String configDescription = jedis.hget(config, "description");

        configuration.setDescription(configDescription);
        return configuration;
    }

    void shutdown(@Observes ShutdownEvent shutdownEvent) {
        jedisPool.close();
    }
}
