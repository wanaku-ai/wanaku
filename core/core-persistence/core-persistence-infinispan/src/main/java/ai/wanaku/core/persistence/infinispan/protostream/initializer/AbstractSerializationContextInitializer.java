package ai.wanaku.core.persistence.infinispan.protostream.initializer;

import ai.wanaku.api.exceptions.WanakuException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.infinispan.protostream.BaseMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;

public abstract class AbstractSerializationContextInitializer implements SerializationContextInitializer {

    private final String protoFileName;
    private final String protoFileFullPath;
    private final List<BaseMarshaller> marshallers;

    protected AbstractSerializationContextInitializer(
            String protoFileName, String protoFileFullPath, List<BaseMarshaller> marshallers) {
        this.protoFileName = protoFileName;
        this.protoFileFullPath = protoFileFullPath;
        this.marshallers = marshallers;
    }

    @Override
    public String getProtoFileName() {
        return protoFileName;
    }

    @Override
    public String getProtoFile() throws UncheckedIOException {
        ClassLoader classLoader = AbstractSerializationContextInitializer.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(protoFileFullPath)) {
            if (inputStream == null) {
                throw new WanakuException("File not found: " + protoFileFullPath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new WanakuException("Error loading proto file: " + protoFileFullPath);
        }
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getProtoFileName(), this.getProtoFile()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        marshallers.stream().forEach(serCtx::registerMarshaller);
    }
}
