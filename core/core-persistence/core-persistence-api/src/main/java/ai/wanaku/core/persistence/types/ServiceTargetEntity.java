package ai.wanaku.core.persistence.types;

import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;

import java.util.List;
import java.util.Map;

public class ServiceTargetEntity extends ServiceTarget implements WanakuEntity {

    private Map<String, Configuration> configurations;

    public ServiceTargetEntity(String service, String host, int port, ServiceType serviceType) {
        super(service, host, port, serviceType);
    }

    // Constructor needed by Jackson
    public ServiceTargetEntity() {
        super(null, null, 0, null);
    }

    @Override
    public String getId() {
        return getService();
    }

    @Override
    public void setId(String id) {
        // Do nothing, the Id is mapped via service
    }

    public Map<String, Configuration> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Map<String, Configuration> configurations) {
        this.configurations = configurations;
    }
}
