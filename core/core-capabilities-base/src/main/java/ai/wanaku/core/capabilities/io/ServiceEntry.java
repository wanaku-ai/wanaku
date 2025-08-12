package ai.wanaku.core.capabilities.io;

public class ServiceEntry {
    public static final int ID_LENGTH = 36;
    public static final int BYTES = ID_LENGTH + 4;
    private String id;

    ServiceEntry() {}

    ServiceEntry(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
