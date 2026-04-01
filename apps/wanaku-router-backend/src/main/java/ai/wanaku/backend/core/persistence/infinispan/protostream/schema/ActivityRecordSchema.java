package ai.wanaku.backend.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller.ActivityRecordMarshaller;

public class ActivityRecordSchema extends AbstractWanakuSerializationContextInitializer {

    private final ServiceStateSchema serviceState = new ServiceStateSchema();

    @Override
    public String getName() {
        return "activity_record.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        serviceState.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getName(), this.getContent()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serviceState.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new ActivityRecordMarshaller());
    }
}
