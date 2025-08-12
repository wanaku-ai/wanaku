package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.api.types.ResourceReference;
import java.io.IOException;
import java.util.ArrayList;
import org.infinispan.protostream.MessageMarshaller;

public class ResourceReferenceMarshaller implements MessageMarshaller<ResourceReference> {
    @Override
    public String getTypeName() {
        return ResourceReference.class.getCanonicalName();
    }

    @Override
    public Class<? extends ResourceReference> getJavaClass() {
        return ResourceReference.class;
    }

    @Override
    public ResourceReference readFrom(ProtoStreamReader reader) throws IOException {
        ResourceReference ref = new ResourceReference();
        ref.setId(reader.readString("id"));
        ref.setLocation(reader.readString("location"));
        ref.setType(reader.readString("type"));
        ref.setName(reader.readString("name"));
        ref.setDescription(reader.readString("description"));
        ref.setMimeType(reader.readString("mime_type"));
        ref.setParams(reader.readCollection("params", new ArrayList<>(), ResourceReference.Param.class));
        ref.setConfigurationURI(reader.readString("configuration_uri"));
        ref.setSecretsURI(reader.readString("secrets_uri"));
        ref.setNamespace(reader.readString("namespace"));
        return ref;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ResourceReference ref) throws IOException {
        writer.writeString("id", ref.getId());
        writer.writeString("location", ref.getLocation());
        writer.writeString("type", ref.getType());
        writer.writeString("name", ref.getName());
        writer.writeString("description", ref.getDescription());
        writer.writeString("mime_type", ref.getMimeType());
        writer.writeCollection("params", ref.getParams(), ResourceReference.Param.class);
        writer.writeString("configuration_uri", ref.getConfigurationURI());
        writer.writeString("secrets_uri", ref.getSecretsURI());
        writer.writeString("namespace", ref.getNamespace());
    }
}
