package ai.wanaku.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A utility class that provides a way to retrieve Wanaku current version
 */
public final class VersionHelper {

    /**
     * The current version of this library, loaded from the "version.txt" file in the root directory.
     */
    public static final String VERSION;

    static {
        // Initialize the VERSION field with the contents of the "version.txt" file
        VERSION = initVersion();
    }

    /**
     * Private constructor to prevent instantiation and ensure this class is used as a utility.
     */
    private VersionHelper() {}

    /**
     * Initializes and returns the version string by reading from the "version.txt" file in the root directory.
     *
     * @return The current version of this library.
     */
    private static String initVersion() {
        try (InputStream stream = VersionHelper.class.getResourceAsStream("/version.txt")) {
            if (stream == null) {
                throw new IllegalStateException("version.txt not found on classpath");
            }
            byte[] bytes = stream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
