package ai.wanaku.core.util;

public final class StringHelper {

    private StringHelper() {}

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
}
