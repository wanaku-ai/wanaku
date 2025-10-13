package ai.wanaku.api.types;

/**
 * Represents an error response from the Wanaku API or services.
 * <p>
 * This immutable record encapsulates error information that is returned to clients
 * when API operations fail or encounter errors. It provides a simple, standardized
 * way to communicate error details across the Wanaku system.
 * </p>
 * <p>
 * The error message should be descriptive and provide enough context for clients
 * to understand what went wrong and potentially take corrective action.
 * </p>
 *
 * @param message the error message describing what went wrong, or {@code null} if no message is available
 */
public record WanakuError(String message) {

    /**
     * Default constructor that creates a {@link WanakuError} with no message.
     * <p>
     * This constructor is provided for cases where an error indicator is needed
     * but no specific message is available at the time of creation.
     * </p>
     */
    public WanakuError() {
        this(null);
    }
}
