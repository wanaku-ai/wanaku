package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import java.util.HashMap;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.capabilities.sdk.api.types.Namespace;

public class NamespaceMarshaller implements MessageMarshaller<Namespace> {
    @Override
    public Namespace readFrom(ProtoStreamReader reader) throws IOException {
        Namespace namespace = new Namespace();

        namespace.setId(reader.readString("id"));
        namespace.setName(reader.readString("name"));
        namespace.setPath(reader.readString("path"));
        namespace.setLabels(reader.readMap("labels", new HashMap<>(), String.class, String.class));

        return namespace;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, Namespace namespace) throws IOException {
        writer.writeString("id", namespace.getId());
        writer.writeString("name", namespace.getName());
        writer.writeString("path", namespace.getPath());
        writer.writeMap("labels", namespace.getLabels(), String.class, String.class);
    }

    @Override
    public Class<? extends Namespace> getJavaClass() {
        return Namespace.class;
    }

    @Override
    public String getTypeName() {
        return Namespace.class.getCanonicalName();
    }
}
