package ai.wanaku.core.persistence.infinispan.remote.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.ToolReferenceMarshaller;

public class ToolReferenceSchema extends AbstractWanakuSerializationContextInitializer
        implements SerializationContextInitializer {

    private final InputSchemaSchema inputSchema = new InputSchemaSchema();

    @Override
    public String getName() {
        return "tool_reference.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        inputSchema.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getName(), this.getContent()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        inputSchema.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new ToolReferenceMarshaller());
    }
}
