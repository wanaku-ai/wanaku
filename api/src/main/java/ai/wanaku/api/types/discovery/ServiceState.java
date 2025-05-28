package ai.wanaku.api.types.discovery;

import java.time.Instant;
import java.util.Objects;

/**
 * Contains the state of the service for an specific point in time
 */
public class ServiceState {
    private Instant timestamp;
    private boolean healthy;
    private String reason;

    public ServiceState() {
    }

    /**
     * Saves the current state of the service
     * @param timestamp the current timestamp
     * @param healthy whether it is healthy (true for healthy, false otherwise)
     * @param reason Optional state message (ignored if healthy)
     */
    public ServiceState(Instant timestamp, boolean healthy, String reason) {
        this.timestamp = timestamp;
        this.healthy = healthy;
        this.reason = reason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ServiceState that = (ServiceState) o;
        return healthy == that.healthy && Objects.equals(timestamp, that.timestamp) && Objects.equals(reason,
                that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, healthy, reason);
    }

    @Override
    public String toString() {
        return "ServiceState{" +
                "timestamp=" + timestamp +
                ", healthy=" + healthy +
                ", reason='" + reason + '\'' +
                '}';
    }


    public static ServiceState newHealthy() {
        return new ServiceState(Instant.now(), true, StandardMessages.HEALTHY);
    }

    public static ServiceState newUnhealthy(String reason) {
        return new ServiceState(Instant.now(), false, reason);
    }

    public static ServiceState newMissingInAction() {
        return new ServiceState(Instant.now(), false, StandardMessages.MISSING_IN_ACTION);
    }

    public static ServiceState newInactive() {
        return new ServiceState(Instant.now(), true, StandardMessages.AUTO_DEREGISTRATION);
    }
}
