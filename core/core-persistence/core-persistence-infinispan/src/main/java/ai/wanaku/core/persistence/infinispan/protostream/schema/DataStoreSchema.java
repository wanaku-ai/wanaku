package ai.wanaku.core.persistence.infinispan.protostream.schema;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.DataStoreMarshaller;
import org.infinispan.protostream.SerializationContext;

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
