package ai.wanaku.core.security;

import org.eclipse.microprofile.config.ConfigProvider;

public final class SecurityHelper {

    private SecurityHelper() {}

    public static boolean isAuthEnabled() {
        return Boolean.parseBoolean(ConfigProvider.getConfig()
                .getConfigValue("wanaku.enable.authorization")
                .getValue());
    }
}
