package ai.wanaku.backend.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.ResourceUtils;
import org.infinispan.protostream.SerializationContext;

public abstract class AbstractWanakuSerializationContextInitializer implements GeneratedSchema {

    @Override
    public abstract String getProtoFileName();

    @Override
    public String getProtoFile() {
        return ResourceUtils.getResourceAsString(getClass(), "/proto/" + getProtoFileName());
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getProtoFileName(), this.getProtoFile()));
    }

    @Override
    public abstract void registerMarshallers(SerializationContext serCtx);
}
