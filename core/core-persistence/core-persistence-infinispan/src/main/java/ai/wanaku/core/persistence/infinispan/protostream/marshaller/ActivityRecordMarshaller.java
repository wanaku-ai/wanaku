package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.capabilities.sdk.api.types.discovery.HealthStatus;
import ai.wanaku.capabilities.sdk.api.types.discovery.ServiceState;

public class ActivityRecordMarshaller implements MessageMarshaller<ActivityRecord> {

    @Override
    public String getTypeName() {
        return ActivityRecord.class.getCanonicalName();
    }

    @Override
    public Class<? extends ActivityRecord> getJavaClass() {
        return ActivityRecord.class;
    }

    @Override
    public ActivityRecord readFrom(ProtoStreamReader reader) throws IOException {
        ActivityRecord record = new ActivityRecord();
        record.setId(reader.readString("id"));

        Long lastSeenMillis = reader.readLong("lastSeen");
        if (lastSeenMillis != null) {
            record.setLastSeen(Instant.ofEpochMilli(lastSeenMillis));
        }

        String healthStatusValue = reader.readString("healthStatus");
        if (healthStatusValue != null) {
            record.setHealthStatus(HealthStatus.fromValue(healthStatusValue));
        } else {
            // Backward compatibility: derive health status from the legacy active boolean
            boolean active = reader.readBoolean("active");
            record.setHealthStatus(active ? HealthStatus.HEALTHY : HealthStatus.DOWN);
        }

        List<ServiceState> states = reader.readCollection("states", new ArrayList<>(), ServiceState.class);
        record.setStates(states);

        return record;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ActivityRecord activityRecord) throws IOException {
        writer.writeString("id", activityRecord.getId());
        if (activityRecord.getLastSeen() != null) {
            writer.writeLong("lastSeen", activityRecord.getLastSeen().toEpochMilli());
        }
        writer.writeBoolean("active", activityRecord.isActive()); // backward compatibility
        writer.writeString("healthStatus", activityRecord.getHealthStatus().asValue());
        writer.writeCollection("states", activityRecord.getStates(), ServiceState.class);
    }
}
