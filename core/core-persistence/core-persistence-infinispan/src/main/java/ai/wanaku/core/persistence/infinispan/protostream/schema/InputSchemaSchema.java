package ai.wanaku.core.persistence.infinispan.protostream.schema;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.InputSchemaMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;

public class InputSchemaSchema extends AbstractWanakuSerializationContextInitializer {

    private final PropertySchema propertySchema = new PropertySchema();

    @Override
    public String getProtoFileName() {
        return "input_schema.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        propertySchema.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getProtoFileName(), this.getProtoFile()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        propertySchema.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new InputSchemaMarshaller());
    }
}
