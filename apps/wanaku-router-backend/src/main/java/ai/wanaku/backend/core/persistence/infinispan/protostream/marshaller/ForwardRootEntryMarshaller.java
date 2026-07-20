package ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.backend.bridge.ForwardRootEntry;

public class ForwardRootEntryMarshaller implements MessageMarshaller<ForwardRootEntry> {
    @Override
    public String getTypeName() {
        return "ai.wanaku.backend.bridge.ForwardRootEntry";
    }

    @Override
    public Class<? extends ForwardRootEntry> getJavaClass() {
        return ForwardRootEntry.class;
    }

    @Override
    public ForwardRootEntry readFrom(ProtoStreamReader reader) throws IOException {
        ForwardRootEntry entry = new ForwardRootEntry();
        entry.setUri(reader.readString("uri"));
        entry.setName(reader.readString("name"));
        return entry;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ForwardRootEntry entry) throws IOException {
        writer.writeString("uri", entry.getUri());
        writer.writeString("name", entry.getName());
    }
}
