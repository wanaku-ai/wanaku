package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.capabilities.sdk.api.types.discovery.ServiceState;
import java.io.IOException;
import java.time.Instant;
import org.infinispan.protostream.MessageMarshaller;

public class ServiceStateMarshaller implements MessageMarshaller<ServiceState> {

    @Override
    public String getTypeName() {
        return ServiceState.class.getCanonicalName();
    }

    @Override
    public Class<? extends ServiceState> getJavaClass() {
        return ServiceState.class;
    }

    @Override
    public ServiceState readFrom(ProtoStreamReader reader) throws IOException {
        long timestampMillis = reader.readLong("timestamp");
        Instant timestamp = Instant.ofEpochMilli(timestampMillis);
        boolean healthy = reader.readBoolean("healthy");
        String reason = reader.readString("reason");
        return new ServiceState(timestamp, healthy, reason);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ServiceState serviceState) throws IOException {
        writer.writeLong("timestamp", serviceState.getTimestamp().toEpochMilli());
        writer.writeBoolean("healthy", serviceState.isHealthy());
        writer.writeString("reason", serviceState.getReason());
    }
}
