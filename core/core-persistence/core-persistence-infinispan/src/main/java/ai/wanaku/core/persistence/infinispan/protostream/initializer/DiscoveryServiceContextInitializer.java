package ai.wanaku.core.persistence.infinispan.protostream.initializer;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ActivityRecordMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceStateMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;

import java.io.IOException;
import java.io.UncheckedIOException;

public class DiscoveryServiceContextInitializer implements SerializationContextInitializer {
    @Override
    public String getProtoFileName() {
        return "discovery.proto";
    }

    @Override
    public String getProtoFile() throws UncheckedIOException {
        try {
            return new String(getClass().getClassLoader().getResourceAsStream("proto/discovery.proto").readAllBytes());
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
        serCtx.registerMarshaller(new ActivityRecordMarshaller());
        serCtx.registerMarshaller(new ServiceStateMarshaller());
    }
}

