package ai.wanaku.api.types.management;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a configuration for the downstream service.
 *
 * This class encapsulates key-value pairs of configuration settings, along with their corresponding descriptions.
 */
public class Configuration {

    /**
     * The value associated with this configuration setting.
     */
    private String value;

    /**
     * A human-readable description of the configuration setting and its purpose.
     */
    private String description;

    /**
     * Returns the value associated with this configuration setting.
     *
     * @return The configuration value.
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value associated with this configuration setting.
     *
     * @param value The new configuration value.
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Returns a human-readable description of the configuration setting and its purpose.
     *
     * @return The configuration description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets a human-readable description of the configuration setting and its purpose.
     *
     * @param description The new configuration description.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    public String toJson() {
        return String.format("""
            {"value":%s,"description":%s}
            """, getValue() == null ? "null" : "\"" + getValue() + "\"",
                getDescription() == null ? "null" : "\"" + getDescription() + "\"");
    }

    private static Pattern valuePattern = Pattern.compile("\"value\":\"(.*)\",");
    private static Pattern descriptionPattern = Pattern.compile("\"description\":\"(.*)\"");

    public static Configuration fromJson(String json) {
        Configuration configuration = new Configuration();

        Matcher matcher = valuePattern.matcher(json);
        if (matcher.find()) {
            configuration.setValue(matcher.group(1));
        }

        Matcher descriptionMatcher = descriptionPattern.matcher(json);
        if (descriptionMatcher.find()) {
            configuration.setDescription(descriptionMatcher.group(1));
        }

        return configuration;
    }
}
