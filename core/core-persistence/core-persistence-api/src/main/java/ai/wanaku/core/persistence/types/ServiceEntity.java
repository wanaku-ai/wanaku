package ai.wanaku.core.persistence.types;

import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.providers.ServiceType;

import java.util.List;

public class ServiceEntity implements IdEntity {

    private String name;
    private ServiceType serviceType;
    private String targetAddress;
    private List<ConfigurationEntity> configurationEntities;

    @Override
    public String getId() {
        return getName();
    }

    @Override
    public void setId(String id) {
        setName(id);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public String getTargetAddress() {
        return targetAddress;
    }

    public void setTargetAddress(String targetAddress) {
        this.targetAddress = targetAddress;
    }

    public List<ConfigurationEntity> getConfigurationEntities() {
        return configurationEntities;
    }

    public void setConfigurationEntities(List<ConfigurationEntity> configurationEntities) {
        this.configurationEntities = configurationEntities;
    }
}
