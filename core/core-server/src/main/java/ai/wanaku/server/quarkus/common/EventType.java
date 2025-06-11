package ai.wanaku.server.quarkus.common;

public enum EventType {

    REGISTER("register"),
    DEREGISTER("deregister"),
    UPDATE("update"),
    PING("ping");

    private String value;

    EventType(String value) {
        this.value = value;
    }

    public String asValue() {
        return value;
    }
}