package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.api.types.DataStore;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

/**
 * Protostream marshaller for DataStore entity serialization.
 */
public class DataStoreMarshaller implements MessageMarshaller<DataStore> {

    @Override
    public DataStore readFrom(ProtoStreamReader reader) throws IOException {
        DataStore dataStore = new DataStore();
        dataStore.setId(reader.readString("id"));
        dataStore.setName(reader.readString("name"));
        dataStore.setData(reader.readString("data"));
        return dataStore;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, DataStore dataStore) throws IOException {
        writer.writeString("id", dataStore.getId());
        writer.writeString("name", dataStore.getName());
        writer.writeString("data", dataStore.getData());
    }

    @Override
    public Class<? extends DataStore> getJavaClass() {
        return DataStore.class;
    }

    @Override
    public String getTypeName() {
        return DataStore.class.getCanonicalName();
    }
}
