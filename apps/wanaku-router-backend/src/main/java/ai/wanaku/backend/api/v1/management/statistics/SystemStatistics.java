package ai.wanaku.backend.api.v1.management.statistics;

public class SystemStatistics {
    private long toolsCount;
    private long resourcesCount;
    private long promptsCount;
    private long forwardsCount;
    private long dataStoresCount;
    private CapabilityStatistics toolCapabilities;
    private CapabilityStatistics resourceCapabilities;

    public SystemStatistics() {}

    public SystemStatistics(
            long toolsCount,
            long resourcesCount,
            long promptsCount,
            long forwardsCount,
            long dataStoresCount,
            CapabilityStatistics toolCapabilities,
            CapabilityStatistics resourceCapabilities) {
        this.toolsCount = toolsCount;
        this.resourcesCount = resourcesCount;
        this.promptsCount = promptsCount;
        this.forwardsCount = forwardsCount;
        this.dataStoresCount = dataStoresCount;
        this.toolCapabilities = toolCapabilities;
        this.resourceCapabilities = resourceCapabilities;
    }

    public long getToolsCount() {
        return toolsCount;
    }

    public void setToolsCount(long toolsCount) {
        this.toolsCount = toolsCount;
    }

    public long getResourcesCount() {
        return resourcesCount;
    }

    public void setResourcesCount(long resourcesCount) {
        this.resourcesCount = resourcesCount;
    }

    public long getPromptsCount() {
        return promptsCount;
    }

    public void setPromptsCount(long promptsCount) {
        this.promptsCount = promptsCount;
    }

    public long getForwardsCount() {
        return forwardsCount;
    }

    public void setForwardsCount(long forwardsCount) {
        this.forwardsCount = forwardsCount;
    }

    public long getDataStoresCount() {
        return dataStoresCount;
    }

    public void setDataStoresCount(long dataStoresCount) {
        this.dataStoresCount = dataStoresCount;
    }

    public CapabilityStatistics getToolCapabilities() {
        return toolCapabilities;
    }

    public void setToolCapabilities(CapabilityStatistics toolCapabilities) {
        this.toolCapabilities = toolCapabilities;
    }

    public CapabilityStatistics getResourceCapabilities() {
        return resourceCapabilities;
    }

    public void setResourceCapabilities(CapabilityStatistics resourceCapabilities) {
        this.resourceCapabilities = resourceCapabilities;
    }
}
