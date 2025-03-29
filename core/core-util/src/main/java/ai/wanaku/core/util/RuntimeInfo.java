package ai.wanaku.core.util;

/**
 * Runtime information utilities
 */
public class RuntimeInfo {
    private static String OS = System.getProperty("os.name").toLowerCase();

    private RuntimeInfo() {    }

    /**
     * Tests if it is running on Windows
     * @return
     */
    public static boolean isWindows() {
        return OS.contains("win");
    }
}
