package ai.wanaku.core.persistence.infinispan.protostream.schema;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.CodeExecutionStatusMarshaller;
import org.infinispan.protostream.SerializationContext;

public class CodeExecutionStatusSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "code_execution_status.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new CodeExecutionStatusMarshaller());
    }
}
