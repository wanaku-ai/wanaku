package ai.wanaku.backend.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller.PropertyMarshaller;

public class PropertySchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getName() {
        return "property.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new PropertyMarshaller());
    }
}
