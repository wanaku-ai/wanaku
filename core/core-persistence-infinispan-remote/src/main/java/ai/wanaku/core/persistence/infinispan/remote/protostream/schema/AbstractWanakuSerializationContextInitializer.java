package ai.wanaku.core.persistence.infinispan.remote.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ResourceUtils;
import org.infinispan.protostream.SerializationContext;

public abstract class AbstractWanakuSerializationContextInitializer {

    public abstract String getName();

    public String getContent() {
        return ResourceUtils.getResourceAsString(getClass(), "/proto/" + getName());
    }

    public void registerSchema(SerializationContext serCtx) {
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getName(), this.getContent()));
    }

    public abstract void registerMarshallers(SerializationContext serCtx);
}
