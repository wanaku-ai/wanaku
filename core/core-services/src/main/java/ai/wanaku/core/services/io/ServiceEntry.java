package ai.wanaku.core.services.io;

public class ServiceEntry {
    public static int ID_LENGTH = 36;
    public static int BYTES = ID_LENGTH + 4;
    private String id;

    ServiceEntry() {
    }

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
