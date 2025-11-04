package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.api.types.EmbeddedResource;
import ai.wanaku.api.types.ResourceReference;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

/**
 * Protostream marshaller for EmbeddedResource.
 */
public class EmbeddedResourceMarshaller implements MessageMarshaller<EmbeddedResource> {

    @Override
    public String getTypeName() {
        return EmbeddedResource.class.getCanonicalName();
    }

    @Override
    public Class<? extends EmbeddedResource> getJavaClass() {
        return EmbeddedResource.class;
    }

    @Override
    public EmbeddedResource readFrom(ProtoStreamReader reader) throws IOException {
        EmbeddedResource content = new EmbeddedResource();
        content.setResource(reader.readObject("resource", ResourceReference.class));
        return content;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, EmbeddedResource content) throws IOException {
        writer.writeObject("resource", content.getResource(), ResourceReference.class);
    }
}
