package ai.wanaku.core.services.config;

import io.smallrye.config.WithDefault;
import java.util.Map;

import ai.wanaku.core.config.WanakuConfig;

/**
 * Base configuration class for the downstream services
 */
public interface WanakuServiceConfig extends WanakuConfig {

    interface Service {
        Map<String, String> defaults();
        Map<String, String> configurations();
    }

    interface Credentials {
        Map<String, String> configurations();
    }

    interface Registration {
        @WithDefault("10s")
        String interval();

        @WithDefault("3")
        int delaySeconds();

        @WithDefault("3")
        int retries();

        @WithDefault("1")
        int retryWaitSeconds();
    }

}
