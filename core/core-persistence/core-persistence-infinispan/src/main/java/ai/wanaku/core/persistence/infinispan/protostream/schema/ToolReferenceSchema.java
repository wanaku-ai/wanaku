package ai.wanaku.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ToolReferenceMarshaller;

public class ToolReferenceSchema extends AbstractWanakuSerializationContextInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ToolReferenceSchema.class);

    public ToolReferenceSchema() {
        logger.info("ToolReferenceSchema instantiated!");
    }

    private final InputSchemaSchema inputSchema = new InputSchemaSchema();

    @Override
    public String getProtoFileName() {
        return "tool_reference.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        inputSchema.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getProtoFileName(), this.getProtoFile()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        inputSchema.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new ToolReferenceMarshaller());
    }
}
