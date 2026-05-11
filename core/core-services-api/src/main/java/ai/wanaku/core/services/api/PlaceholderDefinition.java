package ai.wanaku.core.services.api;

/**
 * Describes a placeholder in deployment instructions that the user must fill in.
 *
 * @param key the placeholder marker used in instruction text (e.g., "registration-url")
 * @param label human-readable label for the input field (e.g., "Registration URL")
 * @param description help text explaining what value to provide
 * @param defaultValue suggested default value, or empty string if none
 * @param type the input type hint: "text" (default) or "url" (requires http/https protocol)
 */
public record PlaceholderDefinition(String key, String label, String description, String defaultValue, String type) {

    public PlaceholderDefinition(String key, String label, String description, String defaultValue) {
        this(key, label, description, defaultValue, "text");
    }
}
