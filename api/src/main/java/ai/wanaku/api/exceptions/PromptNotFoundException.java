package ai.wanaku.api.exceptions;

/**
 * Exception thrown when a prompt cannot be found.
 */
public class PromptNotFoundException extends WanakuException {
    /**
     * Constructs a new PromptNotFoundException with the specified prompt name.
     *
     * @param promptName the name of the prompt that was not found
     */
    public PromptNotFoundException(String promptName) {
        super(String.format("Prompt '%s' not found", promptName));
    }
}
