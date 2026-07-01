package ai.wanaku.core.persistence.infinispan.remote.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.InputSchemaMarshaller;

public class InputSchemaSchema extends AbstractWanakuSerializationContextInitializer
        implements SerializationContextInitializer {

    private final PropertySchema propertySchema = new PropertySchema();

    @Override
    public String getName() {
        return "input_schema.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        propertySchema.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getName(), this.getContent()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        propertySchema.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new InputSchemaMarshaller());
    }
}
