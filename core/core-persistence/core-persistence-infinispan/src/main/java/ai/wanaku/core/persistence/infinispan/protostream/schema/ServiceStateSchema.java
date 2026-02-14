package ai.wanaku.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceStateMarshaller;

public class ServiceStateSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "service_state.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new ServiceStateMarshaller());
    }
}
