package ai.wanaku.cli.main.support;

/**
 * Helper methods for working with Strings.
 */
public class StringHelper {

    /**
     * Constructor of utility class should be private.
     */
    private StringHelper() {
    }

    /**
     * Asserts whether the string is <b>not</b> empty.
     *
     * @param  value                    the string to test
     * @return                          {@code true} if {@code value} is not null and not blank, {@code false} otherwise.
     */
    public static boolean isNotEmpty(String value) {
        return !isEmpty(value);
    }

    /**
     * Asserts whether the string is empty.
     *
     * @param  value                    the string to test
     * @return                          {@code true} if {@code value} is null and blank, {@code false} otherwise.
     */
    public static boolean isEmpty(String value) {
        return value == null || value.isBlank();

    }
}
