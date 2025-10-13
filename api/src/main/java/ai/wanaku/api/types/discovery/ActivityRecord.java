package ai.wanaku.api.types.discovery;

import ai.wanaku.api.types.WanakuEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Records the activity and health status of a service in the Wanaku system.
 * <p>
 * This entity tracks service lifecycle information including when the service was last
 * observed, its current active status, and a history of state transitions. Activity records
 * are used by the service discovery mechanism to monitor service health and availability.
 */
public class ActivityRecord implements WanakuEntity<String> {
    private String id;
    private Instant lastSeen;
    private boolean active;
    private List<ServiceState> states = new ArrayList<>();

    /**
     * Default constructor for ActivityRecord.
     */
    public ActivityRecord() {}

    /**
     * Gets the unique identifier for this activity record.
     *
     * @return the activity record identifier
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this activity record.
     *
     * @param id the activity record identifier to set
     */
    @Override
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the timestamp when this service was last observed as active.
     *
     * @return the last seen timestamp, or {@code null} if never observed
     */
    public Instant getLastSeen() {
        return lastSeen;
    }

    /**
     * Sets the timestamp when this service was last observed as active.
     *
     * @param lastSeen the last seen timestamp to set
     */
    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    /**
     * Checks whether the service is currently active.
     *
     * @return {@code true} if the service is active, {@code false} otherwise
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets the active status of the service.
     *
     * @param active {@code true} to mark the service as active, {@code false} otherwise
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Gets the list of state transitions for this service.
     * <p>
     * The states list maintains a history of service state changes, allowing
     * tracking of service health and availability over time.
     *
     * @return the list of service states
     */
    public List<ServiceState> getStates() {
        return states;
    }

    /**
     * Sets the list of state transitions for this service.
     *
     * @param states the list of service states to set
     */
    public void setStates(List<ServiceState> states) {
        this.states = states;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ActivityRecord that = (ActivityRecord) o;
        return active == that.active
                && Objects.equals(id, that.id)
                && Objects.equals(lastSeen, that.lastSeen)
                && Objects.equals(states, that.states);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, lastSeen, active, states);
    }

    @Override
    public String toString() {
        return "ActivityRecord{" + "id='"
                + id + '\'' + ", lastSeen="
                + lastSeen + ", active="
                + active + ", states="
                + states + '}';
    }
}
