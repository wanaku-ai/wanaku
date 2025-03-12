package ai.wanaku.core.mcp;

import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.core.mcp.providers.ServiceType;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Map;

@MongoEntity(collection = ServiceEntity.COLLECTION_NAME)
public class ServiceEntity {
    public final static String COLLECTION_NAME = "services";

    @BsonId
    private String name;
    private String targetAddress;
    private ServiceType serviceType;
    private Map<String, Configuration> configurations;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTargetAddress() {
        return targetAddress;
    }

    public void setTargetAddress(String targetAddress) {
        this.targetAddress = targetAddress;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public Map<String, Configuration> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Map<String, Configuration> configurations) {
        this.configurations = configurations;
    }
}
