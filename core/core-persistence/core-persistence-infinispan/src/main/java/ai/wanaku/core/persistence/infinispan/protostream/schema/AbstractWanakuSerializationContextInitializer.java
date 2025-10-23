package ai.wanaku.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.impl.ResourceUtils;

public abstract class AbstractWanakuSerializationContextInitializer implements SerializationContextInitializer {

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
