package ai.wanaku.core.util;

/**
 * Runtime information utilities
 */
public class RuntimeInfo {
    private static final String OS = System.getProperty("os.name").toLowerCase();

    private RuntimeInfo() {    }

    /**
     * Tests if it is running on Windows
     * @return true if it is Widows or false otherwise
     */
    public static boolean isWindows() {
        return OS.contains("win");
    }
}
