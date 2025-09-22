package ai.wanaku.core.persistence.infinispan.protostream.initializer;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ActivityRecordMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceStateMarshaller;
import java.io.UncheckedIOException;
import java.util.Arrays;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;

public class DiscoveryServiceContextInitializer extends AbstractSerializationContextInitializer
        implements SerializationContextInitializer {

    private static final String PROTO_FILE_NAME = "discovery.proto";

    private static final String PROTO_FILE_PATH = "proto/discovery.proto";

    public DiscoveryServiceContextInitializer() {
        super(
                PROTO_FILE_NAME,
                PROTO_FILE_PATH,
                Arrays.asList(new ActivityRecordMarshaller(), new ServiceStateMarshaller()));
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
