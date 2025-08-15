package ai.wanaku.cli.main.support;

import java.io.File;

public class RuntimeConstants {
    public static final String WANAKU_HOME_DIR;
    public static final String WANAKU_CACHE_DIR;
    public static final String WANAKU_LOCAL_DIR;
    public static final String WANAKU_ROUTER_WEB = "wanaku-router-web";
    public static final String WANAKU_ROUTER_BACKEND = "wanaku-router-backend";

    static {
        WANAKU_HOME_DIR = System.getProperty("user.home") + File.separator + ".wanaku";
        WANAKU_CACHE_DIR = WANAKU_HOME_DIR + File.separator + "cache";
        WANAKU_LOCAL_DIR = WANAKU_HOME_DIR + File.separator + "local";
    }
}
