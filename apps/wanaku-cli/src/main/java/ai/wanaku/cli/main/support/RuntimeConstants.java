package ai.wanaku.cli.main.support;

import java.io.File;

public class RuntimeConstants {
    public static final String WANAKU_ROUTER_BACKEND = "wanaku-router-backend";

    private RuntimeConstants() {}

    public static String wanakuHomeDir() {
        return System.getProperty("user.home") + File.separator + ".wanaku";
    }

    public static String wanakuCacheDir() {
        return wanakuHomeDir() + File.separator + "cache";
    }

    public static String wanakuLocalDir() {
        return wanakuHomeDir() + File.separator + "local";
    }
}
