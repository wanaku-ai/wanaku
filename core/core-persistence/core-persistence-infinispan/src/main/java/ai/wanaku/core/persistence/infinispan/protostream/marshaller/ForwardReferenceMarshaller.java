package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

public class ForwardReferenceMarshaller implements MessageMarshaller<ForwardReference> {
    @Override
    public String getTypeName() {
        return ForwardReference.class.getCanonicalName();
    }

    @Override
    public Class<? extends ForwardReference> getJavaClass() {
        return ForwardReference.class;
    }

    @Override
    public ForwardReference readFrom(ProtoStreamReader reader) throws IOException {
        ForwardReference ref = new ForwardReference();
        ref.setId(reader.readString("id"));
        ref.setName(reader.readString("name"));
        ref.setAddress(reader.readString("address"));
        ref.setNamespace(reader.readString("namespace"));
        return ref;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ForwardReference ref) throws IOException {
        writer.writeString("id", ref.getId());
        writer.writeString("name", ref.getName());
        writer.writeString("address", ref.getAddress());
        writer.writeString("namespace", ref.getNamespace());
    }
}
