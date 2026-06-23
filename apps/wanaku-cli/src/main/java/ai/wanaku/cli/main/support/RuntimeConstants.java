package ai.wanaku.cli.main.support;

import java.io.File;
import ai.wanaku.core.util.WanakuHome;

public class RuntimeConstants {
    public static final String WANAKU_ROUTER_BACKEND = "wanaku-router-backend";

    private RuntimeConstants() {}

    public static String wanakuHomeDir() {
        return WanakuHome.get();
    }

    public static String wanakuCacheDir() {
        return wanakuHomeDir() + File.separator + "cache";
    }

    public static String wanakuLocalDir() {
        return wanakuHomeDir() + File.separator + "local";
    }
}
