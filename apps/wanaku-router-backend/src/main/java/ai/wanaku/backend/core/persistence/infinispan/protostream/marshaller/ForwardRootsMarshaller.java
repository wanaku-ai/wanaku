package ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.backend.bridge.ForwardRoots;

public class ForwardRootsMarshaller implements MessageMarshaller<ForwardRoots> {
    @Override
    public String getTypeName() {
        return ForwardRoots.class.getCanonicalName();
    }

    @Override
    public Class<? extends ForwardRoots> getJavaClass() {
        return ForwardRoots.class;
    }

    @Override
    public ForwardRoots readFrom(ProtoStreamReader reader) throws IOException {
        ForwardRoots roots = new ForwardRoots();
        roots.setName(reader.readString("name"));
        List<String> rootUris = reader.readCollection("rootUris", new ArrayList<>(), String.class);
        roots.setRootUris(rootUris != null ? rootUris : new ArrayList<>());
        return roots;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ForwardRoots roots) throws IOException {
        writer.writeString("name", roots.getName());
        writer.writeCollection("rootUris", roots.getRootUris(), String.class);
    }
}
