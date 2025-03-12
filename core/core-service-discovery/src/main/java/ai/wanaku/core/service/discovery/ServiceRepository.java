package ai.wanaku.core.service.discovery;

import ai.wanaku.core.mcp.ServiceEntity;
import ai.wanaku.core.mcp.providers.ServiceType;
import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ServiceRepository implements PanacheMongoRepositoryBase<ServiceEntity, String> {

    public List<ServiceEntity> listByServiceType(ServiceType serviceType) {
        return list("serviceType", serviceType);
    }

    public ServiceEntity findByIdAndServiceType(String id, ServiceType serviceType) {
        return find("_id = ?1 and serviceType = ?2", id, serviceType).firstResult();
    }
}
