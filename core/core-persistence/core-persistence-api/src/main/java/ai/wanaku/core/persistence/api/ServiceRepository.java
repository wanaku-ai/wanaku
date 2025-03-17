package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.persistence.types.ConfigurationEntity;
import ai.wanaku.core.persistence.types.ServiceEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Repository interface for managing Service entities.
 *
 * <p>This interface extends WanakuRepository to provide specific operations
 * for Service objects, including querying by service type and updating services.</p>
 */
public interface ServiceRepository extends WanakuRepository<Service, ServiceEntity, String> {

    /**
     * Lists all services of a specific service type.
     *
     * @param serviceType the type of service to list
     * @return a list of services matching the specified type
     */
    List<Service> listByServiceType(ServiceType serviceType);

    /**
     * Finds a service by its ID and service type.
     *
     * @param id the ID of the service to find
     * @param serviceType the type of the service to find
     * @return the matching service, or null if not found
     */
    Service findByIdAndServiceType(String id, ServiceType serviceType);

    /**
     * Updates an existing service.
     *
     * @param entity the service with updated information
     * @return the updated service
     */
    Service update(Service entity);

    /**
     * Converts a ServiceEntity to its corresponding Service model.
     *
     * <p>This method maps all properties from the entity to the model, including
     * converting the configuration entities to a configuration map.</p>
     *
     * @param entity the ServiceEntity to convert
     * @return the converted Service model
     */
    @Override
    default Service convertToModel(ServiceEntity entity) {
        Service service = new Service();
        service.setTarget(entity.getTargetAddress());
        service.setName(entity.getName());
        service.setServiceType(entity.getServiceType().asValue());
        Map<String, Configuration> configurationMap = new HashMap<>();
        for (ConfigurationEntity configurationEntity : entity.getConfigurationEntities()) {
            configurationMap.put(configurationEntity.getKey(), configurationEntity.getConfiguration());
        }
        service.setConfigurations(new Configurations());
        service.getConfigurations().setConfigurations(configurationMap);

        return service;
    }

    /**
     * Converts a Service model to its corresponding ServiceEntity.
     *
     * <p>This method maps all properties from the model to the entity, including
     * converting the configuration map to configuration entities.</p>
     *
     * @param model the Service model to convert
     * @return the converted ServiceEntity
     */
    @Override
    default ServiceEntity convertToEntity(Service model) {
        ServiceEntity entity = new ServiceEntity();
        entity.setName(model.getName());
        entity.setId(model.getName());
        entity.setTargetAddress(model.getTarget());
        entity.setServiceType(ServiceType.fromValue(model.getServiceType()));
        entity.setConfigurationEntities(
                model.getConfigurations().getConfigurations().entrySet().stream()
                        .map(configuration -> new ConfigurationEntity(configuration.getKey(), configuration.getValue()))
                        .collect(Collectors.toList())
        );

        return entity;
    }
}
