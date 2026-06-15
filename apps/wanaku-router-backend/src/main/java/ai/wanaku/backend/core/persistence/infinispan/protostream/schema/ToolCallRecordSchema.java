package ai.wanaku.backend.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller.ToolCallRecordMarshaller;

public class ToolCallRecordSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getName() {
        return "tool_call_record.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new ToolCallRecordMarshaller());
    }
}
