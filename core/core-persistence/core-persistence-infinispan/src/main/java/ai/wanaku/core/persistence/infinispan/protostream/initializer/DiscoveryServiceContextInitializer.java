package ai.wanaku.core.persistence.infinispan.protostream.initializer;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ActivityRecordMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceStateMarshaller;
import java.util.Arrays;

public class DiscoveryServiceContextInitializer extends AbstractSerializationContextInitializer {

    private static final String PROTO_FILE_NAME = "discovery.proto";

    private static final String PROTO_FILE_PATH = "proto/discovery.proto";

    public DiscoveryServiceContextInitializer() {
        super(
                PROTO_FILE_NAME,
                PROTO_FILE_PATH,
                Arrays.asList(new ActivityRecordMarshaller(), new ServiceStateMarshaller()));
    }
}
