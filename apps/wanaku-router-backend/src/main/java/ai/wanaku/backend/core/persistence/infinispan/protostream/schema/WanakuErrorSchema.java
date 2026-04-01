package ai.wanaku.backend.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller.WanakuErrorMarshaller;

public class WanakuErrorSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getName() {
        return "wanaku_error.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new WanakuErrorMarshaller());
    }
}
