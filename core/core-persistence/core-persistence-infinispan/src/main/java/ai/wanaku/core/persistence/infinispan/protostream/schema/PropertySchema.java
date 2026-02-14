package ai.wanaku.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.PropertyMarshaller;

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
