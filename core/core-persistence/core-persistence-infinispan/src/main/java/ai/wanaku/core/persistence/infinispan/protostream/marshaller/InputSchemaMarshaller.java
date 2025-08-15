package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.api.types.InputSchema;
import ai.wanaku.api.types.Property;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import org.infinispan.protostream.MessageMarshaller;

public class InputSchemaMarshaller implements MessageMarshaller<InputSchema> {
    @Override
    public String getTypeName() {
        return InputSchema.class.getCanonicalName();
    }

    @Override
    public Class<? extends InputSchema> getJavaClass() {
        return InputSchema.class;
    }

    @Override
    public InputSchema readFrom(ProtoStreamReader reader) throws IOException {
        InputSchema schema = new InputSchema();
        schema.setType(reader.readString("type"));
        schema.setProperties(reader.readMap("properties", new HashMap<>(), String.class, Property.class));
        schema.setRequired(reader.readCollection("required", new ArrayList<>(), String.class));
        return schema;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, InputSchema schema) throws IOException {
        writer.writeString("type", schema.getType());
        writer.writeMap("properties", schema.getProperties(), String.class, Property.class);
        writer.writeCollection("required", schema.getRequired(), String.class);
    }
}
