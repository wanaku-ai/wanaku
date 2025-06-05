package ai.wanaku.core.persistence.infinispan.protostream.initializer;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ActivityRecordMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceStateMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DiscoveryServiceContextInitializer implements SerializationContextInitializer {
    @Override
    public String getProtoFileName() {
        return "discovery.proto";
    }

    @Override
    public String getProtoFile() throws UncheckedIOException {
        try {
            Path path = Paths.get(getClass().getClassLoader().getResource("proto/discovery.proto").toURI());
            return Files.readString(path);
        } catch (IOException | URISyntaxException e) {
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

