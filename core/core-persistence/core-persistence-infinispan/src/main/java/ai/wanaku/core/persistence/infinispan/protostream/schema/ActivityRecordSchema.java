package ai.wanaku.core.persistence.infinispan.protostream.schema;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ActivityRecordMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;

public class ActivityRecordSchema extends AbstractWanakuSerializationContextInitializer {

    private final ServiceStateSchema serviceState = new ServiceStateSchema();

    @Override
    public String getProtoFileName() {
        return "activity_record.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        serviceState.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getProtoFileName(), this.getProtoFile()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serviceState.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new ActivityRecordMarshaller());
    }
}
