package ai.wanaku.core.runtime.camel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for handling Camel query-related operations, particularly for replacing and sanitizing values.
 */
public final class CamelQueryHelper {

    /**
     * Replaces the content within 'RAW()' in a given string with a new value.
     * This method finds all occurrences of the pattern 'RAW(anything_here)' and replaces
     * 'anything_here' with the provided {@code newValue}.
     *
     * @param originalString The string in which to perform the replacement.
     * @param newValue The string to replace the content inside 'RAW()' with.
     * @return A new string with the 'RAW()' contents replaced.
     */
    public static String replaceRawValue(String originalString, String newValue) {
        // The regular expression to find 'RAW(anything_here)'
        // RAW\\(    -> Matches "RAW(" literally
        // (.*?)     -> Matches any character (.), zero or more times (*), non-greedily (?)
        // \\)       -> Matches ")" literally
        String regex = "RAW\\(.*?\\)";

        Pattern pattern = Pattern.compile(regex);

        Matcher matcher = pattern.matcher(originalString);

        return matcher.replaceAll("RAW(" + newValue + ")");
    }

    /**
     * Replaces potentially unsafe values within a string before logging it.
     * Specifically, if the string contains "RAW(", it calls {@link #replaceRawValue(String, String)}
     * to replace the content inside 'RAW()' with "xxxx" for security or privacy reasons.
     * If "RAW(" is not found, the original string is returned unchanged.
     *
     * @param value The string to be sanitized for logging.
     * @return A sanitized string suitable for logging, or the original string if no 'RAW()' values were found.
     */
    public static String safeLog(String value) {
        if (value.contains("RAW(")) {
            return CamelQueryHelper.replaceRawValue(value, "xxxx");
        } else {
            return value;
        }
    }
}
