package ai.wanaku.core.util;

/**
 * Utility class providing helper methods for String operations.
 * <p>
 * This class contains common string validation and manipulation utilities
 * used throughout the Wanaku system. All methods are static and the class
 * cannot be instantiated.
 */
public final class StringHelper {

    /**
     * Private constructor to prevent instantiation.
     */
    private StringHelper() {}

    /**
     * Checks if a string is null or empty.
     * <p>
     * A string is considered empty if it is {@code null} or has zero length
     * after trimming is NOT performed. This means strings containing only
     * whitespace are considered non-empty.
     *
     * @param str the string to check
     * @return {@code true} if the string is null or empty, {@code false} otherwise
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Checks if a string is not null and not empty.
     * <p>
     * This is the logical inverse of {@link #isEmpty(String)}.
     *
     * @param str the string to check
     * @return {@code true} if the string is not null and not empty, {@code false} otherwise
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
}
