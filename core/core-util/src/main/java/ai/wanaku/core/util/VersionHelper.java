package ai.wanaku.core.util;

import java.io.IOException;

public final class VersionHelper {
    public static final String VERSION;

    static {
        VERSION = initVersion();
    }


    private VersionHelper() {}

    private static String initVersion() {
        try {
            byte[] bytes = VersionHelper.class.getResourceAsStream("/version.txt").readAllBytes();
            return new String(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
