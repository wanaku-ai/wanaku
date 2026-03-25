package ai.wanaku.backend.common;

public enum EventType {
    REGISTER("register"),
    DEREGISTER("deregister"),
    UPDATE("update"),
    PING("ping"),
    HEALTH_CHECK("health-check");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String asValue() {
        return value;
    }
}
