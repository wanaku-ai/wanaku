package ai.wanaku.api.types.discovery;

import ai.wanaku.api.types.WanakuEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ActivityRecord implements WanakuEntity<String> {
    private String id;
    private Instant lastSeen;
    private boolean active;
    private List<ServiceState> states = new ArrayList<>();

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<ServiceState> getStates() {
        return states;
    }

    public void setStates(List<ServiceState> states) {
        this.states = states;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ActivityRecord that = (ActivityRecord) o;
        return active == that.active && Objects.equals(id, that.id) && Objects.equals(lastSeen,
                that.lastSeen) && Objects.equals(states, that.states);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, lastSeen, active, states);
    }

    @Override
    public String toString() {
        return "ActivityRecord{" +
                "id='" + id + '\'' +
                ", lastSeen=" + lastSeen +
                ", active=" + active +
                ", states=" + states +
                '}';
    }
}
