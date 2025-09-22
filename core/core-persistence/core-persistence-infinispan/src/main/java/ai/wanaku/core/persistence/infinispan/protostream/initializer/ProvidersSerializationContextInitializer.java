package ai.wanaku.core.persistence.infinispan.protostream.initializer;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceTargetMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceTypeMarshaller;
import java.io.UncheckedIOException;
import java.util.Arrays;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;

public class ProvidersSerializationContextInitializer extends AbstractSerializationContextInitializer
        implements SerializationContextInitializer {

    private static final String PROTO_FILE_NAME = "providers.proto";

    private static final String PROTO_FILE_PATH = "proto/providers.proto";

    public ProvidersSerializationContextInitializer() {
        super(
                PROTO_FILE_NAME,
                PROTO_FILE_PATH,
                Arrays.asList(new ServiceTargetMarshaller(), new ServiceTypeMarshaller()));
    }

    @Override
    public String getProtoFileName() {
        return super.getProtoFileName();
    }

    @Override
    public String getProtoFile() throws UncheckedIOException {
        return super.getProtoFile();
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        super.registerSchema(serCtx);
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        super.registerMarshallers(serCtx);
    }
}
