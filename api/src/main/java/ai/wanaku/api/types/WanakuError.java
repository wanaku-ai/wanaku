package ai.wanaku.api.types;

/**
 * Represents an error response from a WANAKU API or service. This class encapsulates an error message.
 */
public record WanakuError(String message) {

    /**
     * Default constructor that initializes a {@link WanakuError} with no message.
     */
    public WanakuError() {
        this(null);
    }
}
