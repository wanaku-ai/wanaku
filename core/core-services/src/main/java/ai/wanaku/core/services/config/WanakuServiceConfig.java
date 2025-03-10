package ai.wanaku.core.services.config;

import io.smallrye.config.WithDefault;
import java.util.Map;

import ai.wanaku.core.config.WanakuConfig;
import io.smallrye.config.ConfigMapping;

/**
 * Base configuration class for the downstream services
 */
@ConfigMapping(prefix = "wanaku.service")
public interface WanakuServiceConfig extends WanakuConfig {

    interface Service {
        Map<String, String> defaults();
        Map<String, String> configurations();
    }

    interface Credentials {
        Map<String, String> configurations();
    }

    @WithDefault("3")
    int registerRetries();

    @WithDefault("3")
    int registerDelaySeconds();

    @WithDefault("1")
    int registerRetryWaitSeconds();
}
