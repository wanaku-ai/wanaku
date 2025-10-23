package ai.wanaku.core.persistence.infinispan.protostream.schema;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.PropertyMarshaller;
import org.infinispan.protostream.SerializationContext;

public class PropertySchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "property.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new PropertyMarshaller());
    }
}
