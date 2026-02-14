package ai.wanaku.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceTypeMarshaller;

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
