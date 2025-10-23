package ai.wanaku.core.persistence.infinispan.protostream.schema;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.WanakuErrorMarshaller;
import org.infinispan.protostream.SerializationContext;

public class WanakuErrorSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "wanaku_error.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new WanakuErrorMarshaller());
    }
}
