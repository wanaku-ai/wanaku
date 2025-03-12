package ai.wanaku.core.service.discovery;

import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.ServiceEntity;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.ObjectInputFilter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MongoRegistry implements ServiceRegistry {
    private static final Logger LOG = Logger.getLogger(MongoRegistry.class);

    @Inject
    private ServiceRepository serviceRepository;

    @Override
    public void register(ServiceTarget serviceTarget, Map<String, String> configurations) {
        ServiceEntity serviceEntity = serviceRepository.findById(serviceTarget.getService());
        if (serviceEntity == null) {
            serviceEntity = new ServiceEntity();

            serviceEntity.setName(serviceTarget.getService());
            serviceEntity.setTargetAddress(serviceTarget.toAddress());
            serviceEntity.setServiceType(serviceTarget.getServiceType());
            Map<String, Configuration> configurationMap = new HashMap<>();
            for (Map.Entry<String, String> entry : configurations.entrySet()) {
                Configuration configuration = new Configuration();
                configuration.setDescription(entry.getValue());
                configurationMap.put(entry.getKey(), configuration);
            }
            serviceEntity.setConfigurations(configurationMap);

            serviceRepository.persistOrUpdate(serviceEntity);
        } else if (!serviceTarget.toAddress().equals(serviceEntity.getTargetAddress())) {
            serviceEntity.setTargetAddress(serviceTarget.toAddress());
            serviceRepository.update(serviceEntity);
        }
    }

    @Override
    public void deregister(String service) {
        serviceRepository.deleteById(service);
    }

    @Override
    public Service getService(String service) {
        return toService(serviceRepository.findById(service));
    }

    @Override
    public Map<String, Service> getEntries(ServiceType serviceType) {
        Map<String, Service> entries = new HashMap<>();
        List<ServiceEntity> serviceEntityList = serviceRepository.listByServiceType(serviceType);

        for (ServiceEntity serviceEntity : serviceEntityList) {
            Service service = toService(serviceEntity);

            entries.put(serviceEntity.getName(), service);
        }

        return entries;
    }

    private Service toService(ServiceEntity serviceEntity) {
        Service service = new Service();

        Configurations configurations = new Configurations();
        configurations.setConfigurations(serviceEntity.getConfigurations());
        service.setConfigurations(configurations);
        service.setTarget(serviceEntity.getTargetAddress());

        return service;
    }
}
