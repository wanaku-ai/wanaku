package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.api.types.Namespace;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

public class NamespaceMarshaller implements MessageMarshaller<Namespace> {
    @Override
    public Namespace readFrom(ProtoStreamReader reader) throws IOException {
        Namespace namespace = new Namespace();

        namespace.setId(reader.readString("id"));
        namespace.setName(reader.readString("name"));
        namespace.setPath(reader.readString("path"));

        return namespace;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, Namespace namespace) throws IOException {
        writer.writeString("id", namespace.getId());
        writer.writeString("name", namespace.getName());
        writer.writeString("path", namespace.getPath());
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
