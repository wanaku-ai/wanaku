package ai.wanaku.core.persistence.infinispan.protostream.initializer;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceTargetMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ServiceTypeMarshaller;

import java.util.Arrays;

public class ProvidersSerializationContextInitializer extends AbstractSerializationContextInitializer {

    private static final String PROTO_FILE_NAME="providers.proto";

    private static final String PROTO_FILE_PATH="proto/providers.proto";

    public ProvidersSerializationContextInitializer(){
        super(PROTO_FILE_NAME, PROTO_FILE_PATH,
                Arrays.asList(new ServiceTargetMarshaller(),
                              new ServiceTypeMarshaller()) );
    }
}
