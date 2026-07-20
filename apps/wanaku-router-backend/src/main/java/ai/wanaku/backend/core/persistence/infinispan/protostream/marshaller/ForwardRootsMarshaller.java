package ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.backend.bridge.ForwardRootEntry;
import ai.wanaku.backend.bridge.ForwardRoots;

public class ForwardRootsMarshaller implements MessageMarshaller<ForwardRoots> {
    @Override
    public String getTypeName() {
        return "ai.wanaku.backend.bridge.ForwardRoots";
    }

    @Override
    public Class<? extends ForwardRoots> getJavaClass() {
        return ForwardRoots.class;
    }

    @Override
    public ForwardRoots readFrom(ProtoStreamReader reader) throws IOException {
        ForwardRoots roots = new ForwardRoots();
        roots.setId(reader.readString("id"));
        roots.setForwardName(reader.readString("forwardName"));
        List<ForwardRootEntry> entries = reader.readCollection("roots", new ArrayList<>(), ForwardRootEntry.class);
        roots.setRoots(entries);
        return roots;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ForwardRoots roots) throws IOException {
        writer.writeString("id", roots.getId());
        writer.writeString("forwardName", roots.getForwardName());
        writer.writeCollection("roots", roots.getRoots(), ForwardRootEntry.class);
    }
}
