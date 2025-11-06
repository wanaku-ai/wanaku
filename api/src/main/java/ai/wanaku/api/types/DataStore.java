package ai.wanaku.api.types;

import java.util.Objects;

/**
 * Entity representing a data store payload for storing arbitrary data.
 */
public class DataStore implements WanakuEntity<String> {
    private String id;
    private String name;
    private String data;

    public DataStore() {}

    public DataStore(String id, String name, String data) {
        this.id = id;
        this.name = name;
        this.data = data;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataStore dataStore = (DataStore) o;
        return Objects.equals(id, dataStore.id)
                && Objects.equals(name, dataStore.name)
                && Objects.equals(data, dataStore.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, data);
    }

    @Override
    public String toString() {
        return "DataStore{" + "id='" + id + '\'' + ", name='" + name + '\'' + ", data='" + data + '\'' + '}';
    }
}
