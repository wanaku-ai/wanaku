package ai.wanaku.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.CodeExecutionTaskMarshaller;

public class CodeExecutionTaskSchema extends AbstractWanakuSerializationContextInitializer {

    private final CodeExecutionStatusSchema statusSchema = new CodeExecutionStatusSchema();
    private final CodeExecutionRequestSchema requestSchema = new CodeExecutionRequestSchema();

    @Override
    public String getProtoFileName() {
        return "code_execution_task.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        statusSchema.registerSchema(serCtx);
        requestSchema.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getProtoFileName(), this.getProtoFile()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        statusSchema.registerMarshallers(serCtx);
        requestSchema.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new CodeExecutionTaskMarshaller());
    }
}
