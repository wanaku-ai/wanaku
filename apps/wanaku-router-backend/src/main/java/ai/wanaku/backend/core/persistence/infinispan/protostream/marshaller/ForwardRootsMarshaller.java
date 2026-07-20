package ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import java.util.HashMap;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.backend.bridge.ForwardRoots;

public class ForwardRootsMarshaller implements MessageMarshaller<ForwardRoots> {

    @Override
    public ForwardRoots readFrom(ProtoStreamReader reader) throws IOException {
        ForwardRoots forwardRoots = new ForwardRoots();
        forwardRoots.setForwardName(reader.readString("forwardName"));
        forwardRoots.setRoots(reader.readMap("roots", new HashMap<>(), String.class, String.class));
        return forwardRoots;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ForwardRoots forwardRoots) throws IOException {
        writer.writeString("forwardName", forwardRoots.getForwardName());
        writer.writeMap("roots", forwardRoots.getRoots(), String.class, String.class);
    }

    @Override
    public Class<? extends ForwardRoots> getJavaClass() {
        return ForwardRoots.class;
    }

    @Override
    public String getTypeName() {
        return ForwardRoots.class.getCanonicalName();
    }
}
