package ai.wanaku.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ParamMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ResourceReferenceMarshaller;

public class ResourceReferenceSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "resource_reference.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new ParamMarshaller());
        serCtx.registerMarshaller(new ResourceReferenceMarshaller());
    }
}
