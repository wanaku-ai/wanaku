package ai.wanaku.server.quarkus.api.v1.models;

public record WanakuError(String message) {
    public WanakuError() {
        this(null);
    }
}
