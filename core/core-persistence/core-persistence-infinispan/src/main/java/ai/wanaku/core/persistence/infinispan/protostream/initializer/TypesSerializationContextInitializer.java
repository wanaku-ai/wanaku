package ai.wanaku.core.persistence.infinispan.protostream.initializer;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ForwardReferenceMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.InputSchemaMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ParamMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.PropertyMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.RemoteToolReferenceMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ResourceReferenceMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ToolReferenceMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.WanakuErrorMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TypesSerializationContextInitializer implements SerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "types.proto";
    }

    @Override
    public String getProtoFile() throws UncheckedIOException {
        try {
            Path path = Paths.get(getClass().getClassLoader().getResource("proto/types.proto").toURI());
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
        serCtx.registerMarshaller(new ForwardReferenceMarshaller());
        serCtx.registerMarshaller(new InputSchemaMarshaller());
        serCtx.registerMarshaller(new PropertyMarshaller());
        serCtx.registerMarshaller(new RemoteToolReferenceMarshaller());
        serCtx.registerMarshaller(new ParamMarshaller());
        serCtx.registerMarshaller(new ResourceReferenceMarshaller());
        serCtx.registerMarshaller(new WanakuErrorMarshaller());
        serCtx.registerMarshaller(new ToolReferenceMarshaller());
    }
}
