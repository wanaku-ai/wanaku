package ai.wanaku.api.types;

/**
 * Represents a Wanaku response, which can contain either an error or data.
 *
 * @param <T> The type of the data in the response.
 * @param error An optional error to include in the response
 * @param data The data to include in the response.
 */
public record WanakuResponse<T>(WanakuError error, T data) {

    /**
     * Creates a new Wanaku response with no error and no data.
     */
    public WanakuResponse() {
        this(null, null);
    }

    /**
     * Creates a new Wanaku response with the given error message and no data.
     *
     * @param error The error message to include in the response.
     */
    public WanakuResponse(String error) {
        this(new WanakuError(error), null);
    }

    /**
     * Creates a new Wanaku response with no error and the given data.
     *
     * @param data The data to include in the response.
     */
    public WanakuResponse(T data) {
        this(null, data);
    }
}
