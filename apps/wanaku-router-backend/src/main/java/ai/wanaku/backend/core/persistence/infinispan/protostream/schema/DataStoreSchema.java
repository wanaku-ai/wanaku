package ai.wanaku.backend.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.SerializationContext;
import ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller.DataStoreMarshaller;

/**
 * Schema initializer for DataStore entity.
 */
public class DataStoreSchema extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "data_store.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new DataStoreMarshaller());
    }
}
