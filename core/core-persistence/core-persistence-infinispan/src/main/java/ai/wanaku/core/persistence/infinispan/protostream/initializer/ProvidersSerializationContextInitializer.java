package ai.wanaku.core.persistence.infinispan.protostream.initializer;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceTargetMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceTypeMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;

import java.io.IOException;
import java.io.UncheckedIOException;

public class ProvidersSerializationContextInitializer implements SerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "providers.proto";
    }

    @Override
    public String getProtoFile() throws UncheckedIOException {
        try {
            return new String(getClass().getClassLoader().getResourceAsStream("proto/providers.proto").readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getProtoFileName(), this.getProtoFile()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new ServiceTargetMarshaller());
        serCtx.registerMarshaller(new ServiceTypeMarshaller());
    }
}
