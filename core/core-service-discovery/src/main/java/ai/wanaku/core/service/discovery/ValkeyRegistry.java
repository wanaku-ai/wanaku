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

    /**
     * Registers a new service with the given configurations.
     *
     * @param serviceTarget The service target, including its address and type.
     * @param configurations A map of configuration key-value pairs for the service.
     */
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

    /**
     * Deregisters a service with the given name.
     *
     * @param service The name of the service to deregister.
     */
    @Override
    public void deregister(String service) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            jedis.del(service);
            LOG.infof("Service %s registered", service);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to register service %s: %s", service, e.getMessage());
        }
    }

    /**
     * Retrieves a service with the given name.
     *
     * @param service The name of the service to retrieve.
     * @return A Service object representing the retrieved service.
     */
    @Override
    public Service getService(String service) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            return newService(jedis, service);
        }
    }

    /**
     * Retrieves a map of services with the given type.
     *
     * @param serviceType The type of services to retrieve.
     * @return A map of Service objects representing the retrieved services.
     */
    @Override
    public Map<String, Service> getEntries(ServiceType serviceType) {
        Map<String, Service> entries = new HashMap<>();
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys("*");
            for (String key : keys) {
                String sType = jedis.hget(key, ReservedKeys.WANAKU_TARGET_TYPE);
                if (serviceType.asValue().equals(sType)) {
                    Service service = newService(jedis, key);

                    entries.put(key, service);
                }
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed list services: %s", e.getMessage());
        }
        return entries;
    }


    /**
     * Creates a new Service object from the given hashmap key.
     *
     * @param jedis The Jedis connection used to retrieve service information.
     * @param key   The hashmap key representing the service.
     * @return A Service object representing the created service.
     */
    private static Service newService(Jedis jedis, String key) {
        Set<String> configs = jedis.hkeys(key);
        return toService(jedis, key, configs);
    }


    /**
     * Creates a new Service object from the given hashmap key and configuration keys.
     *
     * @param jedis The Jedis connection used to retrieve service information.
     * @param key   The hashmap key representing the service.
     * @param configs The set of configuration keys for the service.
     * @return A Service object representing the created service.
     */
    private static Service toService(Jedis jedis, String key, Set<String> configs) {
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

        String address = jedis.hget(key, ReservedKeys.WANAKU_TARGET_ADDRESS);
        service.setTarget(address);

        return service;
    }


    /**
     * Creates a new Configuration object from the given hashmap key and configuration value.
     *
     * @param jedis The Jedis connection used to retrieve configuration information.
     * @param config The hashmap key representing the configuration.
     * @return A Configuration object representing the created configuration.
     */
    private static Configuration toConfiguration(Jedis jedis, String config) {
        Configuration configuration = new Configuration();
        configuration.setValue(config);
        String configDescription = jedis.hget(config, "description");

        configuration.setDescription(configDescription);
        return configuration;
    }


    /**
     * Shuts down the Jedis connection pool when the application is stopped.
     */
    void shutdown(@Observes ShutdownEvent shutdownEvent) {
        jedisPool.close();
    }
}
