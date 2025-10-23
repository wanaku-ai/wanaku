package ai.wanaku.core.persistence.infinispan.protostream.schema;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.RemoteToolReferenceMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;

public class RemoteToolReferenceSchema extends AbstractWanakuSerializationContextInitializer {

    private final InputSchemaSchema inputSchemaSchema = new InputSchemaSchema();

    @Override
    public String getProtoFileName() {
        return "remote_tool_reference.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        inputSchemaSchema.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getProtoFileName(), this.getProtoFile()));
    }
    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        inputSchemaSchema.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new RemoteToolReferenceMarshaller());
    }
}
