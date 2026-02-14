package ai.wanaku.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.CodeExecutionRequestMarshaller;

public class CodeExecutionRequestSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "code_execution_request.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new CodeExecutionRequestMarshaller());
    }
}
