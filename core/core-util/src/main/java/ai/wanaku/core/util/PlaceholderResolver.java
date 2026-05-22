package ai.wanaku.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

/**
 * Resolves {@code ${placeholder}} variables in text content using a supplied
 * map of name-to-value bindings.
 * <p>
 * Placeholders use the syntax {@code ${name}} where {@code name} is a
 * dot-separated identifier (e.g. {@code ${camel.version}}). Placeholders that
 * have no matching binding are left unchanged so that hard-coded or
 * not-yet-resolved values continue to work.
 * </p>
 */
public final class PlaceholderResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([\\w.-]+)}");

    private PlaceholderResolver() {}

    /**
     * Resolve placeholders in the given text using the provided bindings.
     *
     * @param text     the text containing potential {@code ${name}} placeholders
     * @param bindings map of placeholder names to replacement values
     * @return the text with resolved placeholders
     */
    public static String resolve(String text, Map<String, String> bindings) {
        if (text == null || bindings == null || bindings.isEmpty()) {
            return text;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = bindings.get(varName);
            if (value != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Load version bindings from a properties-format reader.
     *
     * @param reader the reader containing properties data
     * @return map of placeholder names to values
     * @throws WanakuException if the properties cannot be read
     */
    public static Map<String, String> loadBindings(Reader reader) throws WanakuException {
        Properties props = new Properties();
        try {
            props.load(reader);
        } catch (IOException e) {
            throw new WanakuException("Failed to read version properties: " + e.getMessage());
        }
        return propertiesToMap(props);
    }

    /**
     * Load version bindings from a properties-format input stream.
     *
     * @param inputStream the input stream containing properties data
     * @return map of placeholder names to values
     * @throws WanakuException if the properties cannot be read
     */
    public static Map<String, String> loadBindings(InputStream inputStream) throws WanakuException {
        return loadBindings(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    /**
     * Load version bindings from a properties-format string.
     *
     * @param content the string content of a properties file
     * @return map of placeholder names to values
     * @throws WanakuException if the properties cannot be read
     */
    public static Map<String, String> loadBindings(String content) throws WanakuException {
        return loadBindings(new StringReader(content));
    }

    /**
     * Check whether the given text contains any unresolved placeholders.
     *
     * @param text the text to check
     * @return {@code true} if the text contains at least one {@code ${...}} pattern
     */
    public static boolean hasPlaceholders(String text) {
        if (text == null) {
            return false;
        }
        return PLACEHOLDER_PATTERN.matcher(text).find();
    }

    private static Map<String, String> propertiesToMap(Properties props) {
        return Map.copyOf(props.stringPropertyNames().stream()
                .collect(java.util.stream.Collectors.toMap(name -> name, props::getProperty)));
    }
}
