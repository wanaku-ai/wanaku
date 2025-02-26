package ai.wanaku.core.services.config;

import java.util.Map;

import ai.wanaku.core.config.WanakuConfig;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "wanaku.service")
public interface WanakuServiceConfig extends WanakuConfig {

    interface Service {
        Map<String, String> defaults();
        Map<String, String> configurations();
    }

    interface Credentials {
        Map<String, String> configurations();
    }
}
