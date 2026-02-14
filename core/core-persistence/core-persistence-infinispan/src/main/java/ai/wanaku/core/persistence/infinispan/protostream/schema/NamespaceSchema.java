package ai.wanaku.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.NamespaceMarshaller;

public class NamespaceSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "namespace.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new NamespaceMarshaller());
    }
}
