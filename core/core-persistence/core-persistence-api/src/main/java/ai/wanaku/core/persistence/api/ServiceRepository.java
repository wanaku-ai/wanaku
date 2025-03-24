package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.persistence.types.ConfigurationEntity;
import ai.wanaku.core.persistence.types.ServiceTargetEntity;

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
public interface ServiceRepository extends WanakuRepository<ServiceTarget, ServiceTargetEntity, String> {

    /**
     * Lists all services of a specific service type.
     *
     * @param serviceType the type of service to list
     * @return a list of services matching the specified type
     */
    List<ServiceTarget> listByServiceType(ServiceType serviceType);

    /**
     * Finds a service by its ID and service type.
     *
     * @param id the ID of the service to find
     * @param serviceType the type of the service to find
     * @return the matching service, or null if not found
     */
    ServiceTarget findByIdAndServiceType(String id, ServiceType serviceType);

    /**
     * Updates an existing service.
     *
     * @param entity the service with updated information
     * @return the updated service
     */
    ServiceTarget update(ServiceTarget entity);

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
    default ServiceTarget convertToModel(ServiceTargetEntity entity) {
        return new ServiceTarget(entity.getService(), entity.getHost(), entity.getPort(), entity.getServiceType());
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
    default ServiceTargetEntity convertToEntity(ServiceTarget model) {
        return new ServiceTargetEntity(model.getService(), model.getHost(), model.getPort(), model.getServiceType());
    }
}
