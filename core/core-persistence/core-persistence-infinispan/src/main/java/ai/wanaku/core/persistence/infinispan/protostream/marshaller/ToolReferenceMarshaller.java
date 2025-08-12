package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.api.types.InputSchema;
import ai.wanaku.api.types.ToolReference;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

public class ToolReferenceMarshaller implements MessageMarshaller<ToolReference> {

    @Override
    public String getTypeName() {
        return ToolReference.class.getCanonicalName();
    }

    @Override
    public Class<? extends ToolReference> getJavaClass() {
        return ToolReference.class;
    }

    @Override
    public ToolReference readFrom(ProtoStreamReader reader) throws IOException {
        ToolReference ref = new ToolReference();
        ref.setId(reader.readString("id"));
        ref.setName(reader.readString("name"));
        ref.setDescription(reader.readString("description"));
        ref.setUri(reader.readString("uri"));
        ref.setType(reader.readString("type"));
        ref.setInputSchema(reader.readObject("input_schema", InputSchema.class));
        ref.setConfigurationURI(reader.readString("configuration_uri"));
        ref.setSecretsURI(reader.readString("secrets_uri"));
        ref.setNamespace(reader.readString("namespace"));
        return ref;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ToolReference ref) throws IOException {
        writer.writeString("id", ref.getId());
        writer.writeString("name", ref.getName());
        writer.writeString("description", ref.getDescription());
        writer.writeString("uri", ref.getUri());
        writer.writeString("type", ref.getType());
        writer.writeObject("input_schema", ref.getInputSchema(), InputSchema.class);
        writer.writeString("configuration_uri", ref.getConfigurationURI());
        writer.writeString("secrets_uri", ref.getSecretsURI());
        writer.writeString("namespace", ref.getNamespace());
    }
}
