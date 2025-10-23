package ai.wanaku.core.persistence.infinispan.protostream.schema;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceTypeMarshaller;
import org.infinispan.protostream.SerializationContext;

public class ServiceTypeSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "service_type.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new ServiceTypeMarshaller());
    }
}
