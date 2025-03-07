package ai.wanaku.api.types;

public record WanakuError(String message) {
    public WanakuError() {
        this(null);
    }
}
