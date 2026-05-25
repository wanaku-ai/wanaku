package ai.wanaku.core.persistence.infinispan.remote.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.NamespaceMarshaller;

public class NamespaceSchema extends AbstractWanakuSerializationContextInitializer
        implements SerializationContextInitializer {

    @Override
    public String getName() {
        return "namespace.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new NamespaceMarshaller());
    }
}
