package ai.wanaku.backend.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ResourceUtils;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.schema.Schema;

public abstract class AbstractWanakuSerializationContextInitializer implements SerializationContextInitializer, Schema {

    @Override
    public abstract String getName();

    @Override
    public String getContent() {
        return ResourceUtils.getResourceAsString(getClass(), "/proto/" + getName());
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getName(), this.getContent()));
    }

    @Override
    public abstract void registerMarshallers(SerializationContext serCtx);
}
