package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.api.types.Property;
import org.infinispan.protostream.MessageMarshaller;
import java.io.IOException;

public class PropertyMarshaller implements MessageMarshaller<Property> {
    @Override
    public String getTypeName() {
        return "ai.wanaku.api.types.Property";
    }

    @Override
    public Class<? extends Property> getJavaClass() {
        return Property.class;
    }

    @Override
    public Property readFrom(ProtoStreamReader reader) throws IOException {
        Property property = new Property();
        property.setType(reader.readString("type"));
        property.setDescription(reader.readString("description"));
        property.setTarget(reader.readString("target"));
        property.setScope(reader.readString("scope"));
        property.setValue(reader.readString("value"));
        return property;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, Property property) throws IOException {
        writer.writeString("type", property.getType());
        writer.writeString("description", property.getDescription());
        writer.writeString("target", property.getTarget());
        writer.writeString("scope", property.getScope());
        writer.writeString("value", property.getValue());
    }
}