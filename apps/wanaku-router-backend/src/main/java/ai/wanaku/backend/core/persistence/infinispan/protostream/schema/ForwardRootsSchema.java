package ai.wanaku.backend.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller.ForwardRootsMarshaller;

public class ForwardRootsSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getName() {
        return "forward_roots.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new ForwardRootsMarshaller());
    }
}
