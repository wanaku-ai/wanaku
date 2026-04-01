package ai.wanaku.backend.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller.ForwardReferenceMarshaller;

public class ForwardReferenceSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getName() {
        return "forward_reference.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getName(), this.getContent()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new ForwardReferenceMarshaller());
    }
}
