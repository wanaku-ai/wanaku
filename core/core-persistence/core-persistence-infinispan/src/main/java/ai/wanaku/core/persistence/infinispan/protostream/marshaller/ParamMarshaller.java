package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.api.types.ResourceReference;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;

public class ParamMarshaller implements MessageMarshaller<ResourceReference.Param> {
    @Override
    public String getTypeName() {
        return ResourceReference.Param.class.getCanonicalName();
    }

    @Override
    public Class<? extends ResourceReference.Param> getJavaClass() {
        return ResourceReference.Param.class;
    }

    @Override
    public ResourceReference.Param readFrom(ProtoStreamReader reader) throws IOException {
        ResourceReference.Param ref = new ResourceReference.Param();
        ref.setName(reader.readString("name"));
        ref.setValue(reader.readString("value"));
        return ref;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ResourceReference.Param ref) throws IOException {
        writer.writeString("name", ref.getName());
        writer.writeString("value", ref.getValue());
    }
}
