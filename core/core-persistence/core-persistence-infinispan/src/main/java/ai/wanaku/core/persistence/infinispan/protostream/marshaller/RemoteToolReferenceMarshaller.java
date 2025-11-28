package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.RemoteToolReference;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

public class RemoteToolReferenceMarshaller implements MessageMarshaller<RemoteToolReference> {
    @Override
    public String getTypeName() {
        return RemoteToolReference.class.getCanonicalName();
    }

    @Override
    public Class<? extends RemoteToolReference> getJavaClass() {
        return RemoteToolReference.class;
    }

    @Override
    public RemoteToolReference readFrom(ProtoStreamReader reader) throws IOException {
        RemoteToolReference ref = new RemoteToolReference();
        ref.setId(reader.readString("id"));
        ref.setName(reader.readString("name"));
        ref.setDescription(reader.readString("description"));
        ref.setType(reader.readString("type"));
        ref.setInputSchema(reader.readObject("input_schema", InputSchema.class));
        return ref;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, RemoteToolReference ref) throws IOException {
        writer.writeString("id", ref.getId());
        writer.writeString("name", ref.getName());
        writer.writeString("description", ref.getDescription());
        writer.writeString("type", ref.getType());
        writer.writeObject("input_schema", ref.getInputSchema(), InputSchema.class);
    }
}
