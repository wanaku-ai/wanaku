package ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller;

import java.io.IOException;
import java.util.HashMap;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.capabilities.sdk.api.types.DataStore;

public class DataStoreMarshaller implements MessageMarshaller<DataStore> {

    @Override
    public DataStore readFrom(ProtoStreamReader reader) throws IOException {
        DataStore dataStore = new DataStore();
        dataStore.setId(reader.readString("id"));
        dataStore.setName(reader.readString("name"));
        dataStore.setData(reader.readString("data"));
        dataStore.setLabels(reader.readMap("labels", new HashMap<>(), String.class, String.class));
        return dataStore;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, DataStore dataStore) throws IOException {
        writer.writeString("id", dataStore.getId());
        writer.writeString("name", dataStore.getName());
        writer.writeString("data", dataStore.getData());
        writer.writeMap("labels", dataStore.getLabels(), String.class, String.class);
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
