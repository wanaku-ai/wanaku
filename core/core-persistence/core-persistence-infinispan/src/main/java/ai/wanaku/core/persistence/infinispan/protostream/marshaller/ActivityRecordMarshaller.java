package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.api.types.discovery.ServiceState;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

        record.setActive(reader.readBoolean("active"));

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
        writer.writeBoolean("active", activityRecord.isActive());
        writer.writeCollection("states", activityRecord.getStates(), ServiceState.class);
    }
}
