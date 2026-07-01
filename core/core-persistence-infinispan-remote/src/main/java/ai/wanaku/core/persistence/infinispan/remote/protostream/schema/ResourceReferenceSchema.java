package ai.wanaku.core.persistence.infinispan.remote.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.ParamMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.ResourceReferenceMarshaller;

public class ResourceReferenceSchema extends AbstractWanakuSerializationContextInitializer
        implements SerializationContextInitializer {

    @Override
    public String getName() {
        return "resource_reference.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new ParamMarshaller());
        serCtx.registerMarshaller(new ResourceReferenceMarshaller());
    }
}
