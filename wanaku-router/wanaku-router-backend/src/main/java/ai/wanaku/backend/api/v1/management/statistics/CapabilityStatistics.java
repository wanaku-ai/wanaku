package ai.wanaku.backend.api.v1.management.statistics;

public class CapabilityStatistics {
    private long total;
    private long healthy;
    private long unhealthy;
    private long down;
    private long pending;

    public CapabilityStatistics() {}

    public CapabilityStatistics(long total, long healthy, long unhealthy, long down, long pending) {
        this.total = total;
        this.healthy = healthy;
        this.unhealthy = unhealthy;
        this.down = down;
        this.pending = pending;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getHealthy() {
        return healthy;
    }

    public void setHealthy(long healthy) {
        this.healthy = healthy;
    }

    public long getUnhealthy() {
        return unhealthy;
    }

    public void setUnhealthy(long unhealthy) {
        this.unhealthy = unhealthy;
    }

    public long getDown() {
        return down;
    }

    public void setDown(long down) {
        this.down = down;
    }

    public long getPending() {
        return pending;
    }

    public void setPending(long pending) {
        this.pending = pending;
    }
}
