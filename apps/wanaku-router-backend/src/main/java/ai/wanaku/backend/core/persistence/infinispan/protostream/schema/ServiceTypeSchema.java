package ai.wanaku.backend.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller.ServiceTypeMarshaller;

public class ServiceTypeSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getName() {
        return "service_type.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new ServiceTypeMarshaller());
    }
}
