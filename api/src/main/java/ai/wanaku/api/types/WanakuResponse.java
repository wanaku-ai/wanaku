package ai.wanaku.api.types;

public record WanakuResponse<T>(WanakuError error, T data) {
    public WanakuResponse() {
        this(null, null);
    }

    public WanakuResponse(String error) {
        this(new WanakuError(error), null);
    }

    public WanakuResponse(T data) {
        this(null, data);
    }
}
