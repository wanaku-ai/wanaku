package ai.wanaku.core.service.discovery;

import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.api.types.management.State;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.persistence.api.ServiceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class WanakuServiceRegistry implements ServiceRegistry {

    @Inject
    ServiceRepository serviceRepository;

    @Override
    public void register(ServiceTarget serviceTarget, Map<String, String> configurations) {
        Service service = serviceRepository.findById(serviceTarget.getService());
        if (service == null) {
            service = new Service();

            service.setName(serviceTarget.getService());
            service.setTarget(serviceTarget.toAddress());
            service.setServiceType(serviceTarget.getServiceType().asValue());
            Map<String, Configuration> configurationMap = new HashMap<>();
            for (Map.Entry<String, String> entry : configurations.entrySet()) {
                Configuration configuration = new Configuration();
                configuration.setDescription(entry.getValue());
                configurationMap.put(entry.getKey(), configuration);
            }
            Configurations config = new Configurations();
            config.setConfigurations(configurationMap);
            service.setConfigurations(config);

            serviceRepository.persist(service);
//          TODO  serviceRepository.persistOrUpdate(serviceEntity);
        } else if (!serviceTarget.toAddress().equals(service.getTarget())) {
            service.setTarget(serviceTarget.toAddress());
            serviceRepository.update(service);
        }
    }

    @Override
    public void deregister(String service, ServiceType serviceType) {
        serviceRepository.deleteById(service);
    }

    @Override
    public Service getService(String service) {
        return serviceRepository.findById(service);
    }

    @Override
    public void saveState(String service, boolean healthy, String message) {
        // TODO
//        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
//            Map<String, String> state = Map.of("service", service, "healthy",
//                    Boolean.toString(healthy), "message", (healthy ? "healthy" : message));
//
//            jedis.xadd(stateKey(service), state, XAddParams.xAddParams());
//        } catch (Exception e) {
//            LOG.errorf(e, "Failed to save state for %s: %s", service, e.getMessage());
//        }
    }

    @Override
    public List<State> getState(String service, int count) {
        // TODO
//        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
//
//            String stateKey = stateKey(service);
//            Instant now = Instant.now();
//            long endEpoch = now.toEpochMilli();
//            long startEpoch = now.minusSeconds(60).toEpochMilli();
//
//            List<StreamEntry> streamEntries = jedis.xrange(stateKey, new StreamEntryID(startEpoch), new StreamEntryID(endEpoch));
//
//            List<State> states = new ArrayList<>(streamEntries.size());
//
//            for (StreamEntry streamEntry : streamEntries) {
//                LOG.debugf("Entry %s", streamEntry);
//
//                Map<String, String> fields = streamEntry.getFields();
//                String serviceName = fields.get("service");
//                String message = fields.get("message");
//                String healthy = fields.get("healthy");
//
//                State state = new State(serviceName, Boolean.parseBoolean(healthy), message);
//                states.add(state);
//            }
//
//            return states;
//        } catch (Exception e) {
//            LOG.errorf(e, "Failed to get state for %s: %s", service, e.getMessage());
//        }
        return List.of();
    }

    @Override
    public Map<String, Service> getEntries(ServiceType serviceType) {
        Map<String, Service> entries = new HashMap<>();
        List<Service> services = serviceRepository.listByServiceType(serviceType);

        for (Service service : services) {
            entries.put(service.getName(), service);
        }

        return entries;
    }
}
