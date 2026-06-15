package ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.backend.common.ToolCallRecord;

public class ToolCallRecordMarshaller implements MessageMarshaller<ToolCallRecord> {

    @Override
    public String getTypeName() {
        return ToolCallRecord.class.getCanonicalName();
    }

    @Override
    public Class<? extends ToolCallRecord> getJavaClass() {
        return ToolCallRecord.class;
    }

    @Override
    public ToolCallRecord readFrom(ProtoStreamReader reader) throws IOException {
        ToolCallRecord record = new ToolCallRecord();
        record.setId(reader.readString("id"));
        record.setEventId(reader.readString("eventId"));
        record.setEventType(reader.readString("eventType"));
        Long ts = reader.readLong("timestamp");
        record.setTimestamp(ts != null ? ts : 0);
        record.setToolName(reader.readString("toolName"));
        record.setToolType(reader.readString("toolType"));
        record.setConnectionId(reader.readString("connectionId"));
        record.setServiceId(reader.readString("serviceId"));
        record.setServiceAddress(reader.readString("serviceAddress"));
        record.setError(reader.readBoolean("error"));
        Long dur = reader.readLong("duration");
        record.setDuration(dur != null ? dur : 0);
        record.setErrorCategory(reader.readString("errorCategory"));
        record.setErrorMessage(reader.readString("errorMessage"));
        record.setArgumentKeys(reader.readString("argumentKeys"));
        return record;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ToolCallRecord record) throws IOException {
        writer.writeString("id", record.getId());
        writer.writeString("eventId", record.getEventId());
        writer.writeString("eventType", record.getEventType());
        writer.writeLong("timestamp", record.getTimestamp());
        writer.writeString("toolName", record.getToolName());
        writer.writeString("toolType", record.getToolType());
        writer.writeString("connectionId", record.getConnectionId());
        writer.writeString("serviceId", record.getServiceId());
        writer.writeString("serviceAddress", record.getServiceAddress());
        writer.writeBoolean("error", record.isError());
        writer.writeLong("duration", record.getDuration());
        writer.writeString("errorCategory", record.getErrorCategory());
        writer.writeString("errorMessage", record.getErrorMessage());
        writer.writeString("argumentKeys", record.getArgumentKeys());
    }
}
