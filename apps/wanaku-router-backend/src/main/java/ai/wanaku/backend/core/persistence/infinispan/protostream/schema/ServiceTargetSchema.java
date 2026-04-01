package ai.wanaku.backend.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller.ServiceTargetMarshaller;

public class ServiceTargetSchema extends AbstractWanakuSerializationContextInitializer {

    private final ServiceTypeSchema serviceTypeSchema = new ServiceTypeSchema();

    @Override
    public String getName() {
        return "service_target.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        serviceTypeSchema.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getName(), this.getContent()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serviceTypeSchema.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new ServiceTargetMarshaller());
    }
}
